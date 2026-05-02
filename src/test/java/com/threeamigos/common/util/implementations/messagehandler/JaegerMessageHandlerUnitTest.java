package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordFactoryImpl;
import com.threeamigos.common.util.implementations.messagehandler.utils.JaegerLogRecordDispatcher;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

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
        public DispatchResult dispatchFormatted(final String formattedExportLogsServiceRequestJson) throws IOException {
            if (throwable != null) {
                throw throwable;
            }
            payloads.add(formattedExportLogsServiceRequestJson);
            return null;
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
