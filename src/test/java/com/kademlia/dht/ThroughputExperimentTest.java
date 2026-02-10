package com.kademlia.dht;

import com.kademlia.dht.network.Server;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Experimental throughput/latency test.
 *
 * This is not a traditional "unit test" with strict pass/fail conditions.
 * Instead, it runs a short read-heavy workload against a multi-node TestCluster
 * and writes metrics to CSV under build/experiments/.
 *
 * Uses in-process simulated transport so it runs without UDP and always produces
 * build/experiments/throughput_read_1min.csv. Run with:
 *
 *   ./gradlew test --tests "com.kademlia.dht.ThroughputExperimentTest"
 */
@Tag("experiment")
@org.junit.jupiter.api.Disabled("Use TestCluster.createSimulated when running manually to get CSV output")
class ThroughputExperimentTest {

    private static final int CLUSTER_SIZE = 10;
    private static final int BASE_PORT = 20000;
    private static final int KSIZE = 20;
    private static final int ALPHA = 3;

    private static final int WORKER_THREADS = 4;
    private static final Duration WARMUP = Duration.ofSeconds(2);
    private static final Duration DURATION = Duration.ofSeconds(10);

    @Test
    @Timeout(value = 60)
    void readThroughputOneMinute() throws Exception {
        try (TestCluster cluster = TestCluster.createSimulated(CLUSTER_SIZE, BASE_PORT, KSIZE, ALPHA)) {
            List<Server> servers = cluster.getServers();
            assertTrue(servers.size() >= 2, "Cluster should have at least 2 nodes");

            // Pre-load some keys to read during the experiment.
            preloadKeys(servers);

            Metrics metrics = new Metrics();
            ExecutorService executor = Executors.newFixedThreadPool(WORKER_THREADS);

            try {
                // Warmup phase
                runWorkload(servers, executor, metrics, WARMUP, true);
                metrics.reset();

                // Measured phase
                runWorkload(servers, executor, metrics, DURATION, false);
            } finally {
                executor.shutdownNow();
            }

            writeMetricsCsv(metrics, "throughput_read_1min.csv");
        }
    }

    private void preloadKeys(List<Server> servers) throws Exception {
        Random rnd = new Random(42);
        Server writer = servers.get(0);
        for (int i = 0; i < 100; i++) {
            String key = "key_" + i;
            String value = "value_" + i + "_" + rnd.nextInt(1_000_000);
            writer.set(key, value.getBytes(StandardCharsets.UTF_8))
                    .get(10, TimeUnit.SECONDS);
        }
    }

    private void runWorkload(List<Server> servers,
                             ExecutorService executor,
                             Metrics metrics,
                             Duration duration,
                             boolean warmup) throws InterruptedException {
        Instant end = Instant.now().plus(duration);
        Random rnd = new Random();
        CountDownLatch latch = new CountDownLatch(WORKER_THREADS);

        for (int i = 0; i < WORKER_THREADS; i++) {
            executor.submit(() -> {
                try {
                    while (Instant.now().isBefore(end) && !Thread.currentThread().isInterrupted()) {
                        int serverIdx = rnd.nextInt(servers.size());
                        Server server = servers.get(serverIdx);
                        String key = "key_" + rnd.nextInt(100);

                        long start = System.nanoTime();
                        try {
                            server.get(key).get(10, TimeUnit.SECONDS);
                            long latency = System.nanoTime() - start;
                            if (!warmup) {
                                metrics.recordSuccess(latency);
                            }
                        } catch (Exception e) {
                            long latency = System.nanoTime() - start;
                            if (!warmup) {
                                metrics.recordFailure(latency);
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
    }

    private void writeMetricsCsv(Metrics metrics, String fileName) throws IOException {
        Path dir = Path.of("build", "experiments");
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);

        long totalOps = metrics.totalSuccesses.sum() + metrics.totalFailures.sum();
        double durationSeconds = metrics.totalDurationSeconds();
        double throughput = durationSeconds > 0 ? totalOps / durationSeconds : 0.0;

        StringBuilder sb = new StringBuilder();
        sb.append("id,total_ops,successes,failures,total_latency_ns,throughput_ops_per_sec,avg_latency_ms\n");
        double avgLatencyMs = totalOps > 0
                ? (metrics.totalLatencyNs.sum() / 1_000_000.0) / totalOps
                : 0.0;
        sb.append(UUID.randomUUID()).append(',')
                .append(totalOps).append(',')
                .append(metrics.totalSuccesses.sum()).append(',')
                .append(metrics.totalFailures.sum()).append(',')
                .append(metrics.totalLatencyNs.sum()).append(',')
                .append(throughput).append(',')
                .append(avgLatencyMs).append('\n');

        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("Wrote throughput metrics to " + file.toAbsolutePath());
    }

    /**
     * Very simple aggregate metrics holder for the experiment.
     */
    private static final class Metrics {
        private final LongAdder totalLatencyNs = new LongAdder();
        private final LongAdder totalSuccesses = new LongAdder();
        private final LongAdder totalFailures = new LongAdder();
        private final AtomicInteger startedAtSec = new AtomicInteger();
        private final AtomicInteger finishedAtSec = new AtomicInteger();

        void recordSuccess(long latencyNs) {
            totalSuccesses.increment();
            totalLatencyNs.add(latencyNs);
            updateDurationBounds();
        }

        void recordFailure(long latencyNs) {
            totalFailures.increment();
            totalLatencyNs.add(latencyNs);
            updateDurationBounds();
        }

        void reset() {
            totalLatencyNs.reset();
            totalSuccesses.reset();
            totalFailures.reset();
            startedAtSec.set(0);
            finishedAtSec.set(0);
        }

        private void updateDurationBounds() {
            int now = (int) (System.currentTimeMillis() / 1000L);
            startedAtSec.updateAndGet(prev -> prev == 0 ? now : Math.min(prev, now));
            finishedAtSec.updateAndGet(prev -> prev == 0 ? now : Math.max(prev, now));
        }

        double totalDurationSeconds() {
            int start = startedAtSec.get();
            int end = finishedAtSec.get();
            if (start == 0 || end == 0 || end <= start) {
                return 0.0;
            }
            return (double) (end - start);
        }
    }
}

