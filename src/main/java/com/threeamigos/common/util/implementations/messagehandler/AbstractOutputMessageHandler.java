package com.threeamigos.common.util.implementations.messagehandler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for output-oriented handlers that optionally dispatch write operations asynchronously.
 * <p>
 * When async dispatch is enabled, calls to {@link #dispatch(Runnable)} enqueue tasks on a single-worker queue.
 * On shutdown, the worker is interrupted and then any remaining queued tasks are drained in the worker's
 * {@code finally} block before resources are closed.
 */
public abstract class AbstractOutputMessageHandler extends AbstractMessageHandler implements AutoCloseable {

    private boolean async;
    private BlockingQueue<Runnable> queue;
    private ExecutorService worker;
    private Thread shutdownHook;
    private final AtomicBoolean closed = new AtomicBoolean(false);

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
     * When async mode is active, the task is offered to the worker queue. If the queue is full,
     * the task is executed synchronously on the calling thread so that no messages are silently
     * dropped.
     * <p>
     * When async mode is inactive, the task runs synchronously on the calling thread.
     *
     * @param task the write operation to execute; must not be {@code null}
     */
    protected final void dispatch(final Runnable task) {
        if (!async) {
            task.run();
            return;
        }
        if (!queue.offer(task)) {
            // queue full: run synchronously to avoid losing messages
            task.run();
        }
    }

    private void drainLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Runnable task = queue.take();
                task.run();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            Runnable task;
            while ((task = queue.poll()) != null) {
                task.run();
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

    private void drainQueueInCallerThread() {
        if (queue == null) {
            return;
        }
        Runnable task;
        while ((task = queue.poll()) != null) {
            task.run();
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
            worker.shutdownNow();
            try {
                awaitTermination();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        drainQueueInCallerThread();
        closeOutput();
    }
}
