package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordFactoryImpl;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.RawJsonRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AbstractOutputMessageHandler unit tests")
@Tag("unit")
@Tag("messageHandler")
class AbstractOutputMessageHandlerUnitTest {

    private static final LogRecordFactory FACTORY = new LogRecordFactoryImpl();
    private static final LogRecordFormatter FORMATTER = new RawJsonRecordFormatter();

    private static final class ProbeOutputMessageHandler extends AbstractOutputMessageHandler {
        private ProbeOutputMessageHandler(final boolean async, final int queueCapacity) {
            super(FACTORY, FORMATTER);
            initializeOutputDispatch(async, queueCapacity, false,
                    "ProbeOutputMessageHandler-async", "ProbeOutputMessageHandler-shutdown");
        }

        private void submit(final Runnable runnable) {
            dispatch(runnable);
        }

        private void markSuccess() {
            recordOutputSuccess();
        }

        private void markFailure() {
            recordOutputFailure();
        }

        private void markDropped() {
            recordDroppedOutput();
        }

        private void markRetryAttemptMetric() {
            recordRetryAttempt();
        }

        private void markRetrySuccessMetric() {
            recordRetrySuccess();
        }

        private void markRetryFailureMetric() {
            recordRetryFailure();
        }

        private HandlerHealthMetrics healthMetrics() {
            return getHandlerHealthMetrics();
        }

        @Override
        public void handleMessage(@Nonnull SeverityNumber level, @Nonnull String message) {
        }

        @Override
        protected void handleExceptionInternal(@Nonnull String message, @Nonnull Throwable throwable) {
        }
    }

    @Test
    @DisplayName("Should drain queued tasks from finally block after interrupt")
    void shouldDrainQueuedTasksFromFinallyBlockAfterInterrupt() throws Exception {
        ProbeOutputMessageHandler handler = new ProbeOutputMessageHandler(true, 10);
        CountDownLatch firstTaskStarted = new CountDownLatch(1);
        CountDownLatch queuedTaskExecuted = new CountDownLatch(1);

        handler.submit(() -> {
            firstTaskStarted.countDown();
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(30));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(firstTaskStarted.await(1, TimeUnit.SECONDS));

        handler.submit(queuedTaskExecuted::countDown);

        handler.close();

        assertTrue(queuedTaskExecuted.await(1, TimeUnit.SECONDS),
                "Queued task should be executed by the finally drain loop");
    }

    @Test
    @DisplayName("Drain loop should deterministically cover both poll outcomes")
    void drainLoopShouldDeterministicallyCoverBothPollOutcomes() throws Exception {
        ProbeOutputMessageHandler handler = new ProbeOutputMessageHandler(false, 0);
        CountDownLatch drainedTaskExecuted = new CountDownLatch(1);

        Field queueField = AbstractOutputMessageHandler.class.getDeclaredField("queue");
        queueField.setAccessible(true);
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        queue.offer(() -> Thread.currentThread().interrupt());
        queue.offer(drainedTaskExecuted::countDown);
        queueField.set(handler, queue);

        Method drainLoop = AbstractOutputMessageHandler.class.getDeclaredMethod("drainLoop");
        drainLoop.setAccessible(true);

        assertFalse(Thread.currentThread().isInterrupted());
        try {
            drainLoop.invoke(handler);
            assertEquals(0L, drainedTaskExecuted.getCount());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("drain loop should continue when a queued task throws runtime exception")
    void drainLoopShouldContinueWhenQueuedTaskThrowsRuntimeException() throws Exception {
        ProbeOutputMessageHandler handler = new ProbeOutputMessageHandler(false, 0);
        List<String> trapped = new ArrayList<String>();
        InnerErrorMessageHandler.setGlobalConsumer(trapped::add);
        try {
            CountDownLatch drainedTaskExecuted = new CountDownLatch(1);

            Field queueField = AbstractOutputMessageHandler.class.getDeclaredField("queue");
            queueField.setAccessible(true);
            BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
            queue.offer(() -> {
                throw new IllegalStateException("task-boom");
            });
            queue.offer(drainedTaskExecuted::countDown);
            queue.offer(() -> Thread.currentThread().interrupt());
            queueField.set(handler, queue);

            Method drainLoop = AbstractOutputMessageHandler.class.getDeclaredMethod("drainLoop");
            drainLoop.setAccessible(true);

            assertDoesNotThrow(() -> drainLoop.invoke(handler));
            assertEquals(0L, drainedTaskExecuted.getCount());
            assertFalse(trapped.isEmpty());
            assertTrue(trapped.get(0).contains("task-boom"));
        } finally {
            InnerErrorMessageHandler.resetGlobalConsumer();
        }
    }

    @Test
    @DisplayName("Task offered to queue before close() acquires dispatchLock must not be silently dropped")
    void taskOfferedBeforeCloseAcquiresDispatchLockMustNotBeSilentlyDropped() throws Exception {
        ProbeOutputMessageHandler handler = new ProbeOutputMessageHandler(true, 100);

        Field dispatchLockField = AbstractOutputMessageHandler.class.getDeclaredField("dispatchLock");
        dispatchLockField.setAccessible(true);
        Object dispatchLock = dispatchLockField.get(handler);

        Field queueField = AbstractOutputMessageHandler.class.getDeclaredField("queue");
        queueField.setAccessible(true);
        @SuppressWarnings("unchecked")
        BlockingQueue<Runnable> queue = (BlockingQueue<Runnable>) queueField.get(handler);

        AtomicBoolean taskExecuted = new AtomicBoolean(false);
        CountDownLatch closeStarted = new CountDownLatch(1);

        synchronized (dispatchLock) {
            Thread closeThread = new Thread(() -> {
                closeStarted.countDown();
                handler.close();
            });
            closeThread.start();
            assertTrue(closeStarted.await(1, TimeUnit.SECONDS));
            Thread.sleep(50);

            queue.offer(() -> taskExecuted.set(true));
        }

        Thread.sleep(200);

        assertTrue(taskExecuted.get(),
                "Task offered before close() passed its dispatchLock barrier must be executed during drain");
    }

    @Test
    @DisplayName("dispatch() should throw IllegalStateException after close() in both sync and async modes")
    void dispatchShouldThrowIllegalStateExceptionAfterClose() {
        ProbeOutputMessageHandler syncHandler = new ProbeOutputMessageHandler(false, 0);
        syncHandler.close();
        assertThrows(IllegalStateException.class, () -> syncHandler.submit(() -> {
        }));
        assertEquals(1L, syncHandler.healthMetrics().getDroppedOperations());

        ProbeOutputMessageHandler asyncHandler = new ProbeOutputMessageHandler(true, 100);
        asyncHandler.close();
        assertThrows(IllegalStateException.class, () -> asyncHandler.submit(() -> {
        }));
        assertEquals(1L, asyncHandler.healthMetrics().getDroppedOperations());
    }

    @Test
    @DisplayName("setLogRecordFormatter(null) should throw NullPointerException")
    void setFormatterNullThrowsNpe() {
        ProbeOutputMessageHandler handler = new ProbeOutputMessageHandler(false, 0);
        assertThrows(NullPointerException.class, () -> handler.setLogRecordFormatter(null));
    }

    @Test
    @DisplayName("setLogRecordFormatter with a custom formatter should route output through it")
    void setFormatterCustomFormatterIsUsed() {
        ProbeOutputMessageHandler handler = new ProbeOutputMessageHandler(false, 0);
        LogRecordFormatter custom = logRecord -> "CUSTOM:" + logRecord.getSeverityText() + ":"
                + (logRecord.getBody() == null ? "" : logRecord.getBody().asString());

        handler.setLogRecordFormatter(custom);

        LogRecord record = FACTORY.create(SeverityNumber.INFO, "test");
        assertEquals("CUSTOM:INFO:test", handler.getLogRecordFormatter().format(record));
    }

    @Test
    @DisplayName("logRecordFormatter field should be volatile for runtime formatter swaps")
    void logRecordFormatterFieldShouldBeVolatileForRuntimeFormatterSwaps() throws Exception {
        Field formatterField = AbstractOutputMessageHandler.class.getDeclaredField("logRecordFormatter");
        assertTrue(Modifier.isVolatile(formatterField.getModifiers()));
    }

    @Test
    @DisplayName("Should request shutdown again when awaitTermination times out")
    void shouldRequestShutdownAgainWhenAwaitTerminationTimesOut() throws Exception {
        ProbeOutputMessageHandler handler = new ProbeOutputMessageHandler(false, 0);
        ExecutorService worker = mock(ExecutorService.class);
        when(worker.awaitTermination(5, TimeUnit.SECONDS)).thenReturn(false);

        Field workerField = AbstractOutputMessageHandler.class.getDeclaredField("worker");
        workerField.setAccessible(true);
        workerField.set(handler, worker);

        handler.close();

        verify(worker, times(2)).shutdownNow();
        verify(worker, times(1)).awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("drainQueueInCallerThread should no-op on null queue and drain all queued tasks")
    void drainQueueInCallerThreadShouldNoOpOnNullQueueAndDrainAllQueuedTasks() throws Exception {
        ProbeOutputMessageHandler handler = new ProbeOutputMessageHandler(false, 0);

        Method drainQueueInCallerThread = AbstractOutputMessageHandler.class
                .getDeclaredMethod("drainQueueInCallerThread");
        drainQueueInCallerThread.setAccessible(true);

        assertDoesNotThrow(() -> drainQueueInCallerThread.invoke(handler));

        Field queueField = AbstractOutputMessageHandler.class.getDeclaredField("queue");
        queueField.setAccessible(true);
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        AtomicBoolean firstExecuted = new AtomicBoolean(false);
        AtomicBoolean secondExecuted = new AtomicBoolean(false);
        queue.offer(() -> firstExecuted.set(true));
        queue.offer(() -> secondExecuted.set(true));
        queueField.set(handler, queue);

        drainQueueInCallerThread.invoke(handler);

        assertTrue(firstExecuted.get(), "First queued task should be executed");
        assertTrue(secondExecuted.get(), "Second queued task should be executed");
        assertTrue(queue.isEmpty(), "Queue should be fully drained");
    }

    @Test
    @DisplayName("health metrics should track success, failure, recovery and closed state")
    void healthMetricsShouldTrackSuccessFailureRecoveryAndClosedState() {
        ProbeOutputMessageHandler handler = new ProbeOutputMessageHandler(false, 0);
        assertTrue(handler.isHealthy());

        handler.markSuccess();
        AbstractOutputMessageHandler.HandlerHealthMetrics afterSuccess = handler.healthMetrics();
        assertEquals(1L, afterSuccess.getSuccessfulOperations());
        assertEquals(0L, afterSuccess.getFailedOperations());
        assertEquals(0L, afterSuccess.getConsecutiveFailures());
        assertTrue(afterSuccess.getLastSuccessTimestampMillis() > 0L);
        assertTrue(afterSuccess.isHealthy());

        handler.markFailure();
        AbstractOutputMessageHandler.HandlerHealthMetrics afterFailure = handler.healthMetrics();
        assertEquals(1L, afterFailure.getSuccessfulOperations());
        assertEquals(1L, afterFailure.getFailedOperations());
        assertEquals(2L, afterFailure.getTotalOperations());
        assertEquals(1L, afterFailure.getConsecutiveFailures());
        assertTrue(afterFailure.getLastFailureTimestampMillis() > 0L);
        assertFalse(afterFailure.isHealthy());

        handler.markSuccess();
        AbstractOutputMessageHandler.HandlerHealthMetrics afterRecovery = handler.healthMetrics();
        assertEquals(2L, afterRecovery.getSuccessfulOperations());
        assertEquals(1L, afterRecovery.getFailedOperations());
        assertEquals(0L, afterRecovery.getConsecutiveFailures());
        assertTrue(afterRecovery.isHealthy());

        handler.close();
        AbstractOutputMessageHandler.HandlerHealthMetrics afterClose = handler.healthMetrics();
        assertTrue(afterClose.isClosed());
        assertFalse(handler.isHealthy());
    }

    @Test
    @DisplayName("saturation telemetry should track bounded queue pressure and sync fallback")
    void saturationTelemetryShouldTrackBoundedQueuePressureAndSyncFallback() throws Exception {
        ProbeOutputMessageHandler handler = new ProbeOutputMessageHandler(true, 1);
        CountDownLatch blockingStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocking = new CountDownLatch(1);
        AtomicBoolean fallbackExecutedOnCaller = new AtomicBoolean(false);
        try {
            handler.submit(() -> {
                blockingStarted.countDown();
                try {
                    releaseBlocking.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(blockingStarted.await(1, TimeUnit.SECONDS));

            // This one should enter the queue.
            handler.submit(() -> {
            });
            // Queue is full now (capacity=1 + one running task): this should run synchronously.
            handler.submit(() -> fallbackExecutedOnCaller.set(true));

            AbstractOutputMessageHandler.HandlerHealthMetrics metrics = handler.healthMetrics();
            assertTrue(fallbackExecutedOnCaller.get());
            assertTrue(metrics.isAsync());
            assertTrue(metrics.isBoundedQueue());
            assertEquals(1, metrics.getConfiguredQueueCapacity());
            assertTrue(metrics.getDispatchAttempts() >= 3L);
            assertTrue(metrics.getAsyncEnqueuedOperations() >= 1L);
            assertTrue(metrics.getSynchronousFallbackOperations() >= 1L);
            assertTrue(metrics.getQueueSaturationEvents() >= 1L);
            assertTrue(metrics.getMaxObservedQueueSize() >= 1L);
            assertTrue(metrics.getQueueSaturationRatio() > 0.0d);
        } finally {
            releaseBlocking.countDown();
            handler.close();
        }
    }

    @Test
    @DisplayName("retry metrics should expose attempts, successes and failures")
    void retryMetricsShouldExposeAttemptsSuccessesAndFailures() {
        ProbeOutputMessageHandler handler = new ProbeOutputMessageHandler(false, 0);

        handler.markRetryAttemptMetric();
        handler.markRetryAttemptMetric();
        handler.markRetrySuccessMetric();
        handler.markRetryFailureMetric();
        handler.markDropped();

        AbstractOutputMessageHandler.HandlerHealthMetrics metrics = handler.healthMetrics();
        assertEquals(2L, metrics.getRetryAttempts());
        assertEquals(1L, metrics.getRetrySuccesses());
        assertEquals(1L, metrics.getRetryFailures());
        assertEquals(1L, metrics.getDroppedOperations());
        assertFalse(metrics.isBoundedQueue());
        assertEquals(0, metrics.getConfiguredQueueCapacity());
        assertEquals(0.0d, metrics.getQueueSaturationRatio());

        handler.close();
    }
}
