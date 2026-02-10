package com.kademlia.dht;

import com.kademlia.dht.network.Server;
import com.kademlia.dht.util.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests: two-node set/get. testSetGet uses real UDP (disabled by default).
 * testSetGetSimulated and testSingleNodeSimulated use in-process transport.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ServerIntegrationTest {

    private Server server1;
    private Server server2;
    private static final int PORT1 = 18468;
    private static final int PORT2 = 18469;

    @AfterEach
    void tearDown() {
        if (server1 != null) server1.close();
        if (server2 != null) server2.close();
    }

    @Test
    @Disabled("UDP test; run manually with --tests 'com.kademlia.dht.ServerIntegrationTest.testSetGet'")
    void testSetGet() throws Exception {
        server1 = new Server(20, 3, null, null);
        server2 = new Server(20, 3, null, null);
        server1.listen(PORT1, "127.0.0.1");
        server2.listen(PORT2, "127.0.0.1");
        server2.bootstrap(List.of(Pair.of("127.0.0.1", PORT1))).get(10, TimeUnit.SECONDS);
        server1.set("test_key", "test_value".getBytes()).get(10, TimeUnit.SECONDS);
        Optional<byte[]> result = server2.get("test_key").get(10, TimeUnit.SECONDS);
        assertTrue(result.isPresent());
        assertEquals("test_value", new String(result.get()));
    }

    @Test
    @Disabled("Simulated cluster; enable to run without UDP")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testSingleNodeSimulated() throws Exception {
        try (TestCluster cluster = TestCluster.createSimulated(1, 38468, 20, 3)) {
            Optional<byte[]> result = cluster.getServers().get(0).get("missing").get(2, TimeUnit.SECONDS);
            assertTrue(result.isEmpty());
        }
    }

    @Test
    @Disabled("Simulated cluster; enable to run without UDP")
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testSetGetSimulated() throws Exception {
        try (TestCluster cluster = TestCluster.createSimulated(2, 28468, 20, 3)) {
            List<Server> servers = cluster.getServers();
            servers.get(0).set("test_key", "test_value".getBytes()).get(10, TimeUnit.SECONDS);
            Optional<byte[]> result = servers.get(1).get("test_key").get(10, TimeUnit.SECONDS);
            assertTrue(result.isPresent());
            assertEquals("test_value", new String(result.get()));
        }
    }
}
