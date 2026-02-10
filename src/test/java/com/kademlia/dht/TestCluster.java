package com.kademlia.dht;

import com.kademlia.dht.network.Server;
import com.kademlia.dht.util.Pair;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Test helper that spins up multiple Kademlia {@link Server} instances.
 *
 * Use {@link #createSimulated(int, int, int, int)} for in-process clusters that do not
 * use real UDP; experiments run reliably and produce CSV output. Use the constructor
 * for real UDP (can time out on some environments).
 */
public final class TestCluster implements Closeable {

    private final List<Server> servers = new ArrayList<>();
    private final List<Integer> ports = new ArrayList<>();
    private SimulatedNetwork simulatedNetwork;

    /**
     * Creates and bootstraps a cluster using real UDP sockets (may time out on some systems).
     */
    public TestCluster(int size, int basePort, int ksize, int alpha, boolean bootstrap) throws Exception {
        if (size < 1) {
            throw new IllegalArgumentException("Cluster size must be >= 1");
        }
        for (int i = 0; i < size; i++) {
            int port = basePort + i;
            Server server = new Server(ksize, alpha, null, null);
            server.listen(port, "127.0.0.1");
            servers.add(server);
            ports.add(port);
        }
        if (bootstrap && size > 1) {
            int seedPort = ports.get(0);
            List<Pair<String, Integer>> seedAddr = List.of(Pair.of("127.0.0.1", seedPort));
            for (int i = 1; i < servers.size(); i++) {
                servers.get(i).bootstrap(seedAddr).get(5, TimeUnit.SECONDS);
            }
        }
    }

    /**
     * Creates an in-process cluster using SimulatedTransport. No UDP; use this for
     * experiments so they always complete and write output (e.g. build/experiments/*.csv).
     */
    public static TestCluster createSimulated(int size, int basePort, int ksize, int alpha) throws Exception {
        if (size < 1) {
            throw new IllegalArgumentException("Cluster size must be >= 1");
        }
        SimulatedNetwork network = new SimulatedNetwork();
        TestCluster cluster = new TestCluster();
        cluster.simulatedNetwork = network;
        for (int i = 0; i < size; i++) {
            int port = basePort + i;
            Server server = new Server(ksize, alpha, null, null);
            SimulatedTransport transport = new SimulatedTransport(network, port);
            network.register(port, transport);
            server.listenWithTransport(transport, port, "127.0.0.1");
            cluster.servers.add(server);
            cluster.ports.add(port);
        }
        if (size > 1) {
            List<Pair<String, Integer>> seedAddr = List.of(Pair.of("127.0.0.1", basePort));
            for (int i = 1; i < cluster.servers.size(); i++) {
                cluster.servers.get(i).bootstrap(seedAddr).get(10, TimeUnit.SECONDS);
            }
        }
        return cluster;
    }

    private TestCluster() {}

    public List<Server> getServers() {
        return Collections.unmodifiableList(servers);
    }

    public Server getSeed() {
        return servers.get(0);
    }

    public List<Integer> getPorts() {
        return Collections.unmodifiableList(ports);
    }

    @Override
    public void close() throws IOException {
        for (Server server : servers) {
            try {
                server.close();
            } catch (Exception ignored) {
            }
        }
        servers.clear();
        ports.clear();
        if (simulatedNetwork != null) {
            simulatedNetwork.shutdown();
            simulatedNetwork = null;
        }
    }
}

