package com.kademlia.dht.protocol;

import com.kademlia.dht.node.Node;
import com.kademlia.dht.node.NodeId;
import com.kademlia.dht.routing.RoutingTable;
import com.kademlia.dht.storage.IStorage;
import com.kademlia.dht.network.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kademlia RPC handler: ping, store, find_node, find_value; async call methods.
 */
public class KademliaProtocol {
    private static final Logger log = LoggerFactory.getLogger(KademliaProtocol.class);
    private static final AtomicInteger messageIdCounter = new AtomicInteger(ThreadLocalRandom.current().nextInt());

    private final Node selfNode;
    private final RoutingTable routingTable;
    private final IStorage storage;
    private final Transport transport;
    private final int ksize;

    public KademliaProtocol(Node selfNode, IStorage storage, int ksize, Transport transport) {
        this.selfNode = selfNode;
        this.routingTable = new RoutingTable(selfNode, ksize);
        this.storage = storage;
        this.transport = transport;
        this.ksize = ksize;
    }

    public RoutingTable getRoutingTable() {
        return routingTable;
    }

    public Node getSelfNode() {
        return selfNode;
    }

    public void handleRequest(RpcRequest request, InetAddress fromIp, int fromPort) {
        Node sender = new Node(request.senderId(), fromIp, fromPort);
        RpcResponse response = switch (request) {
            case PingRequest req -> handlePing(sender, req.messageId());
            case StoreRequest req -> handleStore(sender, req.messageId(), req.key(), req.value());
            case FindNodeRequest req -> handleFindNode(sender, req.messageId(), req.targetId());
            case FindValueRequest req -> handleFindValue(sender, req.messageId(), req.key());
        };
        transport.sendResponse(response, fromIp, fromPort);
    }

    private PingResponse handlePing(Node sender, byte[] messageId) {
        welcomeIfNew(sender);
        return new PingResponse(messageId, selfNode.id());
    }

    private StoreResponse handleStore(Node sender, byte[] messageId, byte[] key, byte[] value) {
        welcomeIfNew(sender);
        storage.put(key, value);
        log.debug("Stored key from {}", sender);
        return new StoreResponse(messageId, true);
    }

    private FindNodeResponse handleFindNode(Node sender, byte[] messageId, NodeId targetId) {
        welcomeIfNew(sender);
        Node target = new Node(targetId, null, 0);
        List<Node> neighbors = routingTable.findNeighbors(target, ksize);
        return new FindNodeResponse(messageId, neighbors);
    }

    private FindValueResponse handleFindValue(Node sender, byte[] messageId, byte[] key) {
        welcomeIfNew(sender);
        Optional<byte[]> value = storage.get(key);
        if (value.isPresent()) {
            return new FindValueResponse(messageId, value, List.of());
        }
        Node target = new Node(new NodeId(key), null, 0);
        List<Node> neighbors = routingTable.findNeighbors(target, ksize);
        return new FindValueResponse(messageId, Optional.empty(), neighbors);
    }

    private void welcomeIfNew(Node node) {
        if (routingTable.isNewNode(node)) {
            log.info("New node discovered: {}", node);
            for (var it = storage.iterator(); it.hasNext(); ) {
                var entry = it.next();
                Node keyNode = new Node(new NodeId(entry.getKey()), null, 0);
                List<Node> neighbors = routingTable.findNeighbors(keyNode, ksize);
                if (!neighbors.isEmpty()) {
                    int lastDist = neighbors.get(neighbors.size() - 1).distanceTo(keyNode);
                    int newNodeDist = node.distanceTo(keyNode);
                    int thisDist = selfNode.distanceTo(keyNode);
                    if (newNodeDist < lastDist && thisDist < neighbors.get(0).distanceTo(keyNode)) {
                        callStore(node, entry.getKey(), entry.getValue());
                    }
                }
            }
            routingTable.addContact(node);
        }
    }

    public byte[] generateMessageId() {
        int id = messageIdCounter.incrementAndGet();
        return new byte[]{
                (byte) (id >> 24),
                (byte) (id >> 16),
                (byte) (id >> 8),
                (byte) id
        };
    }

    public CompletableFuture<PingResponse> callPing(Node node) {
        byte[] msgId = generateMessageId();
        PingRequest req = new PingRequest(msgId, selfNode.id(), selfNode.ip(), selfNode.port());
        return transport.send(req, node.ip(), node.port(), Duration.ofSeconds(5))
                .thenApply(resp -> (PingResponse) resp)
                .handle((resp, ex) -> handleCallResponse(resp, ex, node));
    }

    public CompletableFuture<StoreResponse> callStore(Node node, byte[] key, byte[] value) {
        byte[] msgId = generateMessageId();
        StoreRequest req = new StoreRequest(msgId, selfNode.id(), selfNode.ip(), selfNode.port(), key, value);
        return transport.send(req, node.ip(), node.port(), Duration.ofSeconds(5))
                .thenApply(resp -> (StoreResponse) resp)
                .handle((resp, ex) -> handleCallResponse(resp, ex, node));
    }

    public CompletableFuture<FindNodeResponse> callFindNode(Node node, NodeId targetId) {
        byte[] msgId = generateMessageId();
        FindNodeRequest req = new FindNodeRequest(msgId, selfNode.id(), selfNode.ip(), selfNode.port(), targetId);
        return transport.send(req, node.ip(), node.port(), Duration.ofSeconds(5))
                .thenApply(resp -> (FindNodeResponse) resp)
                .handle((resp, ex) -> handleCallResponse(resp, ex, node));
    }

    public CompletableFuture<FindValueResponse> callFindValue(Node node, byte[] key) {
        byte[] msgId = generateMessageId();
        FindValueRequest req = new FindValueRequest(msgId, selfNode.id(), selfNode.ip(), selfNode.port(), key);
        return transport.send(req, node.ip(), node.port(), Duration.ofSeconds(5))
                .thenApply(resp -> (FindValueResponse) resp)
                .handle((resp, ex) -> handleCallResponse(resp, ex, node));
    }

    private <T> T handleCallResponse(T response, Throwable ex, Node node) {
        if (ex != null) {
            log.warn("No response from {}, removing from router", node);
            routingTable.removeContact(node);
            return null;
        }
        if (response != null) {
            welcomeIfNew(node);
        }
        return response;
    }

    /**
     * Returns node IDs for buckets that need refresh (e.g. not updated recently).
     */
    public List<NodeId> getRefreshIds() {
        List<NodeId> ids = new ArrayList<>();
        Random rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 5; i++) {
            byte[] id = new byte[20];
            rnd.nextBytes(id);
            ids.add(new NodeId(id));
        }
        return ids;
    }
}
