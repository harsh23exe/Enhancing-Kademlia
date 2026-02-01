package com.kademlia.dht.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adaptive quorum: adjust R/W based on latency and failures.
 */
public class DynamicQuorum {
    private static final Logger log = LoggerFactory.getLogger(DynamicQuorum.class);
    private static final long ADJUSTMENT_INTERVAL_NANOS = 5_000_000_000L;
    private static final int MAX_SAMPLES = 100;
    private static final double HIGH_LATENCY_THRESHOLD = 1.0;

    private final int minR;
    private final int minW;
    private final int minN;
    private final AtomicInteger currentR;
    private final AtomicInteger currentW;
    private final AtomicInteger currentN;
    private final ConcurrentLinkedQueue<Long> responseTimes = new ConcurrentLinkedQueue<>();
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile long lastAdjustment = System.nanoTime();

    public DynamicQuorum(int minR, int minW, int minN) {
        this.minR = minR;
        this.minW = minW;
        this.minN = minN;
        this.currentR = new AtomicInteger(minR);
        this.currentW = new AtomicInteger(minW);
        this.currentN = new AtomicInteger(minN);
    }

    public void adjustQuorum(long latencyNanos, boolean success) {
        long now = System.nanoTime();
        responseTimes.offer(latencyNanos);
        while (responseTimes.size() > MAX_SAMPLES) {
            responseTimes.poll();
        }
        if (!success) {
            failureCount.incrementAndGet();
        } else {
            failureCount.updateAndGet(c -> Math.max(0, c - 1));
        }
        if (now - lastAdjustment < ADJUSTMENT_INTERVAL_NANOS) {
            return;
        }
        lastAdjustment = now;
        double avgLatencySec = responseTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0) / 1_000_000_000.0;
        int r = currentR.get();
        int w = currentW.get();
        int n = currentN.get();
        if (avgLatencySec > HIGH_LATENCY_THRESHOLD || failureCount.get() > 3) {
            r = Math.min(n - 1, r + 1);
            w = Math.max(minW, w - 1);
        } else {
            r = Math.max(minR, r - 1);
            w = Math.min(n - 1, w + 1);
        }
        if (r + w <= n) {
            w = n - r + 1;
        }
        currentR.set(r);
        currentW.set(w);
        log.debug("Adjusted quorum: R={}, W={}, N={} (latency={}, failures={})",
                r, w, n, avgLatencySec, failureCount.get());
    }

    public int getReadQuorum() {
        return currentR.get();
    }

    public int getWriteQuorum() {
        return currentW.get();
    }

    public int getTotalReplicas() {
        return currentN.get();
    }
}
