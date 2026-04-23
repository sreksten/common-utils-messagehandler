package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordFactoryImpl;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.RawJsonRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
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

    private static final LogRecordFactory FACTORY = new LogRecordFactoryImpl();
    private static final LogRecordFormatter DEFAULT_FORMATTER = logRecord -> {
        String severity = logRecord.getSeverityText() == null ? "UNSPEC" : logRecord.getSeverityText();
        if (severity.length() < 5) {
            severity = String.format("%-5s", severity);
        } else if (severity.length() > 5) {
            severity = severity.substring(0, 5);
        }
        return "[" + logRecord.getTimestamp() + "] [" + severity + "] " + stringifyBody(logRecord.getBody());
    };

    private static String stringifyBody(final AnyValue body) {
        if (body == null || body.getType() == null) {
            return "";
        }
        if (body.getType() == AnyValue.Type.STRING) {
            return body.asString() == null ? "" : body.asString();
        }
        return String.valueOf(body.asString());
    }

    private static class ThrowingAwaitTerminationConsoleMessageHandler extends ConsoleMessageHandler {
        private ThrowingAwaitTerminationConsoleMessageHandler() {
            super(FACTORY, DEFAULT_FORMATTER, true, 1, false);
        }

        @Override
        protected void awaitTermination() throws InterruptedException {
            throw new InterruptedException("forced");
        }
    }

    private static class ThrowingRemoveHookConsoleMessageHandler extends ConsoleMessageHandler {
        private ThrowingRemoveHookConsoleMessageHandler() {
            super(FACTORY, DEFAULT_FORMATTER, true, 1, true);
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
        ConsoleMessageHandler sut = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER);
        // When
        String infoMessage = null;
        // Then
        assertThrows(NullPointerException.class, () -> sut.info(infoMessage));
    }

    @Test
    @DisplayName("Should handle info message")
    void shouldHandleInfoMessage() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER);
        // When
        sut.info("INFO");
        // Then
        assertFormattedLine(out, "INFO ", "INFO");
    }

    @Test
    @DisplayName("Should throw an exception if a null warn message is provided")
    void shouldThrowAnExceptionIfANullWarnMessageIsProvided() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER);
        // When
        String warnMessage = null;
        // Then
        assertThrows(NullPointerException.class, () -> sut.warn(warnMessage));
    }

    @Test
    @DisplayName("Should handle warn message")
    void shouldHandleWarnMessage() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER);
        // When
        sut.warn("WARN");
        // Then
        assertFormattedLine(out, "WARN ", "WARN");
    }

    @Test
    @DisplayName("Should throw an exception if a null error message is provided")
    void shouldThrowAnExceptionIfANullErrorMessageIsProvided() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER);
        // When
        String errorMessage = null;
        // Then
        assertThrows(NullPointerException.class, () -> sut.error(errorMessage));
    }

    @Test
    @DisplayName("Should handle error message")
    void shouldHandleErrorMessage() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER);
        // When
        sut.error("ERROR");
        // Then
        assertFormattedLine(err, "ERROR", "ERROR");
    }

    @Test
    @DisplayName("Should throw an exception if a null debug message is provided")
    void shouldThrowAnExceptionIfANullDebugMessageIsProvided() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER);
        sut.setDebugEnabled(true);
        // When
        String debugMessage = null;
        // Then
        assertThrows(NullPointerException.class, () -> sut.debug(debugMessage));
    }

    @Test
    @DisplayName("Should handle debug message")
    void shouldHandleDebugMessage() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER);
        sut.setDebugEnabled(true);
        // When
        sut.debug("DEBUG");
        // Then
        assertFormattedLine(out, "DEBUG", "DEBUG");
    }

    @Test
    @DisplayName("Should throw an exception if a null trace message is provided")
    void shouldThrowAnExceptionIfANullTraceMessageIsProvided() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER);
        sut.setTraceEnabled(true);
        // When
        String traceMessage = null;
        // Then
        assertThrows(NullPointerException.class, () -> sut.trace(traceMessage));
    }

    @Test
    @DisplayName("Should handle trace message")
    void shouldHandleTraceMessage() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER);
        sut.setTraceEnabled(true);
        // When
        sut.trace("TRACE");
        // Then
        assertFormattedLine(out, "TRACE", "TRACE");
    }

    @Test
    @DisplayName("Should throw an exception if a null fatal message is provided")
    void shouldThrowAnExceptionIfANullFatalMessageIsProvided() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER);
        // When
        String fatalMessage = null;
        // Then
        assertThrows(NullPointerException.class, () -> sut.fatal(fatalMessage));
    }

    @Test
    @DisplayName("Should handle fatal message")
    void shouldHandleFatalMessage() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER);
        // When
        sut.fatal("FATAL");
        // Then
        assertFormattedLine(err, "FATAL", "FATAL");
    }

    @Test
    @DisplayName("Should throw an exception if a null exception is provided")
    void shouldThrowAnExceptionIfANullExceptionIsProvided() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER);
        // When
        Exception exception = null;
        // Then
        assertThrows(NullPointerException.class, () -> sut.exception(exception));
    }

    @Test
    @DisplayName("Should handle exception")
    void shouldHandleExceptionMessage() {
        // Given
        ConsoleMessageHandler sut = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER);
        Exception exception = mock(IllegalArgumentException.class);
        when(exception.getMessage()).thenReturn("Boom");
        // When
        sut.exception(exception);
        // Then
        ArgumentCaptor<String> captor = forClass(String.class);
        verify(err, times(1)).println(captor.capture());
        String actual = captor.getValue();
        assertTrue(actual.contains("[ERROR]"), "Output should contain [ERROR]");
        assertTrue(actual.contains("Boom"), "Output should contain the exception message");
    }

    @Test
    @DisplayName("Should handle exception with custom message")
    void shouldHandleExceptionWithCustomMessage() {
        ConsoleMessageHandler sut = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER);
        Exception exception = mock(IllegalArgumentException.class);
        when(exception.getMessage()).thenReturn("Boom");

        sut.exception("prefix", exception);

        ArgumentCaptor<String> captor = forClass(String.class);
        verify(err, times(1)).println(captor.capture());
        assertTrue(captor.getValue().contains("[ERROR] prefix"));
    }

    @Test
    @DisplayName("RawJsonRecordFormatter should produce NDJSON output to System.out")
    void jsonFormatterShouldProduceNdjsonOutput() throws Exception {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent, true, StandardCharsets.UTF_8.name()));
        try (ConsoleMessageHandler handler = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER)) {
            handler.setLogRecordFormatter(new RawJsonRecordFormatter());
            handler.info("structured message");
        } finally {
            System.setOut(originalOut);
        }
        String output = outContent.toString(StandardCharsets.UTF_8.name());
        assertTrue(output.startsWith("{"), "Output should be a JSON object");
        assertTrue(output.contains("\"severityText\":\"INFO\""), "Output should contain severity INFO");
        assertTrue(output.contains("\"stringValue\":\"structured message\""), "Output should contain the message");
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
        try (ConsoleMessageHandler handler = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER, true, 1)) {
            handler.info("first");
            assertTrue(firstPrintEntered.await(500, TimeUnit.MILLISECONDS));
            handler.info("queued");
            handler.info("sync");
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
        try (ConsoleMessageHandler handler = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER, true, 1)) {
            handler.info("blocking");
            assertTrue(firstPrintEntered.await(500, TimeUnit.MILLISECONDS));
            handler.info("queued-drain");
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
        try (ConsoleMessageHandler handler = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER, true, 1)) {
            CountDownLatch latch = new CountDownLatch(1);
            handler.info(() -> {
                try {
                    latch.await(500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignored) {
                }
                return "background";
            });
            handler.info("sync");
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
        ConsoleMessageHandler handler = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER, true, 1, true);
        Thread hook = getShutdownHook(handler);
        assertNotNull(hook);
        handler.close();
    }

    @Test
    @DisplayName("Should not register shutdown hook when not requested")
    void shouldNotRegisterShutdownHook() throws Exception {
        ConsoleMessageHandler handler = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER, true, 1, false);
        Thread hook = getShutdownHook(handler);
        assertNull(hook);
        handler.close();
    }

    @Test
    @DisplayName("Async with non-positive capacity should behave as unbounded")
    void asyncWithNonPositiveCapacityBehavesUnbounded() {
        assertDoesNotThrow(() -> {
            try (ConsoleMessageHandler handler = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER, true, 0, false)) {
                handler.info("msg1");
                handler.info("msg2");
                Thread.sleep(100);
            }
        });
    }

    @Test
    @DisplayName("Close on synchronous handler should be a no-op")
    void closeOnSynchronousHandlerShouldBeNoOp() {
        ConsoleMessageHandler handler = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER);
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
        try (ConsoleMessageHandler handler = new ConsoleMessageHandler(FACTORY, DEFAULT_FORMATTER, true, 2048, false)) {
            ExecutorService producerPool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                futures.add(producerPool.submit(() -> {
                    start.await();
                    for (int i = 0; i < messagesPerThread; i++) {
                        final int messageIndex = i;
                        handler.info(() -> "stress-console-" + threadId + "-" + messageIndex);
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
