package com.threeamigos.common.util.interfaces.pool;

import jakarta.annotation.Nonnull;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Generic resource pool contract.
 * <p>
 * A pool manages a bounded set of pre-created or lazily-created resources of type {@code T}.
 * Callers borrow a resource via {@link #acquire()}, use it, then return it via
 * {@link #release(Object)} or discard it via {@link #invalidate(Object)} when it is found to be
 * unhealthy. Typical resource types include database connections, HTTP client instances, or
 * any other expensive-to-create, reusable objects.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Acquire a resource — {@link #acquire()} or {@link #acquire(long, TimeUnit)}.</li>
 *   <li>Use the resource on the calling thread.</li>
 *   <li>Return it — {@link #release(Object)} on success, {@link #invalidate(Object)} when the
 *       resource is found to be broken or expired.</li>
 *   <li>At shutdown, call {@link #close()} to release all idle resources and signal that no
 *       further acquisitions are permitted.</li>
 * </ol>
 *
 * <h3>Thread safety</h3>
 * <p>
 * Implementations must be fully thread-safe. Multiple threads may call any method concurrently.
 *
 * <h3>Recommended usage pattern</h3>
 * <pre>{@code
 * T resource = pool.acquire();
 * try {
 *     // use resource
 * } catch (SomeException e) {
 *     pool.invalidate(resource);
 *     resource = null;
 *     throw e;
 * } finally {
 *     if (resource != null) {
 *         pool.release(resource);
 *     }
 * }
 * }</pre>
 *
 * @param <T> the type of pooled resource
 * @author Stefano Reksten
 */
public interface Pool<T> extends AutoCloseable {

    /**
     * Acquires a resource from the pool, blocking indefinitely until one becomes available.
     * <p>
     * The caller is responsible for returning the resource via {@link #release(Object)} or
     * {@link #invalidate(Object)} after use.
     *
     * @return a non-null resource ready for use
     * @throws InterruptedException  if the calling thread is interrupted while waiting
     * @throws IllegalStateException if the pool has been closed
     */
    @Nonnull
    T acquire() throws InterruptedException;

    /**
     * Acquires a resource from the pool, waiting up to the given timeout for one to become
     * available.
     * <p>
     * Returns {@link Optional#empty()} if the timeout elapses before a resource becomes
     * available. The caller must return the resource via {@link #release(Object)} or
     * {@link #invalidate(Object)} when the returned {@code Optional} is non-empty.
     *
     * @param timeout maximum time to wait
     * @param unit    time unit of {@code timeout}
     * @return an {@link Optional} containing the acquired resource, or empty on timeout
     * @throws InterruptedException  if the calling thread is interrupted while waiting
     * @throws IllegalStateException if the pool has been closed
     */
    Optional<T> acquire(long timeout, @Nonnull TimeUnit unit) throws InterruptedException;

    /**
     * Returns a previously acquired resource to the pool, making it available for future
     * acquisitions.
     * <p>
     * Implementations should silently ignore a {@code null} argument or a resource that does not
     * belong to this pool, so that callers can safely invoke this method in {@code finally} blocks
     * without additional null checks.
     *
     * @param resource the resource to return; ignored when {@code null}
     */
    void release(T resource);

    /**
     * Marks a previously acquired resource as invalid and permanently removes it from the pool.
     * <p>
     * Call this instead of {@link #release(Object)} when the resource is found to be broken,
     * expired, or otherwise unusable (for example, a dropped database connection or a closed HTTP
     * connection). Implementations may asynchronously create a replacement resource to maintain
     * the configured pool capacity.
     * <p>
     * Implementations should silently ignore a {@code null} argument or a resource that does not
     * belong to this pool.
     *
     * @param resource the resource to invalidate; ignored when {@code null}
     */
    void invalidate(T resource);

    /**
     * Returns the configured maximum number of resources this pool will manage.
     *
     * @return maximum pool capacity (always &gt; 0)
     */
    int capacity();

    /**
     * Returns the number of idle resources currently available for acquisition without blocking.
     *
     * @return number of idle resources (&gt;= 0)
     */
    int available();

    /**
     * Returns the number of resources currently on loan (acquired but not yet released or
     * invalidated).
     *
     * @return number of active resources (&gt;= 0)
     */
    int active();

    /**
     * Returns {@code true} if this pool has been closed.
     * <p>
     * Once closed, calls to {@link #acquire()} and {@link #acquire(long, TimeUnit)} throw
     * {@link IllegalStateException}.
     *
     * @return {@code true} if closed
     */
    boolean isClosed();

    /**
     * Closes this pool and releases all underlying idle resources.
     * <p>
     * After this call:
     * <ul>
     *   <li>Further calls to {@link #acquire()} throw {@link IllegalStateException}.</li>
     *   <li>Calls to {@link #release(Object)} and {@link #invalidate(Object)} may be silently
     *       ignored or may close the returned resource immediately, depending on the
     *       implementation.</li>
     * </ul>
     * Resources currently on loan at the time of the call are not forcibly reclaimed. The
     * behavior of unreturned resources after close is implementation-defined; implementations
     * should document whether they wait for active loans to complete or close resources
     * immediately.
     * <p>
     * This method is idempotent: repeated calls after the first have no effect.
     *
     * @throws Exception if closing underlying resources fails
     */
    @Override
    void close() throws Exception;
}
