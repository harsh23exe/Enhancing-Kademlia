package com.kademlia.dht.node;

import com.kademlia.dht.util.Digest;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

class NodeTest {

    @Test
    void testNodeCreation() throws Exception {
        Node node = new Node(
                new NodeId(Digest.hash("test")),
                InetAddress.getByName("127.0.0.1"),
                8468
        );
        assertEquals(8468, node.port());
        assertEquals("127.0.0.1:8468", node.toString());
    }

    @Test
    void testDistanceTo() throws Exception {
        Node n1 = new Node(new NodeId(Digest.hash("a")), InetAddress.getByName("127.0.0.1"), 8468);
        Node n2 = new Node(new NodeId(Digest.hash("b")), InetAddress.getByName("127.0.0.1"), 8469);
        assertTrue(n1.distanceTo(n2) >= 0);
        assertEquals(0, n1.distanceTo(n1));
    }

    @Test
    void testSameHome() throws Exception {
        Node n1 = new Node(new NodeId(Digest.hash("a")), InetAddress.getByName("127.0.0.1"), 8468);
        Node n2 = new Node(new NodeId(Digest.hash("b")), InetAddress.getByName("127.0.0.1"), 8468);
        assertTrue(n1.sameHome(n2));
    }
}
