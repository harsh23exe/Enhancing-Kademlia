package com.kademlia.dht;

import com.kademlia.dht.network.Server;
import com.kademlia.dht.util.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

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
}
