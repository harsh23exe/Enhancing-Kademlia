package com.kademlia.dht.crawling;

import com.kademlia.dht.node.Node;
import com.kademlia.dht.node.NodeHeap;
import com.kademlia.dht.node.NodeId;
import com.kademlia.dht.protocol.KademliaProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Base spider crawl: iterative contact of alpha closest uncontacted nodes.
 */
public abstract class SpiderCrawl {
    private static final Logger log = LoggerFactory.getLogger(SpiderCrawl.class);

    protected final KademliaProtocol protocol;
    protected final Node target;
    protected final int ksize;
    protected final int alpha;
    protected final NodeHeap nearest;
    protected List<NodeId> lastIdsCrawled = List.of();

    public SpiderCrawl(KademliaProtocol protocol, Node target, List<Node> initialNodes, int ksize, int alpha) {
        this.protocol = protocol;
        this.target = target;
        this.ksize = ksize;
        this.alpha = alpha;
        this.nearest = new NodeHeap(target, ksize);
        this.nearest.push(initialNodes);
    }

    protected CompletableFuture<Void> crawl(Function<Node, CompletableFuture<?>> rpcMethod) {
        log.debug("Crawling with {} nearest nodes", nearest.toList().size());
        int count = alpha;
        if (nearest.getIds().equals(lastIdsCrawled)) {
            count = nearest.getNotContacted().size();
        }
        lastIdsCrawled = nearest.getIds();
        List<Node> toContact = nearest.getNotContacted().stream().limit(count).toList();
        if (toContact.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        List<CompletableFuture<?>> futures = toContact.stream()
                .peek(nearest::markContacted)
                .map(rpcMethod)
                .toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenCompose(v -> processResponses(futures));
    }

    protected abstract CompletableFuture<Void> processResponses(List<CompletableFuture<?>> responses);
}
