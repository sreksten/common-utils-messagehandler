package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordFactoryImpl;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ExportLogsServiceRequestLogRecordFormatter;
import com.threeamigos.common.util.implementations.messagehandler.utils.JaegerLogRecordDispatcher;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("JaegerMessageHandler unit tests")
@Tag("unit")
@Tag("messageHandler")
class JaegerMessageHandlerUnitTest {

    @Test
    @DisplayName("should dispatch formatted message records")
    void shouldDispatchFormattedMessageRecords() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        JaegerMessageHandler sut = new JaegerMessageHandler(
                new LogRecordFactoryImpl(),
                logRecord -> "{\"msg\":\"" + logRecord.getBody().asString() + "\"}",
                dispatcher,
                false, 0, false);

        sut.info("hello");
        sut.close();

        assertEquals(1, dispatcher.payloads.size());
        assertEquals("{\"msg\":\"hello\"}", dispatcher.payloads.get(0));
    }

    @Test
    @DisplayName("should dispatch throwable records")
    void shouldDispatchThrowableRecords() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        JaegerMessageHandler sut = new JaegerMessageHandler(
                new LogRecordFactoryImpl(),
                new MinimalThrowableFormatter(),
                dispatcher,
                false, 0, false);

        sut.exception("prefix", new IllegalStateException("boom"));
        sut.close();

        assertEquals(1, dispatcher.payloads.size());
        assertTrue(dispatcher.payloads.get(0).contains("\"severity\":\"ERROR\""));
        assertTrue(dispatcher.payloads.get(0).contains("\"body\":\"prefix\""));
    }

    @Test
    @DisplayName("async mode should dispatch records through worker")
    void asyncModeShouldDispatchThroughWorker() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        JaegerMessageHandler sut = new JaegerMessageHandler(
                new LogRecordFactoryImpl(),
                logRecord -> "{\"s\":\"" + logRecord.getSeverityText() + "\"}",
                dispatcher,
                true, 16, false);
        try {
            sut.warn("w1");
            sut.warn("w2");
        } finally {
            sut.close();
        }
        assertEquals(2, dispatcher.payloads.size());
    }

    @Test
    @DisplayName("dispatch failure should be reported to error consumer")
    void dispatchFailureShouldBeReportedToErrorConsumer() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        dispatcher.throwable = new IOException("network down");
        List<String> errors = new ArrayList<String>();
        JaegerMessageHandler sut = new JaegerMessageHandler(
                new LogRecordFactoryImpl(),
                logRecord -> "{}",
                dispatcher,
                false, 0, false);
        sut.setErrorConsumer(errors::add);

        sut.info("x");
        sut.close();

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("Failed to dispatch log record to Jaeger endpoint"));
        assertTrue(errors.get(0).contains("network down"));
    }

    @Test
    @DisplayName("dispatch failure with null exception message should include exception class")
    void dispatchFailureWithNullMessageShouldIncludeExceptionClass() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        dispatcher.throwable = new IOException();
        List<String> errors = new ArrayList<String>();
        JaegerMessageHandler sut = new JaegerMessageHandler(
                new LogRecordFactoryImpl(),
                logRecord -> "{}",
                dispatcher,
                false, 0, false);
        sut.setErrorConsumer(errors::add);

        sut.info("x");
        sut.close();

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("java.io.IOException"));
    }

    @Test
    @DisplayName("closeOnDispatchError should close handler in synchronous mode")
    void closeOnDispatchErrorShouldCloseSyncHandler() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        dispatcher.throwable = new IOException("boom");
        JaegerMessageHandler sut = new JaegerMessageHandler(
                new LogRecordFactoryImpl(),
                logRecord -> "{}",
                dispatcher,
                false, 0, false);
        sut.setErrorConsumer(msg -> {
        });
        sut.setCloseOnDispatchError(true);

        sut.info("first");
        assertThrows(IllegalStateException.class, () -> sut.info("second"));
    }

    @Test
    @DisplayName("closeOnDispatchError should close handler in async mode")
    void closeOnDispatchErrorShouldCloseAsyncHandler() throws Exception {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        dispatcher.throwable = new IOException("boom");
        JaegerMessageHandler sut = new JaegerMessageHandler(
                new LogRecordFactoryImpl(),
                logRecord -> "{}",
                dispatcher,
                true, 8, false);
        sut.setErrorConsumer(msg -> {
        });
        sut.setCloseOnDispatchError(true);

        sut.info("first");
        waitUntilClosed(sut, 2_000);
        assertThrows(IllegalStateException.class, () -> sut.info("second"));
    }

    @Test
    @DisplayName("closeOnDispatchError in async mode should schedule close only once")
    void closeOnDispatchErrorAsyncShouldScheduleCloseOnlyOnce() throws Exception {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        dispatcher.throwable = new IOException("boom");
        CloseCountingJaegerMessageHandler sut = new CloseCountingJaegerMessageHandler(dispatcher);
        sut.setErrorConsumer(msg -> {
        });
        sut.setCloseOnDispatchError(true);

        try {
            for (int i = 0; i < 300; i++) {
                try {
                    sut.info("trigger-" + i);
                } catch (IllegalStateException ignored) {
                    // Expected once the close thread completes.
                }
            }

            long deadline = System.currentTimeMillis() + 5000;
            while (sut.getCloseInvocations() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10L);
            }
            assertTrue(sut.getCloseInvocations() >= 1, "close() should be invoked at least once");

            Thread.sleep(400L);
            assertEquals(1, sut.getCloseInvocations(),
                    "Async close-on-dispatch-error must schedule exactly one close request");
        } finally {
            sut.forceCloseWithoutCounting();
        }
    }

    @Test
    @DisplayName("constructors and guards should validate parameters")
    void constructorsAndGuardsShouldValidateParameters() {
        JaegerMessageHandler simple = new JaegerMessageHandler("http://localhost:4318/v1/logs");
        JaegerMessageHandler basic = new JaegerMessageHandler("http://localhost:4318/v1/logs", "u", "p");
        JaegerMessageHandler full = new JaegerMessageHandler(
                "http://localhost:4318/v1/logs", null, null, "token", 1000, 1000, Collections.singletonMap("X-A", "b"),
                false, 0, false);
        simple.close();
        basic.close();
        full.close();

        CapturingDispatcher dispatcher = new CapturingDispatcher();
        JaegerMessageHandler sut = new JaegerMessageHandler(
                new LogRecordFactoryImpl(), logRecord -> "{}", dispatcher, false, 0, false);
        assertThrows(NullPointerException.class, () -> sut.setErrorConsumer(null));
        assertThrows(NullPointerException.class, () -> new JaegerMessageHandler(
                new LogRecordFactoryImpl(), logRecord -> "{}", null, false, 0, false));
    }

    @Test
    @DisplayName("default jaeger handler constructor should wire dispatcher and export-logs formatter")
    void defaultJaegerHandlerConstructorShouldWireDispatcherAndFormatter() throws Exception {
        JaegerMessageHandler handler = new JaegerMessageHandler("http://localhost:4318/v1/logs");
        try {
            Field dispatcherField = JaegerMessageHandler.class.getDeclaredField("dispatcher");
            dispatcherField.setAccessible(true);
            Object dispatcher = dispatcherField.get(handler);

            Field formatterField = findField(handler.getClass(), "logRecordFormatter");
            formatterField.setAccessible(true);
            Object formatter = formatterField.get(handler);

            assertTrue(dispatcher instanceof JaegerLogRecordDispatcher);
            assertTrue(formatter instanceof ExportLogsServiceRequestLogRecordFormatter);
        } finally {
            handler.close();
        }
    }

    @Test
    @DisplayName("jaeger handler should use export-logs formatter output with dispatcher")
    void jaegerHandlerShouldUseExportLogsFormatterOutputWithDispatcher() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        JaegerMessageHandler sut = new JaegerMessageHandler(
                new LogRecordFactoryImpl(),
                new ExportLogsServiceRequestLogRecordFormatter(),
                dispatcher,
                false, 0, false);

        sut.info("Hello Jaeger!");
        sut.close();

        assertEquals(1, dispatcher.payloads.size());
        String payload = dispatcher.payloads.get(0);
        assertTrue(payload.contains("\"resourceLogs\""));
        assertTrue(payload.contains("\"scopeLogs\""));
        assertTrue(payload.contains("\"logRecords\""));
        assertTrue(payload.contains("Hello Jaeger!"));
    }

    private static Field findField(final Class<?> type, final String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static void waitUntilClosed(final JaegerMessageHandler handler, final long timeoutMillis) throws Exception {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadlineNanos) {
            try {
                handler.info("probe");
                Thread.sleep(10L);
            } catch (IllegalStateException closed) {
                return;
            }
        }
        throw new AssertionError("handler did not close in expected time");
    }

    private static final class CapturingDispatcher extends JaegerLogRecordDispatcher {
        private final List<String> payloads = new CopyOnWriteArrayList<String>();
        private volatile IOException throwable;

        private CapturingDispatcher() {
            super("http://localhost:4318/v1/logs");
        }

        @Override
        public DispatchResult dispatchFormatted(@Nonnull final String formattedExportLogsServiceRequestJson) throws IOException {
            if (throwable != null) {
                throw throwable;
            }
            payloads.add(formattedExportLogsServiceRequestJson);
            return null;
        }
    }

    private static final class CloseCountingJaegerMessageHandler extends JaegerMessageHandler {
        private final AtomicInteger closeInvocations = new AtomicInteger();

        private CloseCountingJaegerMessageHandler(final CapturingDispatcher dispatcher) {
            super(new LogRecordFactoryImpl(), logRecord -> "{}", dispatcher, true, 4096, false);
        }

        @Override
        public void close() {
            closeInvocations.incrementAndGet();
            try {
                Thread.sleep(150L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            super.close();
        }

        private int getCloseInvocations() {
            return closeInvocations.get();
        }

        private void forceCloseWithoutCounting() {
            super.close();
        }
    }

    private static final class MinimalThrowableFormatter implements LogRecordFormatter {
        @Override
        public String format(final LogRecord logRecord) {
            String body = logRecord.getBody() == null || logRecord.getBody().asString() == null
                    ? "" : logRecord.getBody().asString();
            return "{\"severity\":\"" + logRecord.getSeverityText() + "\",\"body\":\"" + body + "\"}";
        }
    }
}
