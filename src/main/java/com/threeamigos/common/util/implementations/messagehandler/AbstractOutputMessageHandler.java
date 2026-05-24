package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import jakarta.annotation.Nonnull;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base class for output-oriented handlers that optionally dispatch write operations asynchronously.
 * It is an opt-in extension over {@link AbstractMessageHandler}; handlers that do not extend this
 * class remain synchronous unless they implement their own threading model.
 * <p>
 * Subclasses call {@link #initializeOutputDispatch} once from their constructor to configure the
 * dispatch mode. Two modes are supported:
 * <ul>
 *   <li><strong>Synchronous</strong> ({@code async=false}): every {@link #dispatch(Runnable)} call
 *       runs the task immediately on the calling thread.</li>
 *   <li><strong>Asynchronous</strong> ({@code async=true}): tasks are enqueued on a bounded or
 *       unbounded {@link java.util.concurrent.LinkedBlockingQueue} and executed sequentially by a
 *       single background worker thread.</li>
 * </ul>
 *
 * <h3>Daemon worker thread</h3>
 * <p>
 * The background worker thread is created as a <em>daemon</em> thread. It will not prevent the JVM
 * from exiting when all non-daemon threads have finished. If graceful flushing of pending tasks on
 * JVM exit is required, pass {@code registerShutdownHook=true} to
 * {@link #initializeOutputDispatch}, which registers a shutdown hook that calls {@link #close()}.
 *
 * <h3>Backpressure</h3>
 * <p>
 * When the worker queue is full (bounded capacity only), {@link #dispatch(Runnable)} falls back to
 * executing the task synchronously on the calling thread rather than dropping it. This provides
 * backpressure without silent message loss.
 *
 * <h3>Graceful shutdown and drain-in-finally</h3>
 * <p>
 * {@link #close()} is idempotent. On the first call it interrupts the worker thread and waits for
 * it to finish. The worker's {@code finally} block drains any tasks already queued before
 * the interrupt was processed, so no enqueued messages are lost during shutdown. After the worker
 * exits, the caller thread performs one additional drain pass to capture tasks enqueued in the
 * window between the interrupt and the worker's own drain, then calls {@link #closeOutput()} to
 * release the underlying output resource.
 *
 * <h3>Close-on-error helper for subclasses</h3>
 * <p>
 * Subclasses that support "close on unrecoverable write/dispatch error" should use
 * {@link #requestCloseOnErrorIfEnabled(boolean, String)}. It prevents deadlocks in async mode and
 * guarantees one-shot close-thread scheduling under repeated failures.
 * <p>
 * @author Stefano Reksten
 */
public abstract class AbstractOutputMessageHandler extends AbstractMessageHandler implements AutoCloseable {

    private volatile boolean async;
    private BlockingQueue<Runnable> queue;
    private ExecutorService worker;
    private Thread shutdownHook;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean asyncCloseOnErrorScheduled = new AtomicBoolean(false);
    private final AtomicLong successfulOutputOperations = new AtomicLong(0L);
    private final AtomicLong failedOutputOperations = new AtomicLong(0L);
    private final AtomicLong consecutiveOutputFailures = new AtomicLong(0L);
    private final AtomicLong lastSuccessEpochMillis = new AtomicLong(0L);
    private final AtomicLong lastFailureEpochMillis = new AtomicLong(0L);
    private final AtomicLong droppedOutputOperations = new AtomicLong(0L);
    private final AtomicLong retryAttempts = new AtomicLong(0L);
    private final AtomicLong retrySuccesses = new AtomicLong(0L);
    private final AtomicLong retryFailures = new AtomicLong(0L);
    private final AtomicLong dispatchAttempts = new AtomicLong(0L);
    private final AtomicLong asyncEnqueuedOperations = new AtomicLong(0L);
    private final AtomicLong synchronousFallbackOperations = new AtomicLong(0L);
    private final AtomicLong queueSaturationEvents = new AtomicLong(0L);
    private final AtomicLong maxObservedQueueSize = new AtomicLong(0L);
    private volatile int configuredQueueCapacity = 0;
    private final Object dispatchLock = new Object();
    protected volatile LogRecordFormatter logRecordFormatter;

    /**
     * Immutable snapshot of output-handler health counters.
     */
    public static final class HandlerHealthMetrics {
        private final long successfulOperations;
        private final long failedOperations;
        private final long totalOperations;
        private final long consecutiveFailures;
        private final long lastSuccessTimestampMillis;
        private final long lastFailureTimestampMillis;
        private final boolean async;
        private final int pendingQueueSize;
        private final boolean closed;
        private final boolean healthy;
        private final long droppedOperations;
        private final long retryAttempts;
        private final long retrySuccesses;
        private final long retryFailures;
        private final long dispatchAttempts;
        private final long asyncEnqueuedOperations;
        private final long synchronousFallbackOperations;
        private final long queueSaturationEvents;
        private final long maxObservedQueueSize;
        private final int configuredQueueCapacity;
        private final boolean boundedQueue;
        private final double queueSaturationRatio;

        private HandlerHealthMetrics(final long successfulOperations,
                                     final long failedOperations,
                                     final long totalOperations,
                                     final long consecutiveFailures,
                                     final long lastSuccessTimestampMillis,
                                     final long lastFailureTimestampMillis,
                                     final boolean async,
                                     final int pendingQueueSize,
                                     final boolean closed,
                                     final boolean healthy,
                                     final long droppedOperations,
                                     final long retryAttempts,
                                     final long retrySuccesses,
                                     final long retryFailures,
                                     final long dispatchAttempts,
                                     final long asyncEnqueuedOperations,
                                     final long synchronousFallbackOperations,
                                     final long queueSaturationEvents,
                                     final long maxObservedQueueSize,
                                     final int configuredQueueCapacity,
                                     final boolean boundedQueue,
                                     final double queueSaturationRatio) {
            this.successfulOperations = successfulOperations;
            this.failedOperations = failedOperations;
            this.totalOperations = totalOperations;
            this.consecutiveFailures = consecutiveFailures;
            this.lastSuccessTimestampMillis = lastSuccessTimestampMillis;
            this.lastFailureTimestampMillis = lastFailureTimestampMillis;
            this.async = async;
            this.pendingQueueSize = pendingQueueSize;
            this.closed = closed;
            this.healthy = healthy;
            this.droppedOperations = droppedOperations;
            this.retryAttempts = retryAttempts;
            this.retrySuccesses = retrySuccesses;
            this.retryFailures = retryFailures;
            this.dispatchAttempts = dispatchAttempts;
            this.asyncEnqueuedOperations = asyncEnqueuedOperations;
            this.synchronousFallbackOperations = synchronousFallbackOperations;
            this.queueSaturationEvents = queueSaturationEvents;
            this.maxObservedQueueSize = maxObservedQueueSize;
            this.configuredQueueCapacity = configuredQueueCapacity;
            this.boundedQueue = boundedQueue;
            this.queueSaturationRatio = queueSaturationRatio;
        }

        public long getSuccessfulOperations() {
            return successfulOperations;
        }

        public long getFailedOperations() {
            return failedOperations;
        }

        public long getTotalOperations() {
            return totalOperations;
        }

        public long getConsecutiveFailures() {
            return consecutiveFailures;
        }

        public long getLastSuccessTimestampMillis() {
            return lastSuccessTimestampMillis;
        }

        public long getLastFailureTimestampMillis() {
            return lastFailureTimestampMillis;
        }

        public boolean isAsync() {
            return async;
        }

        public int getPendingQueueSize() {
            return pendingQueueSize;
        }

        public boolean isClosed() {
            return closed;
        }

        public boolean isHealthy() {
            return healthy;
        }

        public long getDroppedOperations() {
            return droppedOperations;
        }

        public long getRetryAttempts() {
            return retryAttempts;
        }

        public long getRetrySuccesses() {
            return retrySuccesses;
        }

        public long getRetryFailures() {
            return retryFailures;
        }

        public long getDispatchAttempts() {
            return dispatchAttempts;
        }

        public long getAsyncEnqueuedOperations() {
            return asyncEnqueuedOperations;
        }

        public long getSynchronousFallbackOperations() {
            return synchronousFallbackOperations;
        }

        public long getQueueSaturationEvents() {
            return queueSaturationEvents;
        }

        public long getMaxObservedQueueSize() {
            return maxObservedQueueSize;
        }

        public int getConfiguredQueueCapacity() {
            return configuredQueueCapacity;
        }

        public boolean isBoundedQueue() {
            return boundedQueue;
        }

        public double getQueueSaturationRatio() {
            return queueSaturationRatio;
        }
    }

    public AbstractOutputMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                                        final @Nonnull LogRecordFormatter logRecordFormatter) {
        Objects.requireNonNull(logRecordFactory, MessageHandlerResourceBundle.get("nullLogRecordFactoryProvided"));
        Objects.requireNonNull(logRecordFormatter, MessageHandlerResourceBundle.get("nullFormatterProvided"));
        this.logRecordFormatter = logRecordFormatter;
    }

    /**
     * Initializes the optional async dispatch infrastructure.
     * <p>
     * Must be called once from the subclass constructor, after all subclass fields have been set.
     * <p>
     * When {@code async} is {@code true}:
     * <ul>
     *   <li>A {@link java.util.concurrent.LinkedBlockingQueue} is created with the given capacity
     *       (unbounded when {@code queueCapacity} is {@code 0} or negative).</li>
     *   <li>A single daemon worker thread is started; it drains tasks from the queue
     *       sequentially until interrupted.</li>
     *   <li>If {@code registerShutdownHook} is {@code true}, a JVM shutdown hook is registered
     *       that calls {@link #close()} to flush any pending tasks before the JVM exits.</li>
     * </ul>
     * When {@code async} is {@code false}, calls to {@link #dispatch(Runnable)} execute the
     * task synchronously on the calling thread.
     *
     * @param async                whether to enable asynchronous dispatch
     * @param queueCapacity        maximum number of queued tasks; {@code 0} or negative means unbounded
     * @param registerShutdownHook whether to register a JVM shutdown hook that calls {@link #close()}
     * @param workerThreadName     name to assign to the background worker thread
     * @param shutdownHookName     name to assign to the shutdown hook thread (ignored when
     *                             {@code registerShutdownHook} is {@code false})
     */
    protected final void initializeOutputDispatch(final boolean async, final int queueCapacity,
                                                  final boolean registerShutdownHook,
                                                  final String workerThreadName, final String shutdownHookName) {
        this.async = async;
        this.configuredQueueCapacity = queueCapacity;
        if (async) {
            this.queue = queueCapacity > 0 ? new LinkedBlockingQueue<>(queueCapacity) : new LinkedBlockingQueue<>();
            this.worker = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, workerThreadName);
                t.setDaemon(true);
                return t;
            });
            this.worker.submit(this::drainLoop);
            if (registerShutdownHook) {
                this.shutdownHook = new Thread(this::close, shutdownHookName);
                Runtime.getRuntime().addShutdownHook(this.shutdownHook);
            } else {
                this.shutdownHook = null;
            }
        } else {
            this.queue = null;
            this.worker = null;
            this.shutdownHook = null;
        }
    }

    /**
     * Submits a write task for execution.
     * <p>
     * When async mode is active, the closed-flag check and the queue offer are performed together
     * under {@code dispatchLock}, which is also acquired by {@link #close()} before it drains the
     * queue. This ensures that no task can slip into the queue after the final drain has run:
     * either the task is offered before {@code close()} reaches the barrier (and will be drained),
     * or it sees {@code closed=true} and throws.
     * <p>
     * If the queue is full, the task is executed synchronously on the calling thread so that no
     * messages are silently dropped.
     * <p>
     * When async mode is inactive, the task runs synchronously on the calling thread.
     * Runtime failures raised by the task are trapped and reported through
     * {@link InnerErrorMessageHandler}; they are not propagated to logging callers.
     *
     * @param task the write operation to execute; must not be {@code null}
     * @throws IllegalStateException if the handler has been closed
     */
    protected final void dispatch(final Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        dispatchAttempts.incrementAndGet();
        if (!async) {
            if (closed.get()) {
                recordDroppedOutput();
                throw new IllegalStateException(MessageHandlerResourceBundle.get("handlerIsClosed"));
            }
            runTaskSafely(task, "synchronous dispatch");
            return;
        }
        synchronized (dispatchLock) {
            if (closed.get()) {
                recordDroppedOutput();
                throw new IllegalStateException(MessageHandlerResourceBundle.get("handlerIsClosed"));
            }
            if (!queue.offer(task)) {
                // queue full: run synchronously to avoid losing messages
                recordQueueSaturation();
                runTaskSafely(task, "queue-saturation synchronous fallback");
            } else {
                asyncEnqueuedOperations.incrementAndGet();
                updateMaxObservedQueueSize(queue.size());
            }
        }
    }

    /**
     * Executes one output task and traps runtime failures to preserve caller flow.
     *
     * @param task dispatch task
     * @param executionPhase short label describing the execution path
     */
    private void runTaskSafely(final Runnable task, final String executionPhase) {
        try {
            task.run();
        } catch (RuntimeException taskFailure) {
            reportInnerFailure("executing output task (" + executionPhase + ")", taskFailure);
        }
    }

    private void drainLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Runnable task = queue.take();
                runTaskSafely(task, "async worker");
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            Runnable task;
            while ((task = queue.poll()) != null) {
                runTaskSafely(task, "async worker finally-drain");
            }
        }
    }

    /**
     * Removes a previously registered JVM shutdown hook.
     * <p>
     * Exposed as a protected method (rather than inlined) so that tests can override it to
     * avoid manipulating the real JVM shutdown-hook registry.
     *
     * @param hook the shutdown hook thread to remove
     */
    protected void removeShutdownHook(final Thread hook) {
        Runtime.getRuntime().removeShutdownHook(hook);
    }

    /**
     * Waits up to 5 seconds for the worker thread to finish processing after a
     * {@link java.util.concurrent.ExecutorService#shutdownNow()} call.
     * <p>
     * If the worker does not terminate in time, {@code shutdownNow()} is called again as a
     * best-effort measure. Exposed as protected so that tests can override the wait behavior.
     *
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    protected void awaitTermination() throws InterruptedException {
        if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
            // Best effort: request interruption again if the worker did not stop in time.
            worker.shutdownNow();
        }
    }

    /**
     * Closes the underlying output resource (e.g., a file writer or network stream).
     * <p>
     * Called by {@link #close()} after the worker thread has been stopped and any remaining
     * queued tasks have been drained. The default implementation is a no-op; subclasses that
     * hold output resources should override this method to flush and close them.
     */
    protected void closeOutput() {
        // Default no-op. Subclasses can override to close underlying resources.
    }

    /**
     * Replaces the log formatter used to render each output line.
     * <p>
     * The formatter is applied to every message before it is handed to the underlying output
     * (file, console, etc.).
     * For server deployments that rely on machine-parsed console/file logs, prefer setting this
     * once during application bootstrap (or via a centralized factory) and keep it stable across
     * handlers to avoid schema drift.
     *
     * @param logRecordFormatter the non-null formatter to use
     * @throws NullPointerException if {@code formatter} is {@code null}
     */
    public final void setLogRecordFormatter(@Nonnull final LogRecordFormatter logRecordFormatter) {
        Objects.requireNonNull(logRecordFormatter, MessageHandlerResourceBundle.get("nullFormatterProvided"));
        this.logRecordFormatter = logRecordFormatter;
    }

    /**
     * Returns the currently active {@link LogRecordFormatter}.
     *
     * @return the non-null formatter
     */
    protected final LogRecordFormatter getLogRecordFormatter() {
        return logRecordFormatter;
    }

    /**
     * Returns {@code true} if this handler dispatches writes asynchronously.
     * <p>
     * Subclasses that need to call {@link #close()} from within a dispatched task (e.g., on
     * write error) must check this flag: in async mode, calling {@code close()} on the same
     * thread that runs dispatch tasks causes a deadlock because {@code close()} calls
     * {@code awaitTermination()}, which waits for the worker thread to finish.
     *
     * @return {@code true} if async mode is active
     */
    protected final boolean isAsync() {
        return async;
    }

    /**
     * Records one successful output operation.
     * <p>
     * A success clears the consecutive-failure streak and updates {@code lastSuccessTimestampMillis}.
     * Subclasses should invoke this exactly once per successful sink operation.
     */
    protected final void recordOutputSuccess() {
        successfulOutputOperations.incrementAndGet();
        consecutiveOutputFailures.set(0L);
        lastSuccessEpochMillis.set(System.currentTimeMillis());
    }

    /**
     * Records one failed output operation.
     * <p>
     * A failure increases the consecutive-failure streak and updates {@code lastFailureTimestampMillis}.
     * Subclasses should invoke this exactly once per failed sink operation.
     */
    protected final void recordOutputFailure() {
        failedOutputOperations.incrementAndGet();
        consecutiveOutputFailures.incrementAndGet();
        lastFailureEpochMillis.set(System.currentTimeMillis());
    }

    /**
     * Records one dropped output operation.
     * <p>
     * The base class increments this counter automatically when a new dispatch request arrives
     * after the handler has been closed.
     */
    protected final void recordDroppedOutput() {
        droppedOutputOperations.incrementAndGet();
    }

    /**
     * Records one retry attempt.
     * <p>
     * Subclasses with retry logic should call this once per attempt.
     */
    protected final void recordRetryAttempt() {
        retryAttempts.incrementAndGet();
    }

    /**
     * Records one successful retry attempt.
     */
    protected final void recordRetrySuccess() {
        retrySuccesses.incrementAndGet();
    }

    /**
     * Records one failed retry attempt.
     */
    protected final void recordRetryFailure() {
        retryFailures.incrementAndGet();
    }

    /**
     * Returns {@code true} when the handler is open and has no consecutive sink failures.
     */
    public final boolean isHealthy() {
        return !closed.get() && consecutiveOutputFailures.get() == 0L;
    }

    /**
     * Returns an immutable snapshot of handler-health counters.
     */
    public final HandlerHealthMetrics getHandlerHealthMetrics() {
        long successful = successfulOutputOperations.get();
        long failed = failedOutputOperations.get();
        long consecutiveFailures = consecutiveOutputFailures.get();
        long total = successful + failed;
        boolean closedNow = closed.get();
        boolean healthyNow = !closedNow && consecutiveFailures == 0L;
        int pendingQueue = queue == null ? 0 : queue.size();
        long dispatchAttemptsNow = dispatchAttempts.get();
        long queueSaturationEventsNow = queueSaturationEvents.get();
        boolean boundedQueue = async && configuredQueueCapacity > 0;
        double saturationRatio = dispatchAttemptsNow == 0L
                ? 0.0d
                : (double) queueSaturationEventsNow / (double) dispatchAttemptsNow;
        return new HandlerHealthMetrics(
                successful,
                failed,
                total,
                consecutiveFailures,
                lastSuccessEpochMillis.get(),
                lastFailureEpochMillis.get(),
                async,
                pendingQueue,
                closedNow,
                healthyNow,
                droppedOutputOperations.get(),
                retryAttempts.get(),
                retrySuccesses.get(),
                retryFailures.get(),
                dispatchAttemptsNow,
                asyncEnqueuedOperations.get(),
                synchronousFallbackOperations.get(),
                queueSaturationEventsNow,
                maxObservedQueueSize.get(),
                configuredQueueCapacity,
                boundedQueue,
                saturationRatio
        );
    }

    /**
     * Requests handler shutdown when an unrecoverable write/dispatch error occurs.
     * <p>
     * In synchronous mode, {@link #close()} is executed immediately on the caller thread.
     * In asynchronous mode, {@link #close()} is dispatched on a dedicated thread to avoid
     * deadlocking the single async worker, and only the first request spawns that thread.
     *
     * @param closeOnError whether close-on-error behavior is enabled by the subclass
     * @param closeThreadName thread name to use when asynchronous close must be scheduled
     */
    protected final void requestCloseOnErrorIfEnabled(final boolean closeOnError,
                                                      final @Nonnull String closeThreadName) {
        if (!closeOnError) {
            return;
        }
        Objects.requireNonNull(closeThreadName, "closeThreadName must not be null");
        if (!isAsync()) {
            close();
            return;
        }
        if (asyncCloseOnErrorScheduled.compareAndSet(false, true)) {
            new Thread(this::close, closeThreadName).start();
        }
    }

    private void recordQueueSaturation() {
        queueSaturationEvents.incrementAndGet();
        synchronousFallbackOperations.incrementAndGet();
        if (configuredQueueCapacity > 0) {
            updateMaxObservedQueueSize(configuredQueueCapacity);
        } else if (queue != null) {
            updateMaxObservedQueueSize(queue.size());
        }
    }

    private void updateMaxObservedQueueSize(final int observedSize) {
        long candidate = observedSize < 0 ? 0L : observedSize;
        long current;
        do {
            current = maxObservedQueueSize.get();
            if (candidate <= current) {
                return;
            }
        } while (!maxObservedQueueSize.compareAndSet(current, candidate));
    }

    private void drainQueueInCallerThread() {
        if (queue == null) {
            return;
        }
        Runnable task;
        while ((task = queue.poll()) != null) {
            runTaskSafely(task, "close-time caller-thread drain");
        }
    }

    /**
     * Shuts down the handler, flushing all pending write tasks and releasing resources.
     * <p>
     * The method is idempotent: later calls after the first are silently ignored.
     * The shutdown sequence is:
     * <ol>
     *   <li>Deregisters the JVM shutdown hook (if one was registered), ignoring errors that
     *       indicate the JVM is already shutting down.</li>
     *   <li>Interrupts the worker thread via {@link java.util.concurrent.ExecutorService#shutdownNow()}.</li>
     *   <li>Waits for the worker thread to drain its remaining queue and exit (via {@link #awaitTermination()}).</li>
     *   <li>Drains any tasks that were enqueued between the interrupt and the queue-drain
     *       in the worker's {@code finally} block, executing them on the calling thread.</li>
     *   <li>Calls {@link #closeOutput()} to release the underlying output resource.</li>
     * </ol>
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (worker != null) {
            if (shutdownHook != null) {
                try {
                    removeShutdownHook(shutdownHook);
                } catch (IllegalStateException ignored) {
                    // JVM is shutting down
                }
            }
            synchronized (dispatchLock) {
                // Barrier: any in-flight dispatch() that already passed the closed check must
                // have completed its queue.offer() before we reach here. Interrupting the worker
                // inside the lock guarantees no task can be offered to the queue after this point.
                worker.shutdownNow();
            }
            try {
                awaitTermination();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        drainQueueInCallerThread();
        try {
            closeOutput();
        } catch (RuntimeException closeFailure) {
            reportInnerFailure("closing output resource", closeFailure);
        }
    }
}
