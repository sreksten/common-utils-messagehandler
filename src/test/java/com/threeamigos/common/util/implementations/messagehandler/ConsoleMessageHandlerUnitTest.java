package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.AbstractOutputMessageHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.ArgumentCaptor;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

@DisplayName("ConsoleMessageHandler unit test")
@Tag("unit")
@Tag("messageHandler")
@Execution(ExecutionMode.SAME_THREAD)
class ConsoleMessageHandlerUnitTest {

    private static class ThrowingAwaitTerminationConsoleMessageHandler extends ConsoleMessageHandler {
        private ThrowingAwaitTerminationConsoleMessageHandler() {
            super(true, 1, false);
        }

        @Override
        protected void awaitTermination() throws InterruptedException {
            throw new InterruptedException("forced");
        }
    }

    private static class ThrowingRemoveHookConsoleMessageHandler extends ConsoleMessageHandler {
        private ThrowingRemoveHookConsoleMessageHandler() {
            super(true, 1, true);
        }

        @Override
        protected void removeShutdownHook(Thread hook) {
            throw new IllegalStateException("forced");
        }
    }

    private static PrintStream systemOut;
    private static PrintStream systemErr;

    private PrintStream out;
    private PrintStream err;

    @BeforeAll
    static void setupBeforeAll() {
        systemOut = System.out;
        systemErr = System.err;
    }

    @BeforeEach
    void setup() {
        out = mock(PrintStream.class);
        System.setOut(out);
        err = mock(PrintStream.class);
        System.setErr(err);
    }

    @AfterAll
    static void cleanup() {
        System.setOut(systemOut);
        System.setErr(systemErr);
    }

    @Test
    @DisplayName("Should throw an exception if a null info message is provided")
    void shouldThrowAnExceptionIfANullInfoMessageIsProvided() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler();
        // When
        String infoMessage = null;
        // Then
        assertThrows(NullPointerException.class, () -> sut.handleInfoMessage(infoMessage));
    }

    @Test
    @DisplayName("Should handle info message")
    void shouldHandleInfoMessage() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler();
        // When
        sut.handleInfoMessage("INFO");
        // Then
        assertFormattedLine(out, "INFO ", "INFO");
    }

    @Test
    @DisplayName("Should throw an exception if a null warn message is provided")
    void shouldThrowAnExceptionIfANullWarnMessageIsProvided() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler();
        // When
        String warnMessage = null;
        // Then
        assertThrows(NullPointerException.class, () -> sut.handleWarnMessage(warnMessage));
    }

    @Test
    @DisplayName("Should handle warn message")
    void shouldHandleWarnMessage() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler();
        // When
        sut.handleWarnMessage("WARN");
        // Then
        assertFormattedLine(out, "WARN ", "WARN");
    }

    @Test
    @DisplayName("Should throw an exception if a null error message is provided")
    void shouldThrowAnExceptionIfANullErrorMessageIsProvided() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler();
        // When
        String errorMessage = null;
        // Then
        assertThrows(NullPointerException.class, () -> sut.handleErrorMessage(errorMessage));
    }

    @Test
    @DisplayName("Should handle error message")
    void shouldHandleErrorMessage() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler();
        // When
        sut.handleErrorMessage("ERROR");
        // Then
        assertFormattedLine(err, "ERROR", "ERROR");
    }

    @Test
    @DisplayName("Should throw an exception if a null debug message is provided")
    void shouldThrowAnExceptionIfANullDebugMessageIsProvided() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler();
        // When
        String debugMessage = null;
        // Then
        assertThrows(NullPointerException.class, () -> sut.handleDebugMessage(debugMessage));
    }

    @Test
    @DisplayName("Should handle debug message")
    void shouldHandleDebugMessage() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler();
        // When
        sut.handleDebugMessage("DEBUG");
        // Then
        assertFormattedLine(out, "DEBUG", "DEBUG");
    }

    @Test
    @DisplayName("Should throw an exception if a null trace message is provided")
    void shouldThrowAnExceptionIfANullTraceMessageIsProvided() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler();
        // When
        String traceMessage = null;
        // Then
        assertThrows(NullPointerException.class, () -> sut.handleTraceMessage(traceMessage));
    }

    @Test
    @DisplayName("Should handle trace message")
    void shouldHandleTraceMessage() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler();
        // When
        sut.handleTraceMessage("TRACE");
        // Then
        assertFormattedLine(out, "TRACE", "TRACE");
    }

    @Test
    @DisplayName("Should throw an exception if a null exception is provided")
    void shouldThrowAnExceptionIfANullExceptionIsProvided() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler();
        // When
        Exception exception = null;
        // Then
        assertThrows(NullPointerException.class, () -> sut.handleException(exception));
    }

    @Test
    @DisplayName("Should handle exception")
    void shouldHandleExceptionMessage() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler();
        Exception exception = mock(IllegalArgumentException.class);
        when(exception.getMessage()).thenReturn("Boom");
        // When
        sut.handleException(exception);
        // Then
        assertFormattedLine(err, "EXCEP", "Boom");
        verify(exception, times(1)).printStackTrace(err);
    }

    @Test
    @DisplayName("Should handle exception with custom message")
    void shouldHandleExceptionWithCustomMessage() {
        ConsoleMessageHandler sut = new ConsoleMessageHandler();
        Exception exception = mock(IllegalArgumentException.class);
        when(exception.getMessage()).thenReturn("Boom");

        sut.handleException("prefix", exception);

        ArgumentCaptor<String> captor = forClass(String.class);
        verify(err, times(1)).println(captor.capture());
        assertTrue(captor.getValue().contains("[EXCEP] prefix: Boom"));
        verify(exception, times(1)).printStackTrace(err);
    }

    @Test
    @DisplayName("Should run synchronously when queue is full")
    void shouldRunSynchronouslyWhenQueueIsFull() throws Exception {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        CountDownLatch firstPrintEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstPrint = new CountDownLatch(1);
        AtomicBoolean firstCall = new AtomicBoolean(true);
        PrintStream blockingOut = new PrintStream(outContent, true, StandardCharsets.UTF_8.name()) {
            @Override
            public void println(String x) {
                if (firstCall.compareAndSet(true, false)) {
                    firstPrintEntered.countDown();
                    try {
                        releaseFirstPrint.await(1, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
                super.println(x);
            }
        };
        System.setOut(blockingOut);
        try (ConsoleMessageHandler handler = new ConsoleMessageHandler(true, 1)) {
            handler.handleInfoMessage("first");
            assertTrue(firstPrintEntered.await(500, TimeUnit.MILLISECONDS));
            handler.handleInfoMessage("queued");
            handler.handleInfoMessage("sync");
            releaseFirstPrint.countDown();
            Thread.sleep(200);
            String output = outContent.toString(StandardCharsets.UTF_8.name());
            assertTrue(output.contains("first"));
            assertTrue(output.contains("queued"));
            assertTrue(output.contains("sync"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Close should drain pending queued tasks")
    void closeShouldDrainPendingQueuedTasks() throws Exception {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        CountDownLatch firstPrintEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstPrint = new CountDownLatch(1);
        AtomicBoolean firstCall = new AtomicBoolean(true);
        PrintStream blockingOut = new PrintStream(outContent, true, StandardCharsets.UTF_8.name()) {
            @Override
            public void println(String x) {
                if (firstCall.compareAndSet(true, false)) {
                    firstPrintEntered.countDown();
                    try {
                        releaseFirstPrint.await(1, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
                super.println(x);
            }
        };
        System.setOut(blockingOut);
        try (ConsoleMessageHandler handler = new ConsoleMessageHandler(true, 1)) {
            handler.handleInfoMessage("blocking");
            assertTrue(firstPrintEntered.await(500, TimeUnit.MILLISECONDS));
            handler.handleInfoMessage("queued-drain");
            handler.close();
            releaseFirstPrint.countDown();
            Thread.sleep(200);
            String output = outContent.toString(StandardCharsets.UTF_8.name());
            assertTrue(output.contains("queued-drain"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Close should keep interrupt flag when awaitTermination is interrupted")
    void closeShouldKeepInterruptFlagWhenAwaitTerminationIsInterrupted() {
        ThrowingAwaitTerminationConsoleMessageHandler handler = new ThrowingAwaitTerminationConsoleMessageHandler();
        assertFalse(Thread.currentThread().isInterrupted());
        try {
            handler.close();
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("Close should ignore IllegalStateException when removing shutdown hook")
    void closeShouldIgnoreIllegalStateExceptionWhenRemovingShutdownHook() {
        ThrowingRemoveHookConsoleMessageHandler handler = new ThrowingRemoveHookConsoleMessageHandler();
        assertDoesNotThrow(handler::close);
    }

    @Test
    @DisplayName("Should run synchronously when queue is full with supplier")
    void shouldRunSynchronouslyWhenQueueIsFullWithSupplier() throws Exception {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent, true, StandardCharsets.UTF_8.name()));
        try (ConsoleMessageHandler handler = new ConsoleMessageHandler(true, 1)) {
            CountDownLatch latch = new CountDownLatch(1);
            handler.handleInfoMessage(() -> {
                try {
                    latch.await(500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignored) {
                }
                return "background";
            });
            handler.handleInfoMessage("sync");
            latch.countDown();
            Thread.sleep(200);
            String output = outContent.toString(StandardCharsets.UTF_8.name());
            assertTrue(output.contains("sync"));
            assertTrue(output.contains("background"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Should register shutdown hook when requested")
    void shouldRegisterShutdownHook() throws Exception {
        ConsoleMessageHandler handler = new ConsoleMessageHandler(true, 1, true);
        Thread hook = getShutdownHook(handler);
        assertNotNull(hook);
        handler.close();
    }

    @Test
    @DisplayName("Should not register shutdown hook when not requested")
    void shouldNotRegisterShutdownHook() throws Exception {
        ConsoleMessageHandler handler = new ConsoleMessageHandler(true, 1, false);
        Thread hook = getShutdownHook(handler);
        assertNull(hook);
        handler.close();
    }

    @Test
    @DisplayName("Async with non-positive capacity should behave as unbounded")
    void asyncWithNonPositiveCapacityBehavesUnbounded() {
        assertDoesNotThrow(() -> {
            try (ConsoleMessageHandler handler = new ConsoleMessageHandler(true, 0, false)) {
                handler.handleInfoMessage("msg1");
                handler.handleInfoMessage("msg2");
                Thread.sleep(100);
            }
        });
    }

    @Test
    @DisplayName("Close on synchronous handler should be a no-op")
    void closeOnSynchronousHandlerShouldBeNoOp() {
        ConsoleMessageHandler handler = new ConsoleMessageHandler();
        assertDoesNotThrow(handler::close);
    }

    private Thread getShutdownHook(ConsoleMessageHandler handler) throws Exception {
        Field f = AbstractOutputMessageHandler.class.getDeclaredField("shutdownHook");
        f.setAccessible(true);
        return (Thread) f.get(handler);
    }

    private void assertFormattedLine(PrintStream stream, String level, String message) {
        ArgumentCaptor<String> captor = forClass(String.class);
        verify(stream, times(1)).println(captor.capture());
        String actual = captor.getValue();
        String regex = "^\\[[^\\]]+\\] \\[" + Pattern.quote(level) + "\\] " + Pattern.quote(message) + "$";
        assertTrue(actual.matches(regex), () -> "Unexpected message: " + actual);
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    @DisplayName("Should keep all info messages under high concurrent load")
    void shouldKeepAllInfoMessagesUnderHighConcurrentLoad() throws Exception {
        final int threadCount = 16;
        final int messagesPerThread = 2000;
        final int expectedMessages = threadCount * messagesPerThread;

        PrintStream originalOut = System.out;
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent, true, StandardCharsets.UTF_8.name()));
        try (ConsoleMessageHandler handler = new ConsoleMessageHandler(true, 2048, false)) {
            ExecutorService producerPool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                futures.add(producerPool.submit(() -> {
                    start.await();
                    for (int i = 0; i < messagesPerThread; i++) {
                        final int messageIndex = i;
                        handler.handleInfoMessage(() -> "stress-console-" + threadId + "-" + messageIndex);
                    }
                    return null;
                }));
            }

            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
            producerPool.shutdown();
            assertTrue(producerPool.awaitTermination(10, TimeUnit.SECONDS));
        } finally {
            System.setOut(originalOut);
        }

        String output = outContent.toString(StandardCharsets.UTF_8.name());
        long infoLines;
        try (BufferedReader reader = new BufferedReader(new StringReader(output))) {
            infoLines = reader.lines().filter(line -> line.contains("[INFO ]")).count();
        }

        assertEquals(expectedMessages, infoLines, "Some console info messages were lost under stress");
    }
}
