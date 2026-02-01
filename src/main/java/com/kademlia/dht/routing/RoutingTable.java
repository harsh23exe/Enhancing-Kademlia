package com.kademlia.dht.routing;

import com.kademlia.dht.node.Node;
import com.kademlia.dht.node.NodeId;
import com.kademlia.dht.util.Pair;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Routing table: list of K-buckets with splitting and neighbor lookup.
 */
public class RoutingTable {
    private static final BigInteger TWO = BigInteger.valueOf(2);
    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger MAX_ID = TWO.pow(160).subtract(ONE);

    private final Node selfNode;
    private final int ksize;
    private final List<KBucket> buckets = new CopyOnWriteArrayList<>();

    public RoutingTable(Node selfNode, int ksize) {
        this.selfNode = selfNode;
        this.ksize = ksize;
        buckets.add(new KBucket(BigInteger.ZERO, MAX_ID, ksize));
    }

    public Node getSelfNode() {
        return selfNode;
    }

    public int getKsize() {
        return ksize;
    }

    public List<KBucket> getBuckets() {
        return buckets;
    }

    public int getBucketFor(Node node) {
        BigInteger id = node.id().toBigInteger();
        for (int i = 0; i < buckets.size(); i++) {
            KBucket b = buckets.get(i);
            if (b.hasInRange(node)) {
                return i;
            }
        }
        return buckets.size() - 1;
    }

    public void addContact(Node node) {
        if (node.id().equals(selfNode.id())) {
            return;
        }
        int idx = getBucketFor(node);
        KBucket bucket = buckets.get(idx);
        if (bucket.addNode(node)) {
            return;
        }
        if (bucket.hasInRange(selfNode) || bucket.depth() % 5 != 0) {
            synchronized (this) {
                if (idx < buckets.size()) {
                    splitBucket(idx);
                    addContact(node);
                }
            }
        }
    }

    public void removeContact(Node node) {
        int idx = getBucketFor(node);
        if (idx < buckets.size()) {
            buckets.get(idx).removeNode(node);
        }
    }

    public boolean isNewNode(Node node) {
        int idx = getBucketFor(node);
        if (idx >= buckets.size()) return true;
        List<Node> nodes = buckets.get(idx).getNodes();
        return nodes.stream().noneMatch(n -> n.id().equals(node.id()));
    }

    private synchronized void splitBucket(int idx) {
        if (idx >= buckets.size()) return;
        KBucket bucket = buckets.get(idx);
        Pair<KBucket, KBucket> split = bucket.split();
        buckets.set(idx, split.left());
        buckets.add(idx + 1, split.right());
    }

    /**
     * Find K closest nodes to target by XOR distance. Uses min-heap: smaller distance = closer.
     */
    public List<Node> findNeighbors(Node target, int k) {
        PriorityQueue<Pair<Integer, Node>> heap = new PriorityQueue<>(
                Comparator.comparingInt(Pair::left)
        );
        TableTraverser traverser = new TableTraverser(this, target);
        while (traverser.hasNext()) {
            Node node = traverser.next();
            if (!node.id().equals(target.id())) {
                heap.offer(Pair.of(target.distanceTo(node), node));
                if (heap.size() > k) {
                    heap.poll();
                }
            }
        }
        List<Node> result = new ArrayList<>();
        while (!heap.isEmpty()) {
            result.add(heap.poll().right());
        }
        return result;
    }

    public int getNodeCount() {
        return buckets.stream().mapToInt(KBucket::size).sum();
    }
}
