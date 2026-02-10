package com.kademlia.dht;

import com.kademlia.dht.network.Transport;
import com.kademlia.dht.protocol.RpcRequest;
import com.kademlia.dht.protocol.RpcResponse;
import com.kademlia.dht.storage.ByteArray;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;

/**
 * In-process transport that delivers RPCs via SimulatedNetwork so tests
 * do not depend on real UDP. Implements Transport so Server and KademliaProtocol
 * work unchanged.
 */
public final class SimulatedTransport implements Transport {

    private final SimulatedNetwork network;
    private final int myPort;
    private final Map<ByteArray, CompletableFuture<RpcResponse>> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
    private volatile RequestHandler requestHandler;

    public SimulatedTransport(SimulatedNetwork network, int myPort) {
        this.network = network;
        this.myPort = myPort;
    }

    @Override
    public void setRequestHandler(RequestHandler handler) {
        this.requestHandler = handler;
    }

    @Override
    public CompletableFuture<RpcResponse> send(RpcRequest request, InetAddress ip, int port, Duration timeout) {
        CompletableFuture<RpcResponse> future = new CompletableFuture<>();
        ByteArray key = new ByteArray(request.messageId());
        pending.put(key, future);
        timeoutExecutor.schedule(() -> {
            CompletableFuture<RpcResponse> removed = pending.remove(key);
            if (removed != null && !removed.isDone()) {
                removed.completeExceptionally(new TimeoutException("RPC timeout"));
            }
        }, timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        network.deliverRequest(port, request, ip, myPort);
        return future;
    }

    @Override
    public void sendResponse(RpcResponse response, InetAddress ip, int port) {
        network.completePending(port, response);
    }

    void handleIncomingRequest(RpcRequest request, InetAddress fromIp, int fromPort) {
        RequestHandler handler = requestHandler;
        if (handler != null) {
            handler.handleRequest(request, fromIp, fromPort);
        }
    }

    void completePending(RpcResponse response) {
        CompletableFuture<RpcResponse> future = pending.remove(new ByteArray(response.messageId()));
        if (future != null) {
            future.complete(response);
        }
    }

    @Override
    public void close() {
        timeoutExecutor.shutdown();
    }
}
