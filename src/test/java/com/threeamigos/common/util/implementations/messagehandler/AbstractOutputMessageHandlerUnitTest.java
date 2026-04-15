package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.AbstractOutputMessageHandler;
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
        protected void handleInfoMessageImpl(String message) {
        }

        @Override
        protected void handleWarnMessageImpl(String message) {
        }

        @Override
        protected void handleErrorMessageImpl(String message) {
        }

        @Override
        protected void handleDebugMessageImpl(String message) {
        }

        @Override
        protected void handleTraceMessageImpl(String message) {
        }

        @Override
        protected void handleExceptionImpl(Exception exception) {
        }

        @Override
        protected void handleExceptionImpl(String message, Exception exception) {
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
