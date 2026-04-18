package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.ContextInfo;
import com.threeamigos.common.util.interfaces.messagehandler.LogFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.LogLevelEnum;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AbstractOutputMessageHandler unit tests")
@Tag("unit")
@Tag("messageHandler")
class AbstractOutputMessageHandlerUnitTest {

    private static final class ProbeOutputMessageHandler extends AbstractOutputMessageHandler {
        private ProbeOutputMessageHandler(boolean async, int queueCapacity) {
            initializeOutputDispatch(async, queueCapacity, false,
                    "ProbeOutputMessageHandler-async", "ProbeOutputMessageHandler-shutdown");
        }

        private void submit(Runnable runnable) {
            dispatch(runnable);
        }

        @Override
        protected void handleInfoMessageImpl(String message, ContextInfo context) {
        }

        @Override
        protected void handleWarnMessageImpl(String message, ContextInfo context) {
        }

        @Override
        protected void handleErrorMessageImpl(String message, ContextInfo context) {
        }

        @Override
        protected void handleFatalMessageImpl(String message, ContextInfo context) {
        }

        @Override
        protected void handleDebugMessageImpl(String message, ContextInfo context) {
        }

        @Override
        protected void handleTraceMessageImpl(String message, ContextInfo context) {
        }

        @Override
        protected void handleExceptionImpl(Exception exception, ContextInfo context) {
        }

        @Override
        protected void handleExceptionImpl(String message, Exception exception, ContextInfo context) {
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
                // Interrupted by close(), which routes execution to drain-loop finally.
                Thread.sleep(TimeUnit.SECONDS.toMillis(30));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(firstTaskStarted.await(1, TimeUnit.SECONDS));

        // This task stays queued while the first task is running.
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
        // First task runs in try-loop and interrupts current thread to exit while.
        queue.offer(() -> Thread.currentThread().interrupt());
        // Second task remains queued and is executed by finally-loop (line 65 true branch).
        queue.offer(drainedTaskExecuted::countDown);
        queueField.set(handler, queue);

        Method drainLoop = AbstractOutputMessageHandler.class.getDeclaredMethod("drainLoop");
        drainLoop.setAccessible(true);

        assertFalse(Thread.currentThread().isInterrupted());
        try {
            drainLoop.invoke(handler);
            assertEquals(0L, drainedTaskExecuted.getCount(),
                    "Finally-loop should execute queued task");
            assertTrue(Thread.currentThread().isInterrupted(),
                    "Interrupt flag should be set by the first task");
        } finally {
            // Avoid leaking interrupt state to other tests.
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
            // Simulate a dispatch() call that has already passed the closed check and is
            // about to offer its task. close() must not complete its drain before we offer.
            Thread closeThread = new Thread(() -> {
                closeStarted.countDown();
                handler.close();
            });
            closeThread.start();
            assertTrue(closeStarted.await(1, TimeUnit.SECONDS), "close() thread should start");
            // Give close() time to reach the synchronized (dispatchLock) barrier.
            Thread.sleep(50);

            // Offer the task while holding the lock (simulates dispatch() completing its offer).
            queue.offer(() -> taskExecuted.set(true));
            // Releasing the lock allows close() to proceed through its barrier and drain.
        }

        // Allow close() to finish draining and closing.
        Thread.sleep(200);

        assertTrue(taskExecuted.get(),
                "Task offered before close() passed its dispatchLock barrier must be executed during drain");
    }

    @Test
    @DisplayName("dispatch() should throw IllegalStateException after close() in both sync and async modes")
    void dispatchShouldThrowIllegalStateExceptionAfterClose() {
        ProbeOutputMessageHandler syncHandler = new ProbeOutputMessageHandler(false, 0);
        syncHandler.close();
        assertThrows(IllegalStateException.class, () -> syncHandler.submit(() -> {}));

        ProbeOutputMessageHandler asyncHandler = new ProbeOutputMessageHandler(true, 100);
        asyncHandler.close();
        assertThrows(IllegalStateException.class, () -> asyncHandler.submit(() -> {}));
    }

    @Test
    @DisplayName("setFormatter(null) should throw NullPointerException")
    void setFormatterNullThrowsNpe() {
        ProbeOutputMessageHandler handler = new ProbeOutputMessageHandler(false, 0);
        assertThrows(NullPointerException.class, () -> handler.setFormatter(null));
    }

    @Test
    @DisplayName("setFormatter with a custom formatter should route output through it")
    void setFormatterCustomFormatterIsUsed() {
        ProbeOutputMessageHandler handler = new ProbeOutputMessageHandler(false, 0);
        LogFormatter custom = new LogFormatter() {
            @Nonnull
            @Override
            public String format(@Nonnull LogLevelEnum level, @Nonnull String message, @Nonnull ContextInfo context) {
                return "CUSTOM:" + level + ":" + message;
            }
            @Nonnull
            @Override
            public String formatException(@Nonnull Exception exception, @Nonnull ContextInfo contextInfo) {
                return "CUSTOM:EXCEP:" + exception.getMessage();
            }
            @Nonnull
            @Override
            public String formatException(@Nonnull String prefix, @Nonnull Exception exception, @Nonnull ContextInfo contextInfo) {
                return "CUSTOM:EXCEP:" + prefix + ":" + exception.getMessage();
            }
        };
        handler.setFormatter(custom);
        // Verify getFormatter() returns the custom one
        assertEquals("CUSTOM:INFO:test", handler.getFormatter().format(LogLevelEnum.INFO, "test", new ContextInfoImpl()));
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
}
