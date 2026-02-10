package com.kademlia.dht;

import com.kademlia.dht.network.Transport;
import com.kademlia.dht.protocol.RpcRequest;
import com.kademlia.dht.protocol.RpcResponse;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.time.Duration;

/**
 * In-process "network" that routes RPCs between SimulatedTransport instances by port.
 * Used so multi-node experiments run without real UDP and always produce output.
 */
public final class SimulatedNetwork {

    private final Map<Integer, SimulatedTransport> byPort = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public void register(int port, SimulatedTransport transport) {
        byPort.put(port, transport);
    }

    public void unregister(int port) {
        byPort.remove(port);
    }

    void deliverRequest(int targetPort, RpcRequest request, InetAddress fromIp, int fromPort) {
        SimulatedTransport target = byPort.get(targetPort);
        if (target == null) {
            return;
        }
        executor.submit(() -> target.handleIncomingRequest(request, fromIp, fromPort));
    }

    void completePending(int senderPort, RpcResponse response) {
        SimulatedTransport sender = byPort.get(senderPort);
        if (sender != null) {
            sender.completePending(response);
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
