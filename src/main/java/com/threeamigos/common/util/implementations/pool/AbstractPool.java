package com.threeamigos.common.util.implementations.pool;

import com.threeamigos.common.util.interfaces.pool.Pool;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Skeletal implementation of {@link Pool} that provides all concurrency and lifecycle
 * infrastructure, leaving only resource creation and destruction to subclasses.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>A fair {@link Semaphore} with {@code capacity} permits limits the number of resources
 *       on loan at any time. A permit is acquired on every successful borrow and released on
 *       every {@link #release} or {@link #invalidate} call, ensuring the pool never exceeds
 *       its configured capacity.</li>
 *   <li>Idle resources are held in a {@link LinkedBlockingQueue}. Resources are created lazily:
 *       only when no idle resource is available and the semaphore grants a permit.</li>
 *   <li>On each borrow, idle resources whose {@link #isValid} check fails are silently
 *       discarded and replaced, keeping the semaphore balanced.</li>
 * </ul>
 *
 * <h3>Subclassing</h3>
 * <p>
 * Subclasses must implement two methods:
 * <pre>{@code
 * protected abstract T  createResource()   throws Exception;
 * protected abstract void destroyResource(T resource) throws Exception;
 * }</pre>
 * Optionally, override {@link #isValid(Object)} to perform a lightweight health check
 * before reuse (e.g. {@code connection.isValid(1)} for JDBC):
 * <pre>{@code
 * @Override
 * protected boolean isValid(Connection c) {
 *     try { return c.isValid(1); }
 *     catch (SQLException e) { return false; }
 * }
 * }</pre>
 *
 * <h3>Thread safety</h3>
 * <p>
 * All public methods are fully thread-safe. The semaphore and the blocking queue handle
 * concurrent access without additional synchronization in subclasses (assuming
 * {@link #isValid} is side-effect-free and {@link #createResource}/{@link #destroyResource}
 * are thread-safe with respect to the underlying resource type).
 *
 * @param <T> the type of pooled resource
 * @author Stefano Reksten
 */
public abstract class AbstractPool<T> implements Pool<T> {

    private final int capacity;
    private final Semaphore permits;
    private final LinkedBlockingQueue<T> idleResources;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Constructs a pool with the given maximum capacity.
     *
     * @param capacity maximum number of resources this pool will manage; must be &gt; 0
     * @throws IllegalArgumentException if {@code capacity} is not positive
     */
    protected AbstractPool(final int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0, got: " + capacity);
        }
        this.capacity = capacity;
        this.permits = new Semaphore(capacity, true);
        this.idleResources = new LinkedBlockingQueue<>(capacity);
    }

    // -------------------------------------------------------------------------
    // Abstract hooks
    // -------------------------------------------------------------------------

    /**
     * Creates a new resource instance.
     * <p>
     * Called lazily when no idle resource is available and the semaphore has granted
     * a permit for a new borrow slot. May be called from multiple threads concurrently.
     *
     * @return a new, ready-to-use resource; must not be {@code null}
     * @throws Exception if the resource cannot be created
     */
    @Nonnull
    protected abstract T createResource() throws Exception;

    /**
     * Permanently destroys a resource, releasing any underlying handles (sockets,
     * file descriptors, database connections, etc.).
     * <p>
     * Called when a resource is invalidated, found stale during reuse, or drained
     * at pool close. Implementations should not throw if the resource is already
     * closed or partially initialized.
     *
     * @param resource the resource to destroy; never {@code null}
     * @throws Exception if an error occurs during destruction
     */
    protected abstract void destroyResource(T resource) throws Exception;

    /**
     * Determines whether an idle resource is still healthy and safe to reuse.
     * <p>
     * Called each time an idle resource is a candidate for reuse. The default
     * implementation always returns {@code true}; override to perform a lightweight
     * health check (e.g. {@code connection.isValid(1)} for JDBC connections).
     * <p>
     * This method must not perform blocking I/O that could significantly delay
     * acquire operations.
     *
     * @param resource the idle resource to check; never {@code null}
     * @return {@code true} if the resource is healthy and may be returned to the caller
     */
    protected boolean isValid(final T resource) {
        return true;
    }

    // -------------------------------------------------------------------------
    // Pool interface
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * @throws InterruptedException  if the calling thread is interrupted while waiting for a permit
     * @throws IllegalStateException if the pool has been closed
     */
    @Override
    @Nonnull
    public T acquire() throws InterruptedException {
        checkNotClosed();
        permits.acquire();
        try {
            checkNotClosed();
            return borrowOrCreate();
        } catch (final RuntimeException e) {
            permits.release();
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws InterruptedException  if the calling thread is interrupted while waiting for a permit
     * @throws IllegalStateException if the pool has been closed
     */
    @Override
    public Optional<T> acquire(final long timeout, @Nonnull final TimeUnit unit) throws InterruptedException {
        checkNotClosed();
        if (!permits.tryAcquire(timeout, unit)) {
            return Optional.empty();
        }
        try {
            checkNotClosed();
            return Optional.of(borrowOrCreate());
        } catch (final RuntimeException e) {
            permits.release();
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * If {@link #isValid} returns {@code false} for the returned resource, it is
     * destroyed rather than returned to the idle queue, and the permit is still released.
     */
    @Override
    public void release(final T resource) {
        if (resource == null) {
            return;
        }
        try {
            if (!closed.get() && isValid(resource)) {
                if (!idleResources.offer(resource)) {
                    // Queue full — can happen if capacity was reduced; destroy the surplus resource
                    destroyResourceQuietly(resource);
                }
            } else {
                destroyResourceQuietly(resource);
            }
        } finally {
            permits.release();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invalidate(final T resource) {
        if (resource == null) {
            return;
        }
        try {
            destroyResourceQuietly(resource);
        } finally {
            permits.release();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int capacity() {
        return capacity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int available() {
        return idleResources.size();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Calculated as {@code capacity - availablePermits()}: only active borrows hold
     * permits; idle resources do not.
     */
    @Override
    public int active() {
        return capacity - permits.availablePermits();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Marks the pool as closed and destroys all idle resources. Resources currently
     * on loan are not forcibly reclaimed; callers that subsequently call
     * {@link #release} or {@link #invalidate} will have their resource destroyed
     * immediately.
     * <p>
     * If multiple idle resources fail to close, the first exception is rethrown after
     * all resources have been given a chance to close.
     *
     * @throws Exception the first exception encountered while destroying idle resources,
     *                   if any
     */
    @Override
    public void close() throws Exception {
        if (!closed.compareAndSet(false, true)) {
            return; // idempotent
        }

        final List<T> snapshot = new ArrayList<>(capacity);
        idleResources.drainTo(snapshot);

        Exception firstFailure = null;
        for (final T resource : snapshot) {
            try {
                destroyResource(resource);
            } catch (final Exception e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }

        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Returns an idle resource (after health-checking it) or creates a new one.
     * Stale idle resources are discarded without releasing the permit; the same
     * permit is reused until a healthy resource or a fresh one is obtained.
     *
     * @return a healthy resource ready for use
     * @throws IllegalStateException if resource creation fails
     */
    private T borrowOrCreate() {
        // Drain stale idle resources without releasing the permit
        T resource;
        while ((resource = idleResources.poll()) != null) {
            if (isValid(resource)) {
                return resource;
            }
            destroyResourceQuietly(resource);
        }

        // No idle resource available — create a new one
        try {
            return createResource();
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to create pool resource", e);
        }
    }

    private void destroyResourceQuietly(final T resource) {
        try {
            destroyResource(resource);
        } catch (final Exception ignored) {
            // Best-effort; callers that care (close()) handle the exception themselves
        }
    }

    private void checkNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("Pool is closed");
        }
    }
}
