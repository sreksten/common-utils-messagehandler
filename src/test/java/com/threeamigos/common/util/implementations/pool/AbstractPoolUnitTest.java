package com.threeamigos.common.util.implementations.pool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AbstractPool unit tests")
@Tag("unit")
@Tag("pool")
class AbstractPoolUnitTest {

    // -------------------------------------------------------------------------
    // Minimal concrete subclass for testing
    // -------------------------------------------------------------------------

    private static class TestPool extends AbstractPool<String> {

        private final AtomicInteger createCount = new AtomicInteger(0);
        final List<String> destroyed = new ArrayList<>();
        volatile boolean createShouldThrow = false;
        volatile boolean destroyShouldThrow = false;
        volatile boolean isValidResult = true;

        TestPool(final int capacity) {
            super(capacity);
        }

        @Override
        protected String createResource() throws Exception {
            if (createShouldThrow) {
                throw new Exception("create failed");
            }
            return "resource-" + createCount.incrementAndGet();
        }

        @Override
        protected void destroyResource(final String resource) throws Exception {
            if (destroyShouldThrow) {
                throw new Exception("destroy failed");
            }
            destroyed.add(resource);
        }

        @Override
        protected boolean isValid(final String resource) {
            return isValidResult;
        }
    }

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Constructor should reject zero capacity")
    void constructor_rejectsZeroCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new TestPool(0));
    }

    @Test
    @DisplayName("Constructor should reject negative capacity")
    void constructor_rejectsNegativeCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new TestPool(-1));
    }

    // -------------------------------------------------------------------------
    // capacity() / available() / active()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("capacity() should return configured value")
    void capacity_returnsConfiguredValue() {
        TestPool pool = new TestPool(3);
        assertEquals(3, pool.capacity());
    }

    @Test
    @DisplayName("available() should reflect idle resource count")
    void available_reflectsIdleResourceCount() throws Exception {
        TestPool pool = new TestPool(2);
        assertEquals(0, pool.available());

        String r1 = pool.acquire();
        String r2 = pool.acquire();
        assertEquals(0, pool.available());

        pool.release(r1);
        assertEquals(1, pool.available());

        pool.release(r2);
        assertEquals(2, pool.available());
    }

    @Test
    @DisplayName("active() should reflect borrowed resource count")
    void active_reflectsBorrowedResourceCount() throws Exception {
        TestPool pool = new TestPool(2);
        assertEquals(0, pool.active());

        String r1 = pool.acquire();
        assertEquals(1, pool.active());

        String r2 = pool.acquire();
        assertEquals(2, pool.active());

        pool.release(r1);
        assertEquals(1, pool.active());

        pool.release(r2);
        assertEquals(0, pool.active());
    }

    // -------------------------------------------------------------------------
    // acquire() — basic happy paths
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("acquire() should create resources lazily")
    void acquire_createsResourceLazily() throws Exception {
        TestPool pool = new TestPool(2);
        String r1 = pool.acquire();
        String r2 = pool.acquire();

        assertNotNull(r1);
        assertNotNull(r2);
        assertEquals(2, pool.active());
    }

    @Test
    @DisplayName("acquire() should reuse idle resources")
    void acquire_reusesIdleResource() throws Exception {
        TestPool pool = new TestPool(1);
        String r1 = pool.acquire();
        pool.release(r1);

        String r2 = pool.acquire();
        assertEquals(r1, r2, "Should reuse the released resource");
    }

    // -------------------------------------------------------------------------
    // acquire() — error paths
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("acquire() should throw IllegalStateException when pool is closed")
    void acquire_throwsWhenClosed() throws Exception {
        TestPool pool = new TestPool(1);
        pool.close();
        assertThrows(IllegalStateException.class, pool::acquire);
    }

    @Test
    @DisplayName("acquire() should throw IllegalStateException when pool is closed after permit acquired")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void acquire_throwsIllegalStateException_whenClosedBetweenChecks() throws Exception {
        TestPool pool = new TestPool(1);
        String resource = pool.acquire();  // exhaust permits

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                pool.acquire();  // will block on permits.acquire()
            } catch (final IllegalStateException e) {
                thrown.set(e);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-acquire-thread");
        t.start();

        // Let the thread reach permits.acquire() and block
        Thread.sleep(50);

        // Close the pool (sets closed=true), then release the permit so the blocked
        // thread can proceed past permits.acquire() and hit the second checkNotClosed()
        pool.close();
        pool.release(resource);   // destroyed immediately (pool closed) + releases permit

        t.join(2000);
        assertNotNull(thrown.get());
        assertFalse(t.isAlive(), "Thread should have finished");
    }

    @Test
    @DisplayName("acquire() should propagate InterruptedException")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void acquire_propagatesInterruptedException() throws Exception {
        TestPool pool = new TestPool(1);
        pool.acquire();  // exhaust permits

        AtomicBoolean interrupted = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            started.countDown();
            try {
                pool.acquire();
            } catch (final InterruptedException e) {
                interrupted.set(true);
            }
        }, "test-interrupted-thread");
        t.start();
        started.await();
        Thread.sleep(50);

        t.interrupt();
        t.join(2000);
        assertTrue(interrupted.get());
    }

    @Test
    @DisplayName("acquire() should throw IllegalStateException when createResource() fails")
    void acquire_throwsWhenCreateResourceFails() throws Exception {
        TestPool pool = new TestPool(1);
        pool.createShouldThrow = true;

        IllegalStateException ex = assertThrows(IllegalStateException.class, pool::acquire);
        assertTrue(ex.getMessage().contains("Failed to create pool resource"));
        // Permit must be released even on failure
        assertEquals(0, pool.active());
    }

    // -------------------------------------------------------------------------
    // acquire(timeout, unit)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("acquire(timeout) should return empty Optional on timeout")
    void acquireWithTimeout_returnsEmptyOnTimeout() throws Exception {
        TestPool pool = new TestPool(1);
        pool.acquire();  // exhaust permits

        Optional<String> result = pool.acquire(50, TimeUnit.MILLISECONDS);
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("acquire(timeout) should return resource when available before timeout")
    void acquireWithTimeout_returnsResourceBeforeTimeout() throws Exception {
        TestPool pool = new TestPool(1);
        Optional<String> result = pool.acquire(1, TimeUnit.SECONDS);

        assertTrue(result.isPresent());
        assertEquals(1, pool.active());
    }

    @Test
    @DisplayName("acquire(timeout) should throw IllegalStateException when pool is closed")
    void acquireWithTimeout_throwsWhenClosed() throws Exception {
        TestPool pool = new TestPool(1);
        pool.close();
        assertThrows(IllegalStateException.class, () -> pool.acquire(1, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("acquire(timeout) should throw IllegalStateException when closed after permit acquired")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void acquireWithTimeout_throwsIllegalStateException_whenClosedBetweenChecks() throws Exception {
        TestPool pool = new TestPool(1);
        String resource = pool.acquire();  // exhaust permits

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                pool.acquire(10, TimeUnit.SECONDS);  // will block on tryAcquire
            } catch (final IllegalStateException e) {
                thrown.set(e);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-timeout-acquire-thread");
        t.start();

        Thread.sleep(50);

        pool.close();
        pool.release(resource);  // releases permit so blocked thread can proceed

        t.join(2000);
        assertNotNull(thrown.get());
        assertFalse(t.isAlive());
    }

    @Test
    @DisplayName("acquire(timeout) should throw IllegalStateException when createResource() fails")
    void acquireWithTimeout_throwsWhenCreateResourceFails() throws Exception {
        TestPool pool = new TestPool(1);
        pool.createShouldThrow = true;

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> pool.acquire(1, TimeUnit.SECONDS));
        assertTrue(ex.getMessage().contains("Failed to create pool resource"));
        // Permit must be released even on failure
        assertEquals(0, pool.active());
    }

    @Test
    @DisplayName("acquire(timeout) should propagate InterruptedException")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void acquireWithTimeout_propagatesInterruptedException() throws Exception {
        TestPool pool = new TestPool(1);
        pool.acquire();  // exhaust permits

        AtomicBoolean interrupted = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            started.countDown();
            try {
                pool.acquire(10, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
                interrupted.set(true);
            }
        }, "test-timeout-interrupted-thread");
        t.start();
        started.await();
        Thread.sleep(50);

        t.interrupt();
        t.join(2000);
        assertTrue(interrupted.get());
    }

    // -------------------------------------------------------------------------
    // release()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("release(null) should be a no-op")
    void release_nullIsNoOp() throws Exception {
        TestPool pool = new TestPool(1);
        pool.release(null);  // must not throw
        assertEquals(0, pool.active());
        assertEquals(0, pool.available());
    }

    @Test
    @DisplayName("release() should return valid resource to idle queue")
    void release_validResourceReturnsToIdleQueue() throws Exception {
        TestPool pool = new TestPool(1);
        String resource = pool.acquire();

        pool.release(resource);

        assertEquals(0, pool.active());
        assertEquals(1, pool.available());
        assertTrue(pool.destroyed.isEmpty());
    }

    @Test
    @DisplayName("release() should destroy resource when isValid() returns false")
    void release_invalidResourceIsDestroyed() throws Exception {
        TestPool pool = new TestPool(1);
        String resource = pool.acquire();
        pool.isValidResult = false;

        pool.release(resource);

        assertEquals(0, pool.active());
        assertEquals(0, pool.available());
        assertTrue(pool.destroyed.contains(resource));
    }

    @Test
    @DisplayName("release() should destroy resource when pool is closed")
    void release_whenClosedDestroysResource() throws Exception {
        TestPool pool = new TestPool(1);
        String resource = pool.acquire();
        pool.close();

        pool.release(resource);

        assertTrue(pool.destroyed.contains(resource));
    }

    @Test
    @DisplayName("release() should destroy surplus resource when idle queue is full")
    @SuppressWarnings("unchecked")
    void release_whenQueueFull_destroysResource() throws Exception {
        TestPool pool = new TestPool(1);
        String acquired = pool.acquire();  // queue empty, permits=0

        // Inject a resource directly into the idle queue (fill it to capacity)
        Field field = AbstractPool.class.getDeclaredField("idleResources");
        field.setAccessible(true);
        LinkedBlockingQueue<String> queue = (LinkedBlockingQueue<String>) field.get(pool);
        queue.put("injected-resource");
        // Queue is now full (capacity=1). Releasing acquired should trigger the full-queue branch.

        pool.release(acquired);

        assertTrue(pool.destroyed.contains(acquired), "Surplus resource must be destroyed");
    }

    @Test
    @DisplayName("release() should silently ignore destroyResource() failure")
    void release_destroyFailureIsSilentlyIgnored() throws Exception {
        TestPool pool = new TestPool(1);
        String resource = pool.acquire();
        pool.isValidResult = false;
        pool.destroyShouldThrow = true;

        // Must not propagate the exception from destroyResource()
        pool.release(resource);

        assertEquals(0, pool.active());
    }

    // -------------------------------------------------------------------------
    // invalidate()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("invalidate(null) should be a no-op")
    void invalidate_nullIsNoOp() throws Exception {
        TestPool pool = new TestPool(1);
        pool.invalidate(null);  // must not throw
        assertEquals(0, pool.active());
    }

    @Test
    @DisplayName("invalidate() should destroy resource and release permit")
    void invalidate_destroysResourceAndReleasesPermit() throws Exception {
        TestPool pool = new TestPool(1);
        String resource = pool.acquire();

        pool.invalidate(resource);

        assertEquals(0, pool.active());
        assertTrue(pool.destroyed.contains(resource));
    }

    @Test
    @DisplayName("invalidate() should silently ignore destroyResource() failure")
    void invalidate_destroyFailureIsSilentlyIgnored() throws Exception {
        TestPool pool = new TestPool(1);
        String resource = pool.acquire();
        pool.destroyShouldThrow = true;

        pool.invalidate(resource);  // must not propagate exception

        assertEquals(0, pool.active());
    }

    // -------------------------------------------------------------------------
    // borrowOrCreate() — stale idle resource drain
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("acquire() should drain stale idle resources and create a new one")
    void acquire_drainsStaleIdleResourcesAndCreatesNew() throws Exception {
        TestPool pool = new TestPool(2);
        String r1 = pool.acquire();
        pool.release(r1);  // r1 goes to idle queue

        // Mark all existing resources as invalid for the next acquire
        pool.isValidResult = false;

        // r1 is idle but isValid returns false → should be drained, new resource created
        String r2 = pool.acquire();

        assertNotNull(r2);
        assertTrue(pool.destroyed.contains(r1), "Stale resource must be destroyed");
        // r2 is a newly created resource, not r1
        assertFalse(r1.equals(r2), "Must create a new resource when idle one is stale");
    }

    // -------------------------------------------------------------------------
    // isClosed() / close()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("isClosed() should return false before close and true after")
    void isClosed_falseBeforeClose_trueAfterClose() throws Exception {
        TestPool pool = new TestPool(1);
        assertFalse(pool.isClosed());

        pool.close();
        assertTrue(pool.isClosed());
    }

    @Test
    @DisplayName("close() should be idempotent")
    void close_isIdempotent() throws Exception {
        TestPool pool = new TestPool(1);
        pool.close();
        pool.close();  // second call must not throw or double-destroy
        assertTrue(pool.isClosed());
    }

    @Test
    @DisplayName("close() should destroy all idle resources")
    void close_destroysAllIdleResources() throws Exception {
        TestPool pool = new TestPool(3);
        String r1 = pool.acquire();
        String r2 = pool.acquire();
        String r3 = pool.acquire();
        pool.release(r1);
        pool.release(r2);
        pool.release(r3);
        assertEquals(3, pool.available());

        pool.close();

        assertTrue(pool.destroyed.containsAll(Arrays.asList(r1, r2, r3)));
        assertEquals(0, pool.available());
    }

    @Test
    @DisplayName("close() should rethrow the first destroyResource() failure after draining all")
    void close_rethrowsFirstFailureAfterDrainingAll() throws Exception {
        final List<String> destroyAttempts = new ArrayList<>();

        TestPool pool = new TestPool(2) {
            @Override
            protected void destroyResource(final String resource) throws Exception {
                destroyAttempts.add(resource);
                throw new Exception("destroy failed: " + resource);
            }
        };
        String r1 = pool.acquire();
        String r2 = pool.acquire();
        pool.release(r1);
        pool.release(r2);

        Exception ex = assertThrows(Exception.class, pool::close);
        assertNotNull(ex.getMessage());
        // Both resources must have been given a chance to close
        assertEquals(2, destroyAttempts.size());
    }

    @Test
    @DisplayName("close() should not throw when all destroyResource() calls succeed")
    void close_noExceptionWhenDestroySucceeds() throws Exception {
        TestPool pool = new TestPool(2);
        String r1 = pool.acquire();
        String r2 = pool.acquire();
        pool.release(r1);
        pool.release(r2);

        pool.close();  // must not throw
        assertEquals(2, pool.destroyed.size());
    }

    // -------------------------------------------------------------------------
    // isValid() default hook
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Default isValid() should return true so resources are reused")
    void defaultIsValid_returnsTrueAndResourceIsReused() throws Exception {
        // Use a pool that does NOT override isValid() → default returns true
        AbstractPool<String> pool = new AbstractPool<String>(1) {
            private int count = 0;

            @Override
            protected String createResource() {
                return "r" + (++count);
            }

            @Override
            protected void destroyResource(final String resource) {
                // no-op
            }
        };

        String r1 = pool.acquire();
        pool.release(r1);
        String r2 = pool.acquire();

        assertEquals(r1, r2, "Default isValid=true must reuse idle resource");
        pool.close();
    }

    // -------------------------------------------------------------------------
    // Concurrency: acquire blocks until release frees a permit
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("acquire() should block when pool is exhausted and unblock on release")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void acquire_blocksUntilRelease() throws Exception {
        TestPool pool = new TestPool(1);
        String r1 = pool.acquire();

        CountDownLatch acquired = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                result.set(pool.acquire());
                acquired.countDown();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-blocking-acquire");
        t.start();

        // Give t a moment to block
        Thread.sleep(50);
        assertEquals(0, pool.available());

        pool.release(r1);  // should unblock t

        assertTrue(acquired.await(2, TimeUnit.SECONDS), "Blocked acquire should unblock after release");
        assertEquals(r1, result.get());
    }

}
