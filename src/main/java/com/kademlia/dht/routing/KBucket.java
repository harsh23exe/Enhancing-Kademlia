package com.kademlia.dht.routing;

import com.kademlia.dht.node.Node;
import com.kademlia.dht.node.NodeId;
import com.kademlia.dht.util.Pair;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.stream.Stream;

/**
 * K-bucket: fixed capacity bucket in ID range with LRU eviction and replacement cache.
 */
public class KBucket {
    private static final BigInteger TWO = BigInteger.valueOf(2);
    private static final BigInteger ONE = BigInteger.ONE;

    private final BigInteger rangeLower;
    private final BigInteger rangeUpper;
    private final int ksize;
    private final LinkedHashMap<NodeId, Node> nodes = new LinkedHashMap<>();
    private final LinkedHashMap<NodeId, Node> replacements = new LinkedHashMap<>();
    private volatile long lastUpdated;

    public KBucket(BigInteger rangeLower, BigInteger rangeUpper, int ksize) {
        this.rangeLower = rangeLower;
        this.rangeUpper = rangeUpper;
        this.ksize = ksize;
        touchLastUpdated();
    }

    private void touchLastUpdated() {
        this.lastUpdated = System.nanoTime();
    }

    public synchronized boolean addNode(Node node) {
        if (nodes.containsKey(node.id())) {
            nodes.remove(node.id());
            nodes.put(node.id(), node);
            touchLastUpdated();
            return true;
        }
        if (nodes.size() < ksize) {
            nodes.put(node.id(), node);
            touchLastUpdated();
            return true;
        }
        replacements.put(node.id(), node);
        if (replacements.size() > ksize * 5) {
            replacements.remove(replacements.keySet().iterator().next());
        }
        return false;
    }

    public synchronized void removeNode(Node node) {
        nodes.remove(node.id());
        replacements.remove(node.id());
    }

    public synchronized Pair<KBucket, KBucket> split() {
        BigInteger midpoint = rangeLower.add(rangeUpper).divide(TWO);
        KBucket one = new KBucket(rangeLower, midpoint, ksize);
        KBucket two = new KBucket(midpoint.add(ONE), rangeUpper, ksize);
        Stream.concat(nodes.values().stream(), replacements.values().stream())
                .forEach(n -> {
                    KBucket bucket = n.id().toBigInteger().compareTo(midpoint) <= 0 ? one : two;
                    bucket.addNode(n);
                });
        return Pair.of(one, two);
    }

    public boolean hasInRange(Node node) {
        BigInteger id = node.id().toBigInteger();
        return rangeLower.compareTo(id) <= 0 && id.compareTo(rangeUpper) <= 0;
    }

    public int depth() {
        BigInteger range = rangeUpper.subtract(rangeLower).add(ONE);
        return range.bitLength() - 1;
    }

    public int size() {
        return nodes.size();
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public synchronized java.util.List<Node> getNodes() {
        return java.util.List.copyOf(nodes.values());
    }

    public synchronized Node getReplacementFor(NodeId nodeId) {
        return replacements.get(nodeId);
    }
}
