package com.kademlia.dht.node;

import com.kademlia.dht.util.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Priority queue of nodes by distance to target. Tracks contacted set for spider crawl.
 */
public class NodeHeap {
    private final Node target;
    private final int maxSize;
    private final PriorityQueue<Pair<Integer, Node>> heap;
    private final Set<NodeId> contacted = new HashSet<>();

    public NodeHeap(Node target, int maxSize) {
        this.target = target;
        this.maxSize = maxSize;
        this.heap = new PriorityQueue<>(Comparator.comparingInt(Pair::left));
    }

    public synchronized void push(List<Node> nodes) {
        for (Node node : nodes) {
            if (!contains(node)) {
                heap.offer(Pair.of(target.distanceTo(node), node));
            }
        }
    }

    public synchronized void markContacted(Node node) {
        contacted.add(node.id());
    }

    public synchronized List<Node> getNotContacted() {
        List<Pair<Integer, Node>> sorted = new ArrayList<>(heap);
        sorted.sort(Comparator.comparingInt(Pair::left));
        return sorted.stream()
                .map(Pair::right)
                .filter(n -> !contacted.contains(n.id()))
                .limit(maxSize)
                .toList();
    }

    public synchronized boolean haveContactedAll() {
        return getNotContacted().isEmpty();
    }

    public synchronized List<NodeId> getIds() {
        List<Pair<Integer, Node>> sorted = new ArrayList<>(heap);
        sorted.sort(Comparator.comparingInt(Pair::left));
        return sorted.stream()
                .map(p -> p.right().id())
                .limit(maxSize)
                .toList();
    }

    public synchronized List<Node> toList() {
        List<Pair<Integer, Node>> sorted = new ArrayList<>(heap);
        sorted.sort(Comparator.comparingInt(Pair::left));
        return sorted.stream()
                .map(Pair::right)
                .limit(maxSize)
                .toList();
    }

    private boolean contains(Node node) {
        return heap.stream().anyMatch(p -> p.right().id().equals(node.id()));
    }
}
