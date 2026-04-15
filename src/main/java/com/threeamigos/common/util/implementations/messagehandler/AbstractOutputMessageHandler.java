package com.threeamigos.common.util.implementations.messagehandler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Base class for message handlers that optionally dispatch output operations asynchronously.
 */
public abstract class AbstractOutputMessageHandler extends AbstractMessageHandler implements AutoCloseable {

    private boolean async;
    private BlockingQueue<Runnable> queue;
    private ExecutorService worker;
    private Thread shutdownHook;

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

    protected void removeShutdownHook(final Thread hook) {
        Runtime.getRuntime().removeShutdownHook(hook);
    }

    protected void awaitTermination() throws InterruptedException {
        if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
            // Best effort: request interruption again if the worker did not stop in time.
            worker.shutdownNow();
        }
    }

    protected void closeOutput() {
        // Default no-op. Subclasses can override to close underlying resources.
    }

    @Override
    public void close() {
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
        closeOutput();
    }
}
