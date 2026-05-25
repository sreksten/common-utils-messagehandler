package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordFactoryImpl;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ExportLogsServiceRequestLogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordDispatcher;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Load-profile evidence report integration test")
@Tag("integration")
@Tag("messageHandler")
class LoadProfileEvidenceReportIntegrationTest {

    private static final LogRecordFactory FACTORY = new LogRecordFactoryImpl();
    private static final LogRecordFormatter FORMATTER = new ExportLogsServiceRequestLogRecordFormatter();

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    @DisplayName("should run sustained, burst, outage-recovery profiles and write markdown report")
    void shouldRunLoadProfilesAndWriteMarkdownReport() throws Exception {
        ScenarioResult sustained = runScenario(
                new ScenarioSpec("sustained", 6, 3_000L, 0L, 2L, 256,
                        AbstractOutputMessageHandler.QueueOverflowPolicy.DROP_OLDEST)
        );
        ScenarioResult burst = runScenario(
                new ScenarioSpec("burst", 24, 1_500L, 0L, 3L, 256,
                        AbstractOutputMessageHandler.QueueOverflowPolicy.DROP_OLDEST)
        );
        ScenarioResult outageRecovery = runScenario(
                new ScenarioSpec("outage-recovery", 10, 2_500L, 1_000L, 2L, 256,
                        AbstractOutputMessageHandler.QueueOverflowPolicy.DROP_OLDEST)
        );

        Path reportPath = resolveReportPath();
        writeReport(reportPath, sustained, burst, outageRecovery);

        String report = new String(Files.readAllBytes(reportPath), StandardCharsets.UTF_8);
        assertTrue(Files.exists(reportPath), "Load-profile report file should exist");
        assertFalse(report.trim().isEmpty(), "Load-profile report should not be empty");
        assertTrue(report.contains("| Scenario |"), "Load-profile report should contain the results table");
        assertTrue(report.contains("sustained"), "Report should include sustained scenario");
        assertTrue(report.contains("burst"), "Report should include burst scenario");
        assertTrue(report.contains("outage-recovery"), "Report should include outage-recovery scenario");
    }

    private static Path resolveReportPath() {
        String path = System.getProperty("messagehandler.load.report.path", "target/load-profile-report.md");
        return Paths.get(path);
    }

    private static ScenarioResult runScenario(final ScenarioSpec scenarioSpec) throws Exception {
        SyntheticLoadDispatcher dispatcher = new SyntheticLoadDispatcher();
        dispatcher.setDispatchLatencyMillis(scenarioSpec.dispatchLatencyMillis);
        dispatcher.setFailing(scenarioSpec.outageMillis > 0L);

        JaegerMessageHandler handler = new JaegerMessageHandler(
                FACTORY,
                FORMATTER,
                dispatcher,
                true,
                scenarioSpec.queueCapacity,
                false
        );
        handler.setErrorConsumer(message -> {
            // keep load-profile test output clean
        });
        handler.setQueueOverflowPolicy(scenarioSpec.queueOverflowPolicy);
        handler.setRateLimitPolicy(200_000L, 200_000L);
        handler.setSeveritySamplingPolicy(1.0d, 1.0d, 1.0d, 1.0d, 1.0d, 1.0d);
        handler.setHttpRetryPolicy(2, 1L, 8L);
        handler.setHttpAdaptiveRetryBudgetPolicy(300, 1, 500L, 0.0d);
        handler.setHttpCircuitBreakerPolicy(100_000, 1_000L, 1);

        ExecutorService producerPool = Executors.newFixedThreadPool(scenarioSpec.producerThreads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<Future<?>>();
        AtomicLong attemptedMessages = new AtomicLong(0L);
        LatencyStats latencyStats = new LatencyStats();
        AtomicLong startNanos = new AtomicLong(0L);
        final long scenarioDurationNanos = TimeUnit.MILLISECONDS.toNanos(scenarioSpec.durationMillis);
        final long outageNanos = TimeUnit.MILLISECONDS.toNanos(scenarioSpec.outageMillis);

        for (int threadIndex = 0; threadIndex < scenarioSpec.producerThreads; threadIndex++) {
            final int workerId = threadIndex;
            futures.add(producerPool.submit(() -> {
                start.await();
                long localCounter = 0L;
                while (true) {
                    long nowNanos = System.nanoTime();
                    long elapsedNanos = nowNanos - startNanos.get();
                    if (elapsedNanos >= scenarioDurationNanos) {
                        break;
                    }
                    if (outageNanos > 0L && elapsedNanos >= outageNanos) {
                        dispatcher.setFailing(false);
                    }
                    long beginNanos = System.nanoTime();
                    handler.info("load-profile-" + scenarioSpec.name + "-" + workerId + "-" + localCounter);
                    long elapsedCallNanos = System.nanoTime() - beginNanos;
                    latencyStats.record(elapsedCallNanos);
                    attemptedMessages.incrementAndGet();
                    localCounter++;
                }
                return null;
            }));
        }

        long produceStartNanos = System.nanoTime();
        startNanos.set(produceStartNanos);
        start.countDown();

        for (Future<?> future : futures) {
            future.get(scenarioSpec.durationMillis + 30_000L, TimeUnit.MILLISECONDS);
        }
        producerPool.shutdown();
        assertTrue(producerPool.awaitTermination(30, TimeUnit.SECONDS),
                "Producer pool should terminate for scenario " + scenarioSpec.name);
        long produceElapsedNanos = System.nanoTime() - produceStartNanos;

        long drainStartNanos = System.nanoTime();
        long drainDeadlineNanos = drainStartNanos + TimeUnit.SECONDS.toNanos(20L);
        AbstractOutputMessageHandler.HandlerHealthMetrics metrics;
        do {
            metrics = handler.getHandlerHealthMetrics();
            if (metrics.getPendingQueueSize() == 0) {
                break;
            }
            Thread.sleep(20L);
        } while (System.nanoTime() < drainDeadlineNanos);
        long drainElapsedNanos = System.nanoTime() - drainStartNanos;
        boolean drained = metrics.getPendingQueueSize() == 0;

        handler.close();

        return new ScenarioResult(
                scenarioSpec,
                attemptedMessages.get(),
                produceElapsedNanos,
                drainElapsedNanos,
                drained,
                latencyStats,
                metrics,
                dispatcher.getDispatchCalls(),
                dispatcher.getFailedDispatchCalls(),
                dispatcher.getDispatchedRecords()
        );
    }

    private static void writeReport(final Path reportPath,
                                    final ScenarioResult sustained,
                                    final ScenarioResult burst,
                                    final ScenarioResult outageRecovery) throws IOException {
        Path parent = reportPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Load Profile Evidence Report\n\n");
        sb.append("- Generated at: `").append(Instant.now().toString()).append("`\n");
        sb.append("- Java: `").append(System.getProperty("java.version")).append("`\n");
        sb.append("- OS: `").append(System.getProperty("os.name")).append(" ")
                .append(System.getProperty("os.version")).append("`\n");
        sb.append("- Handler under test: `JaegerMessageHandler` with synthetic dispatcher\n");
        sb.append("- Report path: `").append(reportPath.toString()).append("`\n\n");

        sb.append("## Test Profile\n\n");
        sb.append("- Queue overflow policy: `DROP_OLDEST`\n");
        sb.append("- Queue capacity: `256`\n");
        sb.append("- Rate limiting: `200000 permits/s`, burst `200000`\n");
        sb.append("- Sampling: all severities set to `1.0`\n");
        sb.append("- Retry policy: max retries `2`, backoff `1..8 ms`\n");
        sb.append("- Circuit breaker threshold: `100000` failures (effectively disabled for this profile)\n\n");

        sb.append("## Scenario Results\n\n");
        sb.append("| Scenario | Threads | Duration ms | Attempted | Attempted msg/s | Avg caller latency ms | Max caller latency ms | Queue drained | Drain ms | Dispatch attempts | Saturation events | Drop oldest | Drop newest | Block timeout drops | Rate limited | Sampled out | Retry attempts | Retry successes | Retry failures | Dispatcher calls | Dispatcher failed calls | Dispatched records |\n");
        sb.append("|---|---:|---:|---:|---:|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        appendScenarioRow(sb, sustained);
        appendScenarioRow(sb, burst);
        appendScenarioRow(sb, outageRecovery);

        sb.append("\n## Notes\n\n");
        sb.append("- Numbers above are measured by the test run itself and can vary by machine and CI load.\n");
        sb.append("- `Attempted` means caller-side `handler.info(...)` invocations during the producer window.\n");
        sb.append("- Queue/drain and retry counters are pulled from `getHandlerHealthMetrics()`.\n");
        sb.append("- Dispatcher counters are from the synthetic backend used in this test.\n");

        Files.write(reportPath, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void appendScenarioRow(final StringBuilder sb, final ScenarioResult scenarioResult) {
        AbstractOutputMessageHandler.HandlerHealthMetrics metrics = scenarioResult.metrics;
        sb.append("| ").append(scenarioResult.spec.name)
                .append(" | ").append(scenarioResult.spec.producerThreads)
                .append(" | ").append(scenarioResult.spec.durationMillis)
                .append(" | ").append(scenarioResult.attemptedMessages)
                .append(" | ").append(formatDouble(messagesPerSecond(scenarioResult.attemptedMessages, scenarioResult.produceElapsedNanos)))
                .append(" | ").append(formatDouble(scenarioResult.latencyStats.averageMillis()))
                .append(" | ").append(formatDouble(scenarioResult.latencyStats.maxMillis()))
                .append(" | ").append(scenarioResult.drained ? "yes" : "no")
                .append(" | ").append(TimeUnit.NANOSECONDS.toMillis(scenarioResult.drainElapsedNanos))
                .append(" | ").append(metrics.getDispatchAttempts())
                .append(" | ").append(metrics.getQueueSaturationEvents())
                .append(" | ").append(metrics.getQueueOverflowDropOldestOperations())
                .append(" | ").append(metrics.getQueueOverflowDropNewestOperations())
                .append(" | ").append(metrics.getQueueOverflowBlockTimeoutOperations())
                .append(" | ").append(metrics.getRateLimitedOperations())
                .append(" | ").append(metrics.getSampledOutOperations())
                .append(" | ").append(metrics.getRetryAttempts())
                .append(" | ").append(metrics.getRetrySuccesses())
                .append(" | ").append(metrics.getRetryFailures())
                .append(" | ").append(scenarioResult.dispatcherCalls)
                .append(" | ").append(scenarioResult.dispatcherFailedCalls)
                .append(" | ").append(scenarioResult.dispatchedRecords)
                .append(" |\n");
    }

    private static String formatDouble(final double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static double messagesPerSecond(final long attemptedMessages, final long elapsedNanos) {
        if (elapsedNanos <= 0L) {
            return 0.0d;
        }
        return (double) attemptedMessages * 1_000_000_000.0d / (double) elapsedNanos;
    }

    private static final class ScenarioSpec {
        private final String name;
        private final int producerThreads;
        private final long durationMillis;
        private final long outageMillis;
        private final long dispatchLatencyMillis;
        private final int queueCapacity;
        private final AbstractOutputMessageHandler.QueueOverflowPolicy queueOverflowPolicy;

        private ScenarioSpec(final String name,
                             final int producerThreads,
                             final long durationMillis,
                             final long outageMillis,
                             final long dispatchLatencyMillis,
                             final int queueCapacity,
                             final AbstractOutputMessageHandler.QueueOverflowPolicy queueOverflowPolicy) {
            this.name = name;
            this.producerThreads = producerThreads;
            this.durationMillis = durationMillis;
            this.outageMillis = outageMillis;
            this.dispatchLatencyMillis = dispatchLatencyMillis;
            this.queueCapacity = queueCapacity;
            this.queueOverflowPolicy = queueOverflowPolicy;
        }
    }

    private static final class LatencyStats {
        private final AtomicLong count = new AtomicLong(0L);
        private final AtomicLong totalNanos = new AtomicLong(0L);
        private final AtomicLong maxNanos = new AtomicLong(0L);

        private void record(final long elapsedNanos) {
            count.incrementAndGet();
            totalNanos.addAndGet(elapsedNanos);
            long currentMax;
            do {
                currentMax = maxNanos.get();
                if (elapsedNanos <= currentMax) {
                    return;
                }
            } while (!maxNanos.compareAndSet(currentMax, elapsedNanos));
        }

        private double averageMillis() {
            long observations = count.get();
            if (observations == 0L) {
                return 0.0d;
            }
            return (double) totalNanos.get() / (double) observations / 1_000_000.0d;
        }

        private double maxMillis() {
            return (double) maxNanos.get() / 1_000_000.0d;
        }
    }

    private static final class ScenarioResult {
        private final ScenarioSpec spec;
        private final long attemptedMessages;
        private final long produceElapsedNanos;
        private final long drainElapsedNanos;
        private final boolean drained;
        private final LatencyStats latencyStats;
        private final AbstractOutputMessageHandler.HandlerHealthMetrics metrics;
        private final long dispatcherCalls;
        private final long dispatcherFailedCalls;
        private final long dispatchedRecords;

        private ScenarioResult(final ScenarioSpec spec,
                               final long attemptedMessages,
                               final long produceElapsedNanos,
                               final long drainElapsedNanos,
                               final boolean drained,
                               final LatencyStats latencyStats,
                               final AbstractOutputMessageHandler.HandlerHealthMetrics metrics,
                               final long dispatcherCalls,
                               final long dispatcherFailedCalls,
                               final long dispatchedRecords) {
            this.spec = spec;
            this.attemptedMessages = attemptedMessages;
            this.produceElapsedNanos = produceElapsedNanos;
            this.drainElapsedNanos = drainElapsedNanos;
            this.drained = drained;
            this.latencyStats = latencyStats;
            this.metrics = metrics;
            this.dispatcherCalls = dispatcherCalls;
            this.dispatcherFailedCalls = dispatcherFailedCalls;
            this.dispatchedRecords = dispatchedRecords;
        }
    }

    private static final class SyntheticLoadDispatcher implements LogRecordDispatcher {
        private final AtomicLong dispatchCalls = new AtomicLong(0L);
        private final AtomicLong failedDispatchCalls = new AtomicLong(0L);
        private final AtomicLong dispatchedRecords = new AtomicLong(0L);
        private volatile boolean failing = false;
        private volatile long dispatchLatencyMillis = 0L;

        private void setFailing(final boolean failing) {
            this.failing = failing;
        }

        private void setDispatchLatencyMillis(final long dispatchLatencyMillis) {
            this.dispatchLatencyMillis = Math.max(0L, dispatchLatencyMillis);
        }

        private long getDispatchCalls() {
            return dispatchCalls.get();
        }

        private long getFailedDispatchCalls() {
            return failedDispatchCalls.get();
        }

        private long getDispatchedRecords() {
            return dispatchedRecords.get();
        }

        @Override
        public void dispatchLogRecord(@Nonnull final LogRecord logRecord,
                                      @Nonnull final LogRecordFormatter logRecordFormatter) throws IOException {
            dispatchLogRecords(java.util.Collections.singletonList(logRecord), logRecordFormatter);
        }

        @Override
        public void dispatchLogRecords(@Nonnull final List<LogRecord> logRecords,
                                       @Nonnull final LogRecordFormatter logRecordFormatter) throws IOException {
            dispatchCalls.incrementAndGet();
            long latency = dispatchLatencyMillis;
            if (latency > 0L) {
                try {
                    Thread.sleep(latency);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Synthetic dispatcher interrupted during latency delay", interruptedException);
                }
            }
            if (failing) {
                failedDispatchCalls.incrementAndGet();
                throw new IOException("Synthetic dispatcher failure window is active");
            }
            if (logRecords != null) {
                dispatchedRecords.addAndGet(logRecords.size());
            }
        }
    }
}
