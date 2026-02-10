## Experiment Harness Overview

This project includes an experimental test harness that can spin up multiple Kademlia nodes
in a single JVM and drive workloads against them to approximate the evaluation described
in the final report (latency, throughput, consistency, churn resilience).

The harness uses the existing `Server` implementation and treats each instance as a logical
node, bound to `127.0.0.1` on a unique UDP port.

### Current components

- **`TestCluster`** (`src/test/java/com/kademlia/dht/TestCluster.java`):
  - Starts `N` `Server` instances on localhost.
  - Optionally bootstraps all non-seed nodes against the first node.
  - Intended for integration tests and experiments.

- **`ThroughputExperimentTest`** (`src/test/java/com/kademlia/dht/ThroughputExperimentTest.java`):
  - Experimental JUnit test (tagged `@Tag("experiment")`, `@Disabled` by default).
  - Spins up a `TestCluster`, preloads keys, and runs a read-heavy workload.
  - Records basic metrics and writes a CSV file under `build/experiments/`.

- **`ConsistencyAndChurnExperimentTest`** (`src/test/java/com/kademlia/dht/ConsistencyAndChurnExperimentTest.java`):
  - Experimental JUnit test class (tagged `@Tag("experiment")`, `@Disabled` by default).
  - Contains two skeleton experiments:
    - `consistencyScoreExperiment`: concurrent reads/writes over a small key set to estimate a consistency score.
    - `churnResilienceExperiment`: similar workload while periodically shutting down nodes to simulate churn.
  - Writes CSV summaries under `build/experiments/` (e.g., `consistency_experiment.csv`, `churn_experiment.csv`).

- **`ExperimentOutputTest`** (`src/test/java/com/kademlia/dht/ExperimentOutputTest.java`):
  - Writes sample CSV files to `build/experiments/` (throughput, consistency, churn) so the output format and directory exist for showcase. Runs with the normal test suite.

- **In-process option**: `TestCluster.createSimulated(size, basePort, ksize, alpha)` builds a cluster without UDP so experiments can run in environments where UDP is blocked. The throughput/consistency/churn experiment tests are written to use this when enabled; they are disabled by default.

> Note: Full multi-node experiment tests use either UDP or the in-process `SimulatedTransport`. Some environments (CI, VPNs,
> firewalls, or restricted localhost networking) may drop or block packets, which will
> cause bootstrap and RPC calls to time out. For that reason, the heavy experiments are
> **disabled by default** and should be run manually on a suitable local machine.

### Running the throughput experiment

1. **Enable the test (optional)**

   By default, `ThroughputExperimentTest` is annotated with `@Disabled` to prevent it from
   running as part of the standard `./gradlew test` cycle.

   To enable it temporarily, you can:

   - Comment out or remove the `@Disabled` annotation, **or**
   - Create a local branch where you adjust the annotation just for experimentation.

2. **Run the test**

   From the project root:

   ```bash
   ./gradlew test --tests "com.kademlia.dht.ThroughputExperimentTest"
   ```

   If UDP networking is functional, this will:

   - Start a small cluster of nodes on `127.0.0.1:20000+`.
   - Preload a fixed set of keys.
   - Run a warmup phase, then a timed read-heavy workload.
   - Write a CSV file under `build/experiments/`.

3. **Inspecting the results**

   After a successful run, you should see a file similar to:

   - `build/experiments/throughput_read_1min.csv`

   The CSV has the following columns:

   - `id`: Random run identifier (UUID).
   - `total_ops`: Total number of read operations attempted during the measured phase.
   - `successes`: Number of successful reads (completed without exception).
   - `failures`: Number of failed reads (timeouts, exceptions, etc.).
   - `total_latency_ns`: Sum of per-operation latencies in nanoseconds.
   - `throughput_ops_per_sec`: Approximate measured throughput.
   - `avg_latency_ms`: Average latency per operation in milliseconds.

### Running the consistency and churn experiments

1. **Enable the tests (optional)**

   `ConsistencyAndChurnExperimentTest` is also annotated with `@Disabled` by default. To run it:

   - Temporarily remove or comment out `@Disabled`, or
   - Adjust only on a local branch created for experimentation.

2. **Run the tests**

   ```bash
   ./gradlew test --tests "com.kademlia.dht.ConsistencyAndChurnExperimentTest"
   ```

   If UDP networking is functional, this will:

   - Start one or more clusters on `127.0.0.1` (at ports starting from 21000).
   - Execute a consistency workload (writer + readers).
   - Execute a basic churn workload (writer + readers + periodic node shutdown).
   - Write CSV files such as:
     - `build/experiments/consistency_experiment.csv`
     - `build/experiments/churn_experiment.csv`

3. **CSV column semantics**

   For both consistency and churn experiments, the CSV header is:

   - `id`: Random run identifier (UUID).
   - `total_reads`: Total read attempts.
   - `reads_with_value`: Reads that returned any value.
   - `consistent_reads`: Reads that returned the most recent version of the key.
   - `total_writes`: Total successful writes.
   - `consistency_score`: Approximate consistency score (`consistent_reads / total_reads`).
   - `avg_read_latency_ms`: Average read latency.
   - `avg_write_latency_ms`: Average write latency.

   You can use these metrics to build plots analogous to the consistency and churn figures
   in the report (e.g., consistency over time, resilience under increasing churn).

   You can copy this CSV into a plotting tool (Python, R, Excel, etc.) and generate
   figures analogous to the throughput and latency plots in the report.

### Extending the harness

To more closely match the experiments described in the report, you can:

- Add additional experiment classes under `src/test/java/com/kademlia/dht/` (or a dedicated
  `experiments` subpackage) to:
  - Vary the number of nodes.
  - Adjust read/write ratios.
  - Introduce churn by closing and restarting nodes on different ports.
  - Compute consistency scores over time (fraction of reads that return the most recent write).
- Log per-interval metrics (e.g., per-second throughput and latency) instead of just totals
  to produce time-series plots like those in the paper.

All experiment code should:

- Remain in the `src/test/java` tree.
- Be tagged (e.g., `@Tag("experiment")`) and optionally `@Disabled` to avoid slowing down
  normal unit test runs.

