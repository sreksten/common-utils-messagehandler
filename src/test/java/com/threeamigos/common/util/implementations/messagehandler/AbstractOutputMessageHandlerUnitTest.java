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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
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

        @Override
        public void handleMessage(@Nonnull SeverityNumber level, @Nonnull String message) {
        }

        @Override
        public void handleThrowable(@Nonnull String message, @Nonnull Throwable throwable) {
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

        ProbeOutputMessageHandler asyncHandler = new ProbeOutputMessageHandler(true, 100);
        asyncHandler.close();
        assertThrows(IllegalStateException.class, () -> asyncHandler.submit(() -> {
        }));
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
}
