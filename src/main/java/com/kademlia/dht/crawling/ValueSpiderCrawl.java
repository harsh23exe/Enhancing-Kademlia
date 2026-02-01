package com.kademlia.dht.crawling;

import com.kademlia.dht.node.Node;
import com.kademlia.dht.node.NodeHeap;
import com.kademlia.dht.protocol.FindValueResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Spider crawl for value discovery: find value or K closest nodes.
 */
public class ValueSpiderCrawl extends SpiderCrawl {
    private static final Logger log = LoggerFactory.getLogger(ValueSpiderCrawl.class);

    private final NodeHeap nearestWithoutValue;
    private volatile Optional<byte[]> foundValue = Optional.empty();

    public ValueSpiderCrawl(KademliaProtocol protocol, Node target, List<Node> initialNodes, int ksize, int alpha) {
        super(protocol, target, initialNodes, ksize, alpha);
        this.nearestWithoutValue = new NodeHeap(target, ksize);
    }

    public CompletableFuture<Optional<byte[]>> find() {
        return doFind();
    }

    private CompletableFuture<Optional<byte[]>> doFind() {
        return crawl(node -> protocol.callFindValue(node, target.id().getBytes()))
                .thenApply(v -> foundValue);
    }

    @Override
    protected CompletableFuture<Void> processResponses(List<CompletableFuture<?>> responses) {
        List<byte[]> foundValues = new ArrayList<>();
        for (CompletableFuture<?> future : responses) {
            try {
                FindValueResponse resp = (FindValueResponse) future.get();
                if (resp != null) {
                    if (resp.value().isPresent()) {
                        foundValues.add(resp.value().get());
                    } else {
                        nearestWithoutValue.push(resp.nodes());
                        nearest.push(resp.nodes());
                    }
                }
            } catch (Exception e) {
                log.debug("Node failed during value crawl", e);
            }
        }
        if (!foundValues.isEmpty()) {
            byte[] winner = mostCommon(foundValues);
            foundValue = Optional.of(winner);
            List<Node> withoutValue = nearestWithoutValue.toList();
            if (!withoutValue.isEmpty()) {
                protocol.callStore(withoutValue.get(0), target.id().getBytes(), winner);
            }
            return CompletableFuture.completedFuture(null);
        }
        if (nearest.haveContactedAll()) {
            return CompletableFuture.completedFuture(null);
        }
        return doFind().thenApply(v -> null);
    }

    private static byte[] mostCommon(List<byte[]> values) {
        Map<String, Long> counts = values.stream()
                .map(v -> java.util.Arrays.toString(v))
                .collect(Collectors.groupingBy(x -> x, Collectors.counting()));
        String winner = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        if (winner == null) return values.get(0);
        for (byte[] v : values) {
            if (java.util.Arrays.toString(v).equals(winner)) return v;
        }
        return values.get(0);
    }
}
