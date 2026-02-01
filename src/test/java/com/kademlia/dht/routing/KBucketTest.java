package com.kademlia.dht.routing;

import com.kademlia.dht.node.Node;
import com.kademlia.dht.node.NodeId;
import com.kademlia.dht.util.Digest;
import com.kademlia.dht.util.Pair;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KBucketTest {

    private static final BigInteger TWO_POW_160 = BigInteger.ONE.shiftLeft(160);

    private Node randomNode(int seed) throws Exception {
        byte[] id = Digest.hash("node" + seed);
        return new Node(new NodeId(id), InetAddress.getByName("127.0.0.1"), 8468 + seed);
    }

    @Test
    void testBucketSplit() throws Exception {
        KBucket bucket = new KBucket(BigInteger.ZERO, TWO_POW_160.subtract(BigInteger.ONE), 20);
        for (int i = 0; i < 25; i++) {
            bucket.addNode(randomNode(i));
        }
        Pair<KBucket, KBucket> split = bucket.split();
        assertTrue(split.left().size() + split.right().size() >= 20);
    }

    @Test
    void testAddNodeAndLru() throws Exception {
        KBucket bucket = new KBucket(BigInteger.ZERO, TWO_POW_160.subtract(BigInteger.ONE), 5);
        Node n1 = randomNode(1);
        bucket.addNode(n1);
        assertEquals(1, bucket.size());
        bucket.addNode(n1);
        assertEquals(1, bucket.size());
    }

    @Test
    void testHasInRange() throws Exception {
        Node n = randomNode(1);
        KBucket bucket = new KBucket(BigInteger.ZERO, TWO_POW_160.subtract(BigInteger.ONE), 20);
        assertTrue(bucket.hasInRange(n));
    }
}
