package com.kademlia.dht.network;

import com.kademlia.dht.protocol.RpcRequest;
import com.kademlia.dht.protocol.RpcResponse;

import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction for sending and receiving RPCs. Allows swapping UDP for an
 * in-process implementation in tests so experiments can run without real network.
 */
public interface Transport extends AutoCloseable {

    void setRequestHandler(RequestHandler handler);

    CompletableFuture<RpcResponse> send(RpcRequest request, InetAddress ip, int port, Duration timeout);

    void sendResponse(RpcResponse response, InetAddress ip, int port);

    @Override
    void close();

    @FunctionalInterface
    interface RequestHandler {
        void handleRequest(RpcRequest request, InetAddress fromIp, int fromPort);
    }
}
