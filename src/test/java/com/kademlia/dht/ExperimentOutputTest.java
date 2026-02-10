package com.kademlia.dht;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Writes sample experiment CSV files under build/experiments/ so the output
 * format and directory exist for showcase. Run with:
 *   ./gradlew test --tests "com.kademlia.dht.ExperimentOutputTest"
 * then inspect build/experiments/.
 */
@Tag("experiment")
class ExperimentOutputTest {

    @Test
    void writeSampleExperimentOutput() throws Exception {
        Path dir = Path.of("build", "experiments");
        Files.createDirectories(dir);

        String throughputCsv = "id,total_ops,successes,failures,total_latency_ns,throughput_ops_per_sec,avg_latency_ms\n"
                + UUID.randomUUID() + ",1000,980,20,50000000,50.0,0.05\n";
        Files.writeString(dir.resolve("throughput_read_1min.csv"), throughputCsv, StandardCharsets.UTF_8);

        String consistencyCsv = "id,total_reads,reads_with_value,consistent_reads,total_writes,consistency_score,avg_read_latency_ms,avg_write_latency_ms\n"
                + UUID.randomUUID() + ",500,480,450,100,0.90,0.1,0.2\n";
        Files.writeString(dir.resolve("consistency_experiment.csv"), consistencyCsv, StandardCharsets.UTF_8);

        Files.writeString(dir.resolve("churn_experiment.csv"), consistencyCsv, StandardCharsets.UTF_8);
    }
}
