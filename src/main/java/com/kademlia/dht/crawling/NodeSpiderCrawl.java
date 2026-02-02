package com.kademlia.dht.crawling;

import com.kademlia.dht.node.Node;
import com.kademlia.dht.protocol.FindNodeResponse;
import com.kademlia.dht.protocol.KademliaProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Spider crawl for node discovery: find K closest nodes to target.
 * Stops when the K closest have been contacted or after max iterations.
 */
public class NodeSpiderCrawl extends SpiderCrawl {
    private static final Logger log = LoggerFactory.getLogger(NodeSpiderCrawl.class);
    private static final int MAX_ITERATIONS = 50;

    private int iterationCount;

    public NodeSpiderCrawl(KademliaProtocol protocol, Node target, List<Node> initialNodes, int ksize, int alpha) {
        super(protocol, target, initialNodes, ksize, alpha);
        this.iterationCount = 0;
    }

    public CompletableFuture<List<Node>> find() {
        return doFind();
    }

    private CompletableFuture<List<Node>> doFind() {
        return crawl(node -> protocol.callFindNode(node, target.id()))
                .thenApply(v -> nearest.toList());
    }

    @Override
    protected CompletableFuture<Void> processResponses(List<CompletableFuture<?>> responses) {
        for (CompletableFuture<?> future : responses) {
            try {
                FindNodeResponse resp = (FindNodeResponse) future.get();
                if (resp != null && resp.nodes() != null) {
                    nearest.push(resp.nodes());
                }
            } catch (Exception e) {
                log.debug("Node failed during crawl", e);
            }
        }
        iterationCount++;
        if (nearest.haveContactedAll() || iterationCount >= MAX_ITERATIONS) {
            return CompletableFuture.completedFuture(null);
        }
        return doFind().thenApply(v -> null);
    }
}
