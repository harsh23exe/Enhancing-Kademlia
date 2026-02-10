package com.kademlia.dht.network;

import com.kademlia.dht.crawling.NodeSpiderCrawl;
import com.kademlia.dht.crawling.ValueSpiderCrawl;
import com.kademlia.dht.node.Node;
import com.kademlia.dht.node.NodeId;
import com.kademlia.dht.protocol.KademliaProtocol;
import com.kademlia.dht.protocol.StoreResponse;
import com.kademlia.dht.storage.ForgetfulStorage;
import com.kademlia.dht.storage.IStorage;
import com.kademlia.dht.util.Digest;
import com.kademlia.dht.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * High-level Kademlia server: bootstrap, get, set, routing table refresh.
 */
public class Server implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(Server.class);
    private static final long STORAGE_TTL = 604800;
    private static final int DEFAULT_KSIZE = 20;
    private static final int DEFAULT_ALPHA = 3;

    private final int ksize;
    private final int alpha;
    private Node selfNode;
    private IStorage storage;
    private DynamicQuorum quorum;
    private Transport transport;
    private KademliaProtocol protocol;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public Server(int ksize, int alpha, NodeId nodeId, IStorage storage) {
        this.ksize = ksize > 0 ? ksize : DEFAULT_KSIZE;
        this.alpha = alpha > 0 ? alpha : DEFAULT_ALPHA;
        NodeId id = nodeId != null ? nodeId : new NodeId(Digest.hash(String.valueOf(System.nanoTime())));
        this.selfNode = new Node(id, null, 0);
        this.storage = storage != null ? storage : new ForgetfulStorage(STORAGE_TTL);
        this.quorum = new DynamicQuorum(1, 1, 3);
    }

    public void listen(int port, String iface) throws IOException {
        listenWithTransport(new UdpTransport(port), port, iface);
    }

    /**
     * Attach an existing transport (e.g. in-process for tests). Does not start a UDP socket.
     */
    public void listenWithTransport(Transport transport, int port, String iface) throws IOException {
        InetAddress bindAddr = InetAddress.getByName(iface);
        this.selfNode = new Node(selfNode.id(), bindAddr, port);
        this.transport = transport;
        this.protocol = new KademliaProtocol(selfNode, storage, ksize, transport);
        transport.setRequestHandler(protocol::handleRequest);
        scheduler.scheduleAtFixedRate(this::refreshTable, 3600, 3600, TimeUnit.SECONDS);
        log.info("Node {} listening on {}:{}", selfNode.id(), iface, port);
    }

    public Node getSelfNode() {
        return selfNode;
    }

    public KademliaProtocol getProtocol() {
        return protocol;
    }

    public CompletableFuture<List<Node>> bootstrap(List<Pair<String, Integer>> addresses) {
        log.debug("Bootstrapping with {} addresses", addresses.size());
        List<CompletableFuture<Node>> futures = addresses.stream()
                .map(this::bootstrapNode)
                .toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenCompose(v -> {
                    List<Node> nodes = futures.stream()
                            .map(CompletableFuture::join)
                            .filter(Objects::nonNull)
                            .toList();
                    if (nodes.isEmpty()) {
                        return CompletableFuture.completedFuture(List.of());
                    }
                    NodeSpiderCrawl spider = new NodeSpiderCrawl(
                            protocol, selfNode, nodes, ksize, alpha);
                    return spider.find();
                });
    }

    private CompletableFuture<Node> bootstrapNode(Pair<String, Integer> addr) {
        try {
            InetAddress ip = InetAddress.getByName(addr.left());
            byte[] zeroId = new byte[20];
            Node tempNode = new Node(new NodeId(zeroId), ip, addr.right());
            return protocol.callPing(tempNode)
                    .thenApply(resp -> resp != null
                            ? new Node(resp.nodeId(), ip, addr.right())
                            : null);
        } catch (UnknownHostException e) {
            return CompletableFuture.completedFuture(null);
        }
    }

    public CompletableFuture<Optional<byte[]>> get(String key) {
        log.info("Looking up key {}", key);
        byte[] dkey = Digest.digest(key);
        long startTime = System.nanoTime();
        Optional<byte[]> cached = storage.get(dkey);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(emptyAsAbsent(cached));
        }
        Node target = new Node(new NodeId(dkey), null, 0);
        List<Node> nearest = protocol.getRoutingTable().findNeighbors(target, ksize);
        if (nearest.isEmpty()) {
            log.warn("No known neighbors to get key {}", key);
            return CompletableFuture.completedFuture(Optional.empty());
        }
        ValueSpiderCrawl spider = new ValueSpiderCrawl(
                protocol, target, nearest, ksize, alpha);
        return spider.find()
                .thenApply(result -> {
                    long latency = System.nanoTime() - startTime;
                    quorum.adjustQuorum(latency, result.isPresent() && result.get().length > 0);
                    return emptyAsAbsent(result);
                });
    }

    private static Optional<byte[]> emptyAsAbsent(Optional<byte[]> value) {
        if (value.isEmpty() || value.get().length == 0) {
            return Optional.empty();
        }
        return value;
    }

    /**
     * Delete a key by storing a tombstone (empty value). Get will return empty for deleted keys.
     */
    public CompletableFuture<Boolean> delete(String key) {
        return set(key, new byte[0]);
    }

    public CompletableFuture<Boolean> set(String key, byte[] value) {
        log.info("Setting '{}' on network", key);
        byte[] dkey = Digest.digest(key);
        long startTime = System.nanoTime();
        return setDigest(dkey, value)
                .thenApply(success -> {
                    long latency = System.nanoTime() - startTime;
                    quorum.adjustQuorum(latency, success);
                    return success;
                });
    }

    private CompletableFuture<Boolean> setDigest(byte[] dkey, byte[] value) {
        Node target = new Node(new NodeId(dkey), null, 0);
        List<Node> nearest = protocol.getRoutingTable().findNeighbors(target, ksize);
        if (nearest.isEmpty()) {
            log.warn("No known neighbors to set key");
            return CompletableFuture.completedFuture(false);
        }
        NodeSpiderCrawl spider = new NodeSpiderCrawl(protocol, target, nearest, ksize, alpha);
        return spider.find()
                .thenCompose(nodes -> {
                    log.info("Setting on {} nodes", nodes.size());
                    int maxDist = nodes.stream()
                            .mapToInt(n -> n.distanceTo(target))
                            .max()
                            .orElse(Integer.MAX_VALUE);
                    if (selfNode.distanceTo(target) <= maxDist) {
                        storage.put(dkey, value);
                    }
                    List<CompletableFuture<StoreResponse>> storeFutures = nodes.stream()
                            .map(n -> protocol.callStore(n, dkey, value))
                            .toList();
                    return CompletableFuture.allOf(storeFutures.toArray(CompletableFuture[]::new))
                            .thenApply(v -> storeFutures.stream()
                                    .map(CompletableFuture::join)
                                    .anyMatch(r -> r != null && r.success()));
                });
    }

    private void refreshTable() {
        log.debug("Refreshing routing table");
        try {
            List<NodeId> refreshIds = protocol.getRefreshIds();
            List<CompletableFuture<?>> futures = refreshIds.stream()
                    .map(id -> {
                        Node target = new Node(id, null, 0);
                        List<Node> nearest = protocol.getRoutingTable().findNeighbors(target, alpha);
                        NodeSpiderCrawl spider = new NodeSpiderCrawl(protocol, target, nearest, ksize, alpha);
                        return spider.find();
                    })
                    .collect(Collectors.toList());
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            for (var it = storage.iterator(); it.hasNext(); ) {
                var entry = it.next();
                setDigest(entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            log.warn("Refresh failed", e);
        }
    }

    @Override
    public void close() {
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (transport != null) {
            transport.close();
        }
    }
}
