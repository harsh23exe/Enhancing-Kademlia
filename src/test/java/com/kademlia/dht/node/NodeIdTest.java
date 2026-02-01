package com.kademlia.dht.node;

import com.kademlia.dht.util.Digest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NodeIdTest {

    @Test
    void testXorDistance() {
        NodeId n1 = new NodeId(Digest.hash("node1"));
        NodeId n2 = new NodeId(Digest.hash("node2"));
        assertTrue(n1.distanceTo(n2) > 0);
        assertEquals(0, n1.distanceTo(n1));
    }

    @Test
    void testEqualsAndHashCode() {
        byte[] id = Digest.hash("test");
        NodeId n1 = new NodeId(id);
        NodeId n2 = new NodeId(id.clone());
        assertEquals(n1, n2);
        assertEquals(n1.hashCode(), n2.hashCode());
    }

    @Test
    void testRejectsWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> new NodeId(new byte[19]));
        assertThrows(IllegalArgumentException.class, () -> new NodeId(new byte[21]));
    }

    @Test
    void testToBigInteger() {
        NodeId n = new NodeId(Digest.hash("id"));
        assertNotNull(n.toBigInteger());
        assertTrue(n.toBigInteger().bitLength() <= 160);
    }
}
