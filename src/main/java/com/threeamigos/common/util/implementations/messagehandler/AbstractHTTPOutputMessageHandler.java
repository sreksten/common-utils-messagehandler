package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.utils.HttpDispatchStatusException;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Base class for HTTP-oriented output handlers (for example Jaeger/Grafana log exporters).
 * <p>
 * This class extends {@link AbstractOutputMessageHandler} with transport concerns shared by HTTP handlers:
 * <ul>
 *   <li>bounded retry with exponential backoff</li>
 *   <li>adaptive retry budget and jitter tied to observed dispatch throughput</li>
 *   <li>retryability classification for I/O and HTTP status failures</li>
 *   <li>circuit breaker with closed/open/half-open transitions</li>
 *   <li>best-effort JVM keep-alive configuration for {@link java.net.HttpURLConnection}</li>
 *   <li>failure buffering so records that fail dispatch can be retried as a later batch</li>
 *   <li>non-throwing dispatch path: transport/runtime failures are reported to the configured
 *       error consumer instead of being propagated to the logging caller</li>
 * </ul>
 * <p>
 * Retry batches are built by draining previously failed records from an internal buffer and appending
 * the current record. Subclasses provide the concrete transport call through
 * {@link HttpDispatchOperation}.
 *
 * @author Stefano Reksten
 */
public abstract class AbstractHTTPOutputMessageHandler extends AbstractOutputMessageHandler {

    private static final int DEFAULT_HTTP_MAX_CONNECTIONS = 32;
    private static final int DEFAULT_MAX_RETRIES = 1;
    private static final long DEFAULT_INITIAL_RETRY_BACKOFF_MILLIS = 50L;
    private static final long DEFAULT_MAX_RETRY_BACKOFF_MILLIS = 500L;
    private static final int DEFAULT_CIRCUIT_BREAKER_FAILURE_THRESHOLD = 5;
    private static final long DEFAULT_CIRCUIT_BREAKER_OPEN_STATE_MILLIS = 3_000L;
    private static final int DEFAULT_CIRCUIT_BREAKER_HALF_OPEN_MAX_CALLS = 1;
    private static final int DEFAULT_RETRY_BUDGET_PERCENT = 200;
    private static final int DEFAULT_RETRY_BUDGET_MIN_RETRIES_PER_WINDOW = 1;
    private static final long DEFAULT_RETRY_BUDGET_WINDOW_MILLIS = 1_000L;
    private static final double DEFAULT_RETRY_JITTER_FACTOR = 0.20d;

    private static final String HTTP_KEEP_ALIVE_PROPERTY = "http.keepAlive";
    private static final String HTTP_MAX_CONNECTIONS_PROPERTY = "http.maxConnections";
    private static final Object HTTP_TRANSPORT_CONFIGURATION_LOCK = new Object();
    private final Object circuitBreakerLock = new Object();
    private final Object retryBudgetLock = new Object();

    private volatile int maxRetries = DEFAULT_MAX_RETRIES;
    private volatile long initialRetryBackoffMillis = DEFAULT_INITIAL_RETRY_BACKOFF_MILLIS;
    private volatile long maxRetryBackoffMillis = DEFAULT_MAX_RETRY_BACKOFF_MILLIS;
    private volatile int circuitBreakerFailureThreshold = DEFAULT_CIRCUIT_BREAKER_FAILURE_THRESHOLD;
    private volatile long circuitBreakerOpenStateMillis = DEFAULT_CIRCUIT_BREAKER_OPEN_STATE_MILLIS;
    private volatile int circuitBreakerHalfOpenMaxCalls = DEFAULT_CIRCUIT_BREAKER_HALF_OPEN_MAX_CALLS;
    private volatile int retryBudgetPercent = DEFAULT_RETRY_BUDGET_PERCENT;
    private volatile int retryBudgetMinRetriesPerWindow = DEFAULT_RETRY_BUDGET_MIN_RETRIES_PER_WINDOW;
    private volatile long retryBudgetWindowMillis = DEFAULT_RETRY_BUDGET_WINDOW_MILLIS;
    private volatile double retryJitterFactor = DEFAULT_RETRY_JITTER_FACTOR;
    private CircuitState circuitState = CircuitState.CLOSED;
    private int consecutiveCircuitBreakerFailures = 0;
    private long circuitOpenedAtMillis = 0L;
    private int halfOpenActiveCalls = 0;
    private long retryBudgetWindowStartMillis = System.currentTimeMillis();
    private int retryBudgetWindowPrimaryAttempts = 0;
    private int retryBudgetWindowConsumedRetries = 0;
    private final Queue<LogRecord> retryBuffer = new ConcurrentLinkedQueue<LogRecord>();

    private enum CircuitState {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private static final class CircuitBreakerOpenException extends IOException {
        private static final long serialVersionUID = 1L;

        private CircuitBreakerOpenException(final String message) {
            super(message);
        }
    }

    /**
     * Strategy callback used by {@link #dispatchHttpRecord(LogRecord, HttpDispatchOperation, Consumer, String, boolean, String)}
     * to execute one HTTP dispatch operation on one or more log records.
     */
    @FunctionalInterface
    protected interface HttpDispatchOperation {
        void dispatch(final @Nonnull List<LogRecord> logRecords,
                      final @Nonnull LogRecordFormatter logRecordFormatter) throws IOException;
    }

    /**
     * Creates an HTTP output handler with default retry and transport settings.
     * <p>
     * Defaults:
     * <ul>
     *   <li>max retries: 1</li>
     *   <li>initial backoff: 50 ms</li>
     *   <li>max backoff: 500 ms</li>
     *   <li>retry budget: 200% of primary throughput (minimum 1 retry token per second window)</li>
     *   <li>retry jitter factor: ±20%</li>
     *   <li>circuit breaker: opens after 5 consecutive failures, 3-second open window, 1 half-open probe</li>
     *   <li>HTTP keep-alive max-connections hint: 32</li>
     * </ul>
     *
     * @param logRecordFactory record factory used by the superclass
     * @param logRecordFormatter formatter used by the superclass
     */
    public AbstractHTTPOutputMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                                            final @Nonnull LogRecordFormatter logRecordFormatter) {
        super(logRecordFactory, logRecordFormatter);
        configureHttpConnectionPooling(DEFAULT_HTTP_MAX_CONNECTIONS);
    }

    /**
     * Configures retry behavior for HTTP dispatch failures.
     *
     * @param maxRetries number of retries after the first failed attempt (0 disables retries)
     * @param initialBackoffMillis delay before first retry in milliseconds
     * @param maxBackoffMillis maximum delay cap for exponential backoff in milliseconds
     */
    public final void setHttpRetryPolicy(final int maxRetries,
                                         final long initialBackoffMillis,
                                         final long maxBackoffMillis) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
        if (initialBackoffMillis < 0L) {
            throw new IllegalArgumentException("initialBackoffMillis must be >= 0");
        }
        if (maxBackoffMillis < 0L) {
            throw new IllegalArgumentException("maxBackoffMillis must be >= 0");
        }
        if (maxBackoffMillis > 0L && maxBackoffMillis < initialBackoffMillis) {
            throw new IllegalArgumentException("maxBackoffMillis must be >= initialBackoffMillis when maxBackoffMillis > 0");
        }
        this.maxRetries = maxRetries;
        this.initialRetryBackoffMillis = initialBackoffMillis;
        this.maxRetryBackoffMillis = maxBackoffMillis;
    }

    /**
     * Configures the HTTP transport circuit breaker.
     * <p>
     * The breaker transitions:
     * <ul>
     *   <li><strong>closed</strong> → <strong>open</strong> after {@code failureThreshold} consecutive dispatch failures</li>
     *   <li><strong>open</strong> → <strong>half-open</strong> after {@code openStateMillis} has elapsed</li>
     *   <li><strong>half-open</strong> → <strong>closed</strong> on first successful probe, or back to
     *       <strong>open</strong> on probe failure</li>
     * </ul>
     *
     * @param failureThreshold consecutive failures required to open the breaker (must be > 0)
     * @param openStateMillis time the breaker remains open before allowing half-open probes (must be > 0)
     * @param halfOpenMaxCalls max concurrent probe calls allowed in half-open state (must be > 0)
     */
    public final void setHttpCircuitBreakerPolicy(final int failureThreshold,
                                                  final long openStateMillis,
                                                  final int halfOpenMaxCalls) {
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be > 0");
        }
        if (openStateMillis <= 0L) {
            throw new IllegalArgumentException("openStateMillis must be > 0");
        }
        if (halfOpenMaxCalls <= 0) {
            throw new IllegalArgumentException("halfOpenMaxCalls must be > 0");
        }
        synchronized (circuitBreakerLock) {
            this.circuitBreakerFailureThreshold = failureThreshold;
            this.circuitBreakerOpenStateMillis = openStateMillis;
            this.circuitBreakerHalfOpenMaxCalls = halfOpenMaxCalls;
            this.circuitState = CircuitState.CLOSED;
            this.consecutiveCircuitBreakerFailures = 0;
            this.circuitOpenedAtMillis = 0L;
            this.halfOpenActiveCalls = 0;
        }
    }

    /**
     * Configures adaptive retry-budget and jitter policy.
     * <p>
     * Retry tokens are budgeted over a rolling window. Each new primary dispatch attempt contributes
     * budget according to {@code retryBudgetPercent}; each retry consumes one token. This ties retry
     * pressure to observed throughput and avoids unlimited retry storms during sustained outages.
     *
     * @param retryBudgetPercent budget ratio as percentage of primary throughput (must be >= 0)
     * @param minRetriesPerWindow minimum retry tokens guaranteed per window (must be >= 0)
     * @param budgetWindowMillis rolling window size in milliseconds (must be > 0)
     * @param jitterFactor backoff jitter amplitude in range [0.0, 1.0]
     */
    public final void setHttpAdaptiveRetryBudgetPolicy(final int retryBudgetPercent,
                                                       final int minRetriesPerWindow,
                                                       final long budgetWindowMillis,
                                                       final double jitterFactor) {
        if (retryBudgetPercent < 0) {
            throw new IllegalArgumentException("retryBudgetPercent must be >= 0");
        }
        if (minRetriesPerWindow < 0) {
            throw new IllegalArgumentException("minRetriesPerWindow must be >= 0");
        }
        if (budgetWindowMillis <= 0L) {
            throw new IllegalArgumentException("budgetWindowMillis must be > 0");
        }
        if (Double.isNaN(jitterFactor) || jitterFactor < 0.0d || jitterFactor > 1.0d) {
            throw new IllegalArgumentException("jitterFactor must be between 0.0 and 1.0");
        }
        synchronized (retryBudgetLock) {
            this.retryBudgetPercent = retryBudgetPercent;
            this.retryBudgetMinRetriesPerWindow = minRetriesPerWindow;
            this.retryBudgetWindowMillis = budgetWindowMillis;
            this.retryJitterFactor = jitterFactor;
            this.retryBudgetWindowStartMillis = System.currentTimeMillis();
            this.retryBudgetWindowPrimaryAttempts = 0;
            this.retryBudgetWindowConsumedRetries = 0;
        }
    }

    /**
     * Sets the JVM-wide max connections hint used by {@link java.net.HttpURLConnection} keep-alive cache.
     * <p>
     * This updates global system properties, so it affects every handler using HttpURLConnection in the JVM.
     *
     * @param maxConnections desired keep-alive max connections
     */
    public final void setHttpConnectionPoolSize(final int maxConnections) {
        configureHttpConnectionPooling(maxConnections);
    }

    /**
     * Dispatches one logical log record through the provided HTTP dispatch operation.
     * <p>
     * Behavior:
     * <ol>
     *   <li>Builds a dispatch batch by prepending buffered failed records to the current record.</li>
     *   <li>Executes dispatch through {@link #dispatch(Runnable)} (sync or async, depending on handler mode).</li>
     *   <li>Applies retry/backoff policy for retryable {@link IOException}s.</li>
     *   <li>On failure, records metrics, re-buffers batch records, reports to {@code errorConsumer},
     *       and optionally requests close-on-error.</li>
     * </ol>
     * <p>
     * This method is intentionally non-throwing for dispatch-related failures; it preserves caller flow.
     *
     * @param logRecord current log record being emitted
     * @param dispatchOperation transport callback to execute dispatch
     * @param errorConsumer consumer used to receive localized failure messages
     * @param dispatchErrorMessageKey resource-bundle key for dispatch error message formatting
     * @param closeOnDispatchError whether close-on-error behavior is enabled
     * @param closeThreadName thread name used if async close scheduling is required
     */
    protected final void dispatchHttpRecord(final @Nonnull LogRecord logRecord,
                                            final @Nonnull HttpDispatchOperation dispatchOperation,
                                            final @Nonnull Consumer<String> errorConsumer,
                                            final @Nonnull String dispatchErrorMessageKey,
                                            final boolean closeOnDispatchError,
                                            final @Nonnull String closeThreadName) {
        Objects.requireNonNull(logRecord, MessageHandlerResourceBundle.get("logRecordMustNotBeNull"));
        Objects.requireNonNull(dispatchOperation, "dispatchOperation must not be null");
        Objects.requireNonNull(errorConsumer, MessageHandlerResourceBundle.get("nullErrorConsumerProvided"));
        Objects.requireNonNull(dispatchErrorMessageKey, "dispatchErrorMessageKey must not be null");
        Objects.requireNonNull(closeThreadName, "closeThreadName must not be null");
        final List<LogRecord> recordsToDispatch = buildDispatchBatch(logRecord);

        try {
            dispatch(() -> {
                try {
                    executeHttpDispatchWithRetry(recordsToDispatch, dispatchOperation);
                    recordOutputSuccess();
                } catch (IOException dispatchException) {
                    recordOutputFailure();
                    enqueueRetryCandidates(recordsToDispatch);
                    reportDispatchFailure(dispatchException, errorConsumer, dispatchErrorMessageKey,
                            closeOnDispatchError, closeThreadName);
                } catch (RuntimeException runtimeException) {
                    recordOutputFailure();
                    enqueueRetryCandidates(recordsToDispatch);
                    reportDispatchFailure(runtimeException, errorConsumer, dispatchErrorMessageKey,
                            closeOnDispatchError, closeThreadName);
                }
            });
        } catch (IllegalStateException closedHandlerException) {
            // dispatch(...) already increments droppedOutputOperations when closed.
            reportDispatchFailure(closedHandlerException, errorConsumer, dispatchErrorMessageKey,
                    closeOnDispatchError, closeThreadName);
        } catch (RuntimeException runtimeException) {
            recordOutputFailure();
            enqueueRetryCandidates(recordsToDispatch);
            reportDispatchFailure(runtimeException, errorConsumer, dispatchErrorMessageKey,
                    closeOnDispatchError, closeThreadName);
        }
    }

    /**
     * Records one failed output operation and reports the provided failure through the configured
     * error handling path.
     * <p>
     * Intended for failures that happen before HTTP dispatch starts (for example record creation/formatting).
     *
     * @param failure failure to report
     * @param errorConsumer consumer receiving localized failure details
     * @param dispatchErrorMessageKey resource-bundle key for message formatting
     * @param closeOnDispatchError whether close-on-error behavior is enabled
     * @param closeThreadName thread name used if async close scheduling is required
     */
    protected final void recordAndReportHttpMessageFailure(final @Nonnull Throwable failure,
                                                           final @Nonnull Consumer<String> errorConsumer,
                                                           final @Nonnull String dispatchErrorMessageKey,
                                                           final boolean closeOnDispatchError,
                                                           final @Nonnull String closeThreadName) {
        Objects.requireNonNull(failure, "failure must not be null");
        Objects.requireNonNull(errorConsumer, MessageHandlerResourceBundle.get("nullErrorConsumerProvided"));
        Objects.requireNonNull(dispatchErrorMessageKey, "dispatchErrorMessageKey must not be null");
        Objects.requireNonNull(closeThreadName, "closeThreadName must not be null");
        recordOutputFailure();
        reportDispatchFailure(failure, errorConsumer, dispatchErrorMessageKey, closeOnDispatchError, closeThreadName);
    }

    /**
     * Executes one dispatch batch with bounded retries and exponential backoff.
     *
     * @param logRecords batch to dispatch
     * @param dispatchOperation transport callback
     * @throws IOException when dispatch fails with a non-retryable error or retries are exhausted
     */
    private void executeHttpDispatchWithRetry(final List<LogRecord> logRecords,
                                              final HttpDispatchOperation dispatchOperation) throws IOException {
        int configuredMaxRetries = this.maxRetries;
        long configuredInitialBackoffMillis = this.initialRetryBackoffMillis;
        long configuredMaxBackoffMillis = this.maxRetryBackoffMillis;
        double configuredJitterFactor = this.retryJitterFactor;

        registerPrimaryDispatchAttemptForRetryBudget();

        int retriesUsed = 0;
        while (true) {
            try {
                awaitCircuitBreakerPermission();
                dispatchOperation.dispatch(logRecords, getLogRecordFormatter());
                recordCircuitBreakerSuccess();
                if (retriesUsed > 0) {
                    recordRetrySuccess();
                }
                return;
            } catch (IOException dispatchException) {
                recordCircuitBreakerFailure();
                if (!shouldRetryDispatch(dispatchException, retriesUsed, configuredMaxRetries)) {
                    if (retriesUsed > 0) {
                        recordRetryFailure();
                    }
                    throw dispatchException;
                }
                if (!tryConsumeRetryBudgetToken()) {
                    if (retriesUsed > 0) {
                        recordRetryFailure();
                    }
                    throw dispatchException;
                }
                retriesUsed++;
                recordRetryAttempt();
                try {
                    backoffBeforeRetry(retriesUsed, configuredInitialBackoffMillis, configuredMaxBackoffMillis,
                            configuredJitterFactor);
                } catch (IOException interruptedBackoffException) {
                    recordRetryFailure();
                    throw interruptedBackoffException;
                }
            }
        }
    }

    /**
     * Builds the next dispatch batch by draining buffered failed records and appending the current record.
     *
     * @param currentRecord record from the current logging call
     * @return ordered dispatch batch: buffered-failures first, current record last
     */
    private List<LogRecord> buildDispatchBatch(final LogRecord currentRecord) {
        List<LogRecord> batch = new ArrayList<LogRecord>();
        LogRecord pendingRetryRecord;
        while ((pendingRetryRecord = retryBuffer.poll()) != null) {
            batch.add(pendingRetryRecord);
        }
        batch.add(currentRecord);
        return batch;
    }

    /**
     * Re-enqueues records so a future dispatch call can retry them as a batch.
     *
     * @param logRecords records to re-buffer
     */
    private void enqueueRetryCandidates(final List<LogRecord> logRecords) {
        if (logRecords == null || logRecords.isEmpty()) {
            return;
        }
        for (LogRecord logRecord : logRecords) {
            if (logRecord == null) {
                continue;
            }
            retryBuffer.offer(logRecord);
        }
    }

    /**
     * Returns whether a caught dispatch exception should trigger a retry attempt.
     * <p>
     * Retryable HTTP status errors: 408, 429, and 5xx.
     *
     * @param dispatchException caught dispatch exception
     * @param retriesUsed number of retries already consumed
     * @param maxRetries configured retry budget
     * @return {@code true} when retry should be attempted
     */
    private static boolean shouldRetryDispatch(final IOException dispatchException,
                                               final int retriesUsed,
                                               final int maxRetries) {
        if (retriesUsed >= maxRetries) {
            return false;
        }
        if (dispatchException instanceof CircuitBreakerOpenException) {
            return false;
        }
        if (dispatchException instanceof HttpDispatchStatusException) {
            int statusCode = ((HttpDispatchStatusException) dispatchException).getStatusCode();
            return statusCode == 408 || statusCode == 429 || (statusCode >= 500 && statusCode <= 599);
        }
        return true;
    }

    /**
     * Sleeps according to exponential backoff policy before the next retry.
     *
     * @param retryNumber retry ordinal (1-based)
     * @param initialBackoffMillis initial backoff delay
     * @param maxBackoffMillis maximum backoff cap
     * @throws IOException when the waiting thread is interrupted
     */
    private static void backoffBeforeRetry(final int retryNumber,
                                           final long initialBackoffMillis,
                                           final long maxBackoffMillis,
                                           final double jitterFactor) throws IOException {
        long backoffMillis = computeRetryBackoffMillis(retryNumber, initialBackoffMillis, maxBackoffMillis);
        backoffMillis = applyRetryJitter(backoffMillis, maxBackoffMillis, jitterFactor);
        if (backoffMillis <= 0L) {
            return;
        }
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting to retry HTTP dispatch", interruptedException);
        }
    }

    /**
     * Computes exponential backoff delay for a retry attempt.
     *
     * @param retryNumber retry ordinal (1-based)
     * @param initialBackoffMillis initial backoff delay
     * @param maxBackoffMillis maximum backoff cap
     * @return computed delay in milliseconds
     */
    private static long computeRetryBackoffMillis(final int retryNumber,
                                                  final long initialBackoffMillis,
                                                  final long maxBackoffMillis) {
        if (retryNumber <= 0 || initialBackoffMillis <= 0L) {
            return 0L;
        }
        long candidate = initialBackoffMillis;
        for (int i = 1; i < retryNumber; i++) {
            if (candidate > (Long.MAX_VALUE / 2L)) {
                candidate = Long.MAX_VALUE;
                break;
            }
            candidate = candidate * 2L;
        }
        if (maxBackoffMillis > 0L && candidate > maxBackoffMillis) {
            return maxBackoffMillis;
        }
        return candidate;
    }

    /**
     * Applies symmetric random jitter around the computed base backoff.
     *
     * @param baseBackoffMillis base backoff delay before jitter
     * @param maxBackoffMillis configured max backoff cap
     * @param jitterFactor jitter amplitude in range [0.0, 1.0]
     * @return jittered backoff delay
     */
    private static long applyRetryJitter(final long baseBackoffMillis,
                                         final long maxBackoffMillis,
                                         final double jitterFactor) {
        if (baseBackoffMillis <= 0L || jitterFactor <= 0.0d) {
            return baseBackoffMillis;
        }
        double minMultiplier = 1.0d - jitterFactor;
        if (minMultiplier < 0.0d) {
            minMultiplier = 0.0d;
        }
        double maxMultiplier = 1.0d + jitterFactor;
        double multiplier = ThreadLocalRandom.current().nextDouble(minMultiplier, maxMultiplier);
        long jitteredBackoffMillis = (long) Math.round((double) baseBackoffMillis * multiplier);
        if (jitteredBackoffMillis < 0L) {
            jitteredBackoffMillis = 0L;
        }
        if (maxBackoffMillis > 0L && jitteredBackoffMillis > maxBackoffMillis) {
            return maxBackoffMillis;
        }
        return jitteredBackoffMillis;
    }

    private void awaitCircuitBreakerPermission() throws IOException {
        synchronized (circuitBreakerLock) {
            if (circuitState == CircuitState.OPEN) {
                long nowMillis = System.currentTimeMillis();
                long elapsedMillis = nowMillis - circuitOpenedAtMillis;
                if (elapsedMillis < circuitBreakerOpenStateMillis) {
                    long remainingMillis = circuitBreakerOpenStateMillis - elapsedMillis;
                    throw new CircuitBreakerOpenException("HTTP circuit breaker open; retry in ~" + remainingMillis + " ms");
                }
                circuitState = CircuitState.HALF_OPEN;
                halfOpenActiveCalls = 0;
            }
            if (circuitState == CircuitState.HALF_OPEN) {
                if (halfOpenActiveCalls >= circuitBreakerHalfOpenMaxCalls) {
                    throw new CircuitBreakerOpenException("HTTP circuit breaker half-open probe budget exhausted");
                }
                halfOpenActiveCalls++;
            }
        }
    }

    private void recordCircuitBreakerSuccess() {
        synchronized (circuitBreakerLock) {
            circuitState = CircuitState.CLOSED;
            consecutiveCircuitBreakerFailures = 0;
            circuitOpenedAtMillis = 0L;
            halfOpenActiveCalls = 0;
        }
    }

    private void recordCircuitBreakerFailure() {
        synchronized (circuitBreakerLock) {
            if (circuitState == CircuitState.HALF_OPEN) {
                if (halfOpenActiveCalls > 0) {
                    halfOpenActiveCalls--;
                }
                openCircuitBreakerLocked(System.currentTimeMillis());
                return;
            }
            if (circuitState == CircuitState.OPEN) {
                return;
            }
            consecutiveCircuitBreakerFailures++;
            if (consecutiveCircuitBreakerFailures >= circuitBreakerFailureThreshold) {
                openCircuitBreakerLocked(System.currentTimeMillis());
            }
        }
    }

    private void openCircuitBreakerLocked(final long nowMillis) {
        circuitState = CircuitState.OPEN;
        circuitOpenedAtMillis = nowMillis;
        halfOpenActiveCalls = 0;
        consecutiveCircuitBreakerFailures = 0;
    }

    private void registerPrimaryDispatchAttemptForRetryBudget() {
        synchronized (retryBudgetLock) {
            rotateRetryBudgetWindowIfNeeded(System.currentTimeMillis());
            retryBudgetWindowPrimaryAttempts++;
        }
    }

    private boolean tryConsumeRetryBudgetToken() {
        synchronized (retryBudgetLock) {
            rotateRetryBudgetWindowIfNeeded(System.currentTimeMillis());
            int retryBudgetLimit = computeRetryBudgetLimit(retryBudgetWindowPrimaryAttempts, retryBudgetPercent,
                    retryBudgetMinRetriesPerWindow);
            if (retryBudgetWindowConsumedRetries >= retryBudgetLimit) {
                return false;
            }
            retryBudgetWindowConsumedRetries++;
            return true;
        }
    }

    private void rotateRetryBudgetWindowIfNeeded(final long nowMillis) {
        long elapsedMillis = nowMillis - retryBudgetWindowStartMillis;
        if (elapsedMillis < 0L || elapsedMillis >= retryBudgetWindowMillis) {
            retryBudgetWindowStartMillis = nowMillis;
            retryBudgetWindowPrimaryAttempts = 0;
            retryBudgetWindowConsumedRetries = 0;
        }
    }

    private static int computeRetryBudgetLimit(final int primaryAttempts,
                                               final int budgetPercent,
                                               final int minRetriesPerWindow) {
        if (primaryAttempts <= 0) {
            return minRetriesPerWindow;
        }
        long proportionalBudget = ((long) primaryAttempts * (long) budgetPercent) / 100L;
        if (proportionalBudget > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        int proportionalBudgetInt = (int) proportionalBudget;
        return Math.max(minRetriesPerWindow, proportionalBudgetInt);
    }

    /**
     * Converts and emits a dispatch failure through the configured error reporting and close-on-error path.
     *
     * @param failure failure to report
     * @param errorConsumer error consumer callback
     * @param dispatchErrorMessageKey resource-bundle key for localized message formatting
     * @param closeOnDispatchError whether close-on-error behavior is enabled
     * @param closeThreadName close thread name (used in async close scheduling)
     */
    private void reportDispatchFailure(final Throwable failure,
                                       final Consumer<String> errorConsumer,
                                       final String dispatchErrorMessageKey,
                                       final boolean closeOnDispatchError,
                                       final String closeThreadName) {
        String details = toDispatchFailureDetails(failure);
        String localizedMessage;
        try {
            localizedMessage = MessageHandlerResourceBundle.format(dispatchErrorMessageKey, details);
        } catch (RuntimeException ignored) {
            localizedMessage = details;
        }
        safeConsumeError(errorConsumer, localizedMessage);
        requestCloseOnErrorIfEnabled(closeOnDispatchError, closeThreadName);
    }

    /**
     * Safely invokes an error consumer, swallowing consumer-side runtime errors.
     *
     * @param errorConsumer target consumer
     * @param message message to emit
     */
    private static void safeConsumeError(final Consumer<String> errorConsumer, final String message) {
        try {
            errorConsumer.accept(message);
        } catch (RuntimeException ignored) {
            // Swallow consumer failures to avoid interrupting caller flow.
        }
    }

    /**
     * Builds a human-readable details string for dispatch failures.
     *
     * @param failure failure to describe
     * @return failure message or fallback exception class name
     */
    private static String toDispatchFailureDetails(final Throwable failure) {
        String details = failure.getMessage();
        return details == null ? failure.getClass().getName() : details;
    }

    /**
     * Applies JVM-level keep-alive settings used by {@link java.net.HttpURLConnection}.
     * <p>
     * Settings are best effort and silently ignored in restricted environments.
     *
     * @param maxConnections desired keep-alive max connection hint
     */
    private static void configureHttpConnectionPooling(final int maxConnections) {
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("maxConnections must be > 0");
        }
        synchronized (HTTP_TRANSPORT_CONFIGURATION_LOCK) {
            try {
                String currentKeepAlive = System.getProperty(HTTP_KEEP_ALIVE_PROPERTY);
                if (currentKeepAlive == null || !"false".equalsIgnoreCase(currentKeepAlive)) {
                    System.setProperty(HTTP_KEEP_ALIVE_PROPERTY, Boolean.TRUE.toString());
                }

                String currentMaxConnectionsProperty = System.getProperty(HTTP_MAX_CONNECTIONS_PROPERTY);
                int currentMaxConnections = parsePositiveInteger(currentMaxConnectionsProperty);
                if (currentMaxConnections < maxConnections) {
                    System.setProperty(HTTP_MAX_CONNECTIONS_PROPERTY, Integer.toString(maxConnections));
                }
            } catch (SecurityException ignored) {
                // Running in a restricted environment where global HTTP properties cannot be overridden.
            }
        }
    }

    /**
     * Parses a positive integer from a system-property value.
     *
     * @param raw raw property value
     * @return parsed integer or {@code -1} when absent/invalid
     */
    private static int parsePositiveInteger(final String raw) {
        if (raw == null) {
            return -1;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

}
