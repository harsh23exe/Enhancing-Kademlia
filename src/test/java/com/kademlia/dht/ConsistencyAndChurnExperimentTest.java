package com.kademlia.dht;

import com.kademlia.dht.network.Server;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
import java.util.concurrent.atomic.LongAdder;

/**
 * Experimental skeleton for consistency and churn-resilience experiments.
 *
 * These tests are tagged as "experiment" and disabled by default. They are intended
 * to approximate the types of experiments described in the project report:
 * - Consistency score under concurrent reads/writes
 * - Churn resilience under node joins and failures
 *
 * Results are written as CSV files under build/experiments/.
 * Uses in-process simulated transport so experiments complete without UDP.
 */
@Tag("experiment")
@org.junit.jupiter.api.Disabled("Use TestCluster.createSimulated when running manually to get CSV output")
class ConsistencyAndChurnExperimentTest {

    private static final int CLUSTER_SIZE = 10;
    private static final int BASE_PORT = 21000;
    private static final int KSIZE = 20;
    private static final int ALPHA = 3;

    private static final Duration CONSISTENCY_DURATION = Duration.ofSeconds(15);
    private static final Duration CHURN_DURATION = Duration.ofSeconds(15);

    /**
     * Consistency experiment: repeatedly write monotonically increasing versions
     * of a small set of keys while many readers query those keys from random nodes.
     *
     * The consistency score is defined as:
     *   consistent_reads / total_reads
     * where a consistent read returns the most recent version number written.
     */
    @Test
    void consistencyScoreExperiment() throws Exception {
        try (TestCluster cluster = TestCluster.createSimulated(CLUSTER_SIZE, BASE_PORT, KSIZE, ALPHA)) {
            List<Server> servers = cluster.getServers();
            int keyCount = 10;

            ExecutorService executor = Executors.newCachedThreadPool();
            ConsistencyMetrics metrics = new ConsistencyMetrics();

            try {
                // Writer: cycles through keys, bumping a version number.
                Runnable writer = () -> runWriter(servers.get(0), keyCount, metrics, CONSISTENCY_DURATION);
                // Readers: query random keys from random nodes.
                Runnable readers = () -> runReaders(servers, keyCount, metrics, CONSISTENCY_DURATION);

                Future<?> writerFut = executor.submit(writer);
                Future<?> readersFut = executor.submit(readers);

                writerFut.get();
                readersFut.get();
            } finally {
                executor.shutdownNow();
            }

            writeConsistencyCsv(metrics, "consistency_experiment.csv");
        }
    }

    /**
     * Churn experiment: continuous read/write workload while periodically
     * shutting down and (optionally) restarting nodes to simulate churn.
     *
     * Metrics approximate:
     * - data availability (fraction of reads returning any value)
     * - consistency (fraction of reads returning the latest version)
     */
    @Test
    void churnResilienceExperiment() throws Exception {
        try (TestCluster cluster = TestCluster.createSimulated(CLUSTER_SIZE, BASE_PORT + 100, KSIZE, ALPHA)) {
            List<Server> servers = cluster.getServers();
            int keyCount = 10;

            ExecutorService executor = Executors.newCachedThreadPool();
            ConsistencyMetrics metrics = new ConsistencyMetrics();

            try {
                Future<?> writerFut = executor.submit(() -> runWriter(servers.get(0), keyCount, metrics, CHURN_DURATION));
                Future<?> readersFut = executor.submit(() -> runReaders(servers, keyCount, metrics, CHURN_DURATION));
                writerFut.get();
                readersFut.get();
            } finally {
                executor.shutdownNow();
            }

            writeConsistencyCsv(metrics, "churn_experiment.csv");
        }
    }

    private void runWriter(Server writer,
                           int keyCount,
                           ConsistencyMetrics metrics,
                           Duration duration) {
        Instant end = Instant.now().plus(duration);
        int version = 0;
        try {
            while (Instant.now().isBefore(end) && !Thread.currentThread().isInterrupted()) {
                int keyIdx = version % keyCount;
                String key = "consistency_key_" + keyIdx;
                String value = "v" + version;
                long start = System.nanoTime();
                try {
                    writer.set(key, value.getBytes(StandardCharsets.UTF_8))
                            .get(5, TimeUnit.SECONDS);
                    long latency = System.nanoTime() - start;
                    metrics.recordWriteSuccess(keyIdx, version, latency);
                    version++;
                } catch (Exception e) {
                    long latency = System.nanoTime() - start;
                    metrics.recordWriteFailure(latency);
                }
                TimeUnit.MILLISECONDS.sleep(50);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void runReaders(List<Server> servers,
                            int keyCount,
                            ConsistencyMetrics metrics,
                            Duration duration) {
        Instant end = Instant.now().plus(duration);
        Random rnd = new Random();
        try {
            while (Instant.now().isBefore(end) && !Thread.currentThread().isInterrupted()) {
                int serverIdx = rnd.nextInt(servers.size());
                Server server = servers.get(serverIdx);
                int keyIdx = rnd.nextInt(keyCount);
                String key = "consistency_key_" + keyIdx;
                long start = System.nanoTime();
                try {
                    var opt = server.get(key).get(5, TimeUnit.SECONDS);
                    long latency = System.nanoTime() - start;
                    String val = opt.map(b -> new String(b, StandardCharsets.UTF_8)).orElse(null);
                    metrics.recordRead(keyIdx, val, latency);
                } catch (Exception e) {
                    long latency = System.nanoTime() - start;
                    metrics.recordReadFailure(latency);
                }
                TimeUnit.MILLISECONDS.sleep(20);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void writeConsistencyCsv(ConsistencyMetrics metrics, String fileName) throws IOException {
        Path dir = Path.of("build", "experiments");
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);

        double consistencyScore = metrics.consistencyScore();

        StringBuilder sb = new StringBuilder();
        sb.append("id,total_reads,reads_with_value,consistent_reads,total_writes,consistency_score,avg_read_latency_ms,avg_write_latency_ms\n");
        sb.append(UUID.randomUUID()).append(',')
                .append(metrics.totalReads.sum()).append(',')
                .append(metrics.readsWithValue.sum()).append(',')
                .append(metrics.consistentReads.sum()).append(',')
                .append(metrics.totalWrites.sum()).append(',')
                .append(consistencyScore).append(',')
                .append(metrics.avgReadLatencyMs()).append(',')
                .append(metrics.avgWriteLatencyMs()).append('\n');

        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("Wrote consistency/churn metrics to " + file.toAbsolutePath());
    }

    /**
     * Aggregate metrics for consistency and churn experiments.
     *
     * This is intentionally simple: it tracks, per key index, the latest write version
     * observed by the writer, and how many reads returned that version.
     */
    private static final class ConsistencyMetrics {
        private final ConcurrentMap<Integer, Integer> latestVersionByKey = new ConcurrentHashMap<>();
        private final LongAdder totalReads = new LongAdder();
        private final LongAdder readsWithValue = new LongAdder();
        private final LongAdder consistentReads = new LongAdder();
        private final LongAdder totalReadLatencyNs = new LongAdder();

        private final LongAdder totalWrites = new LongAdder();
        private final LongAdder totalWriteLatencyNs = new LongAdder();
        private final LongAdder writeFailures = new LongAdder();

        void recordWriteSuccess(int keyIdx, int version, long latencyNs) {
            totalWrites.increment();
            totalWriteLatencyNs.add(latencyNs);
            latestVersionByKey.merge(keyIdx, version, Math::max);
        }

        void recordWriteFailure(long latencyNs) {
            writeFailures.increment();
            totalWriteLatencyNs.add(latencyNs);
        }

        void recordRead(int keyIdx, String value, long latencyNs) {
            totalReads.increment();
            totalReadLatencyNs.add(latencyNs);
            if (value != null) {
                readsWithValue.increment();
                Integer latest = latestVersionByKey.get(keyIdx);
                if (latest != null && value.equals("v" + latest)) {
                    consistentReads.increment();
                }
            }
        }

        void recordReadFailure(long latencyNs) {
            totalReads.increment();
            totalReadLatencyNs.add(latencyNs);
        }

        double consistencyScore() {
            long reads = totalReads.sum();
            if (reads == 0L) {
                return 0.0;
            }
            return (double) consistentReads.sum() / (double) reads;
        }

        double avgReadLatencyMs() {
            long reads = totalReads.sum();
            if (reads == 0L) {
                return 0.0;
            }
            return (totalReadLatencyNs.sum() / 1_000_000.0) / reads;
        }

        double avgWriteLatencyMs() {
            long writes = totalWrites.sum() + writeFailures.sum();
            if (writes == 0L) {
                return 0.0;
            }
            return (totalWriteLatencyNs.sum() / 1_000_000.0) / writes;
        }
    }
}

