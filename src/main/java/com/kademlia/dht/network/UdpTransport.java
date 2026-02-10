package com.kademlia.dht.network;

import com.kademlia.dht.protocol.MessageCodec;
import com.kademlia.dht.protocol.RpcMessage;
import com.kademlia.dht.protocol.RpcRequest;
import com.kademlia.dht.protocol.RpcResponse;
import com.kademlia.dht.storage.ByteArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/**
 * UDP transport: send RPC requests, receive responses; dispatch incoming requests to handler.
 */
public class UdpTransport implements Transport {
    private static final Logger log = LoggerFactory.getLogger(UdpTransport.class);
    private static final int MAX_PACKET_SIZE = 65507;

    private final DatagramSocket socket;
    private final MessageCodec codec = new MessageCodec();
    private final Map<ByteArray, CompletableFuture<RpcResponse>> pending = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean running = true;

    private volatile RequestHandler requestHandler;

    @Override
    public void setRequestHandler(RequestHandler handler) {
        this.requestHandler = handler;
    }

    public UdpTransport(int port) throws SocketException {
        this.socket = new DatagramSocket(port);
        startReceiver();
    }

    private void startReceiver() {
        Thread.ofVirtual().start(() -> {
            byte[] buffer = new byte[MAX_PACKET_SIZE];
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
                    handleIncoming(data, packet.getAddress(), packet.getPort());
                } catch (IOException e) {
                    if (running) {
                        log.warn("Error receiving packet", e);
                    }
                }
            }
        });
    }

    private void handleIncoming(byte[] data, InetAddress fromIp, int fromPort) {
        try {
            RpcMessage msg = codec.decode(data);
            if (msg instanceof RpcResponse resp) {
                CompletableFuture<RpcResponse> future = pending.remove(new ByteArray(resp.messageId()));
                if (future != null) {
                    future.complete(resp);
                }
            } else if (msg instanceof RpcRequest req) {
                RequestHandler handler = requestHandler;
                if (handler != null) {
                    executor.submit(() -> handler.handleRequest(req, fromIp, fromPort));
                }
            }
        } catch (IOException e) {
            log.warn("Failed to decode message", e);
        }
    }

    @Override
    public CompletableFuture<RpcResponse> send(RpcRequest request, InetAddress ip, int port, Duration timeout) {
        CompletableFuture<RpcResponse> future = new CompletableFuture<>();
        ByteArray key = new ByteArray(request.messageId());
        pending.put(key, future);
        try {
            byte[] encoded = codec.encode(request);
            DatagramPacket packet = new DatagramPacket(encoded, encoded.length, ip, port);
            socket.send(packet);
            executor.submit(() -> {
                try {
                    Thread.sleep(timeout.toMillis());
                    CompletableFuture<RpcResponse> removed = pending.remove(key);
                    if (removed != null) {
                        removed.completeExceptionally(new TimeoutException("RPC timeout"));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        } catch (IOException e) {
            future.completeExceptionally(e);
            pending.remove(key);
        }
        return future;
    }

    @Override
    public void sendResponse(RpcResponse response, InetAddress ip, int port) {
        try {
            byte[] encoded = codec.encode(response);
            DatagramPacket packet = new DatagramPacket(encoded, encoded.length, ip, port);
            socket.send(packet);
        } catch (IOException e) {
            log.warn("Failed to send response", e);
        }
    }

    @Override
    public void close() {
        running = false;
        socket.close();
        executor.shutdown();
    }
}
