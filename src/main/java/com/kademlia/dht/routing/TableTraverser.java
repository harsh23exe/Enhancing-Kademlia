package com.kademlia.dht.routing;

import com.kademlia.dht.node.Node;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Iterator over all nodes in the routing table (all buckets).
 */
public class TableTraverser implements Iterator<Node> {
    private final RoutingTable table;
    private final Node target;
    private int bucketIndex;
    private Iterator<Node> currentBucketNodes;

    public TableTraverser(RoutingTable table, Node target) {
        this.table = table;
        this.target = target;
        this.bucketIndex = 0;
        this.currentBucketNodes = nextBucketIterator();
    }

    private Iterator<Node> nextBucketIterator() {
        List<KBucket> buckets = table.getBuckets();
        while (bucketIndex < buckets.size()) {
            List<Node> nodes = buckets.get(bucketIndex).getNodes();
            bucketIndex++;
            if (!nodes.isEmpty()) {
                return nodes.iterator();
            }
        }
        return List.<Node>of().iterator();
    }

    @Override
    public boolean hasNext() {
        while (currentBucketNodes != null && !currentBucketNodes.hasNext()) {
            currentBucketNodes = nextBucketIterator();
        }
        return currentBucketNodes != null && currentBucketNodes.hasNext();
    }

    @Override
    public Node next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return currentBucketNodes.next();
    }
}
