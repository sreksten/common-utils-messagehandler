package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordFactoryImpl;
import com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ExportLogsServiceRequestLogRecordFormatter;
import com.threeamigos.common.util.implementations.messagehandler.utils.GrafanaLogRecordDispatcher;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GrafanaMessageHandler unit tests")
@Tag("unit")
@Tag("messageHandler")
class GrafanaMessageHandlerUnitTest {

    @Test
    @DisplayName("should dispatch formatted message records")
    void shouldDispatchFormattedMessageRecords() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        GrafanaMessageHandler sut = new GrafanaMessageHandler(
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
        GrafanaMessageHandler sut = new GrafanaMessageHandler(
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
    @DisplayName("dispatch failure should be reported to error consumer")
    void dispatchFailureShouldBeReportedToErrorConsumer() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        dispatcher.throwable = new IOException("network down");
        List<String> errors = new ArrayList<String>();
        GrafanaMessageHandler sut = new GrafanaMessageHandler(
                new LogRecordFactoryImpl(),
                logRecord -> "{}",
                dispatcher,
                false, 0, false);
        sut.setErrorConsumer(errors::add);

        sut.info("x");
        sut.close();

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("Failed to dispatch log record to Grafana endpoint"));
        assertTrue(errors.get(0).contains("network down"));
    }

    @Test
    @DisplayName("dispatch failure with null message should use exception class and close in sync mode")
    void dispatchFailureWithNullMessageShouldUseExceptionClassAndCloseSync() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        dispatcher.throwable = new IOException();
        List<String> errors = new ArrayList<String>();
        GrafanaMessageHandler sut = new GrafanaMessageHandler(
                new LogRecordFactoryImpl(),
                logRecord -> "{}",
                dispatcher,
                false, 0, false);
        sut.setErrorConsumer(errors::add);
        sut.setCloseOnDispatchError(true);

        sut.info("x");

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains(IOException.class.getName()));
        assertThrows(IllegalStateException.class, () -> sut.info("after-close"));
    }

    @Test
    @DisplayName("closeOnDispatchError should close handler in async mode")
    void closeOnDispatchErrorShouldCloseAsyncHandler() throws Exception {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        dispatcher.throwable = new IOException("boom");
        GrafanaMessageHandler sut = new GrafanaMessageHandler(
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
        GrafanaMessageHandler simple = new GrafanaMessageHandler("http://localhost:4318/v1/logs");
        GrafanaMessageHandler basic = new GrafanaMessageHandler("http://localhost:4318/v1/logs", "u", "p");
        GrafanaMessageHandler full = new GrafanaMessageHandler(
                "http://localhost:4318/v1/logs", null, null, "token", 1000, 1000, Collections.singletonMap("X-A", "b"),
                false, 0, false);
        simple.close();
        basic.close();
        full.close();

        CapturingDispatcher dispatcher = new CapturingDispatcher();
        GrafanaMessageHandler sut = new GrafanaMessageHandler(
                new LogRecordFactoryImpl(), logRecord -> "{}", dispatcher, false, 0, false);
        assertThrows(NullPointerException.class, () -> sut.setErrorConsumer(null));
        assertThrows(NullPointerException.class, () -> new GrafanaMessageHandler(
                new LogRecordFactoryImpl(), logRecord -> "{}", null, false, 0, false));
    }

    @Test
    @DisplayName("default grafana handler constructor should wire dispatcher and export-logs formatter")
    void defaultGrafanaHandlerConstructorShouldWireDispatcherAndFormatter() throws Exception {
        GrafanaMessageHandler handler = new GrafanaMessageHandler("http://localhost:4318/v1/logs");
        try {
            Field dispatcherField = GrafanaMessageHandler.class.getDeclaredField("dispatcher");
            dispatcherField.setAccessible(true);
            Object dispatcher = dispatcherField.get(handler);

            Field formatterField = findField(handler.getClass(), "logRecordFormatter");
            formatterField.setAccessible(true);
            Object formatter = formatterField.get(handler);

            assertTrue(dispatcher instanceof GrafanaLogRecordDispatcher);
            assertTrue(formatter instanceof ExportLogsServiceRequestLogRecordFormatter);
        } finally {
            handler.close();
        }
    }

    @Test
    @DisplayName("startSpan on grafana handler exports spans only when provider span dispatcher is configured")
    void startSpanOnGrafanaHandlerExportsSpansOnlyWhenProviderSpanDispatcherIsConfigured() {
        TracerProvider provider = TracerProvider.builder()
                .serviceName("checkout-api")
                .serviceVersion("1.0.0")
                .build();
        Tracer tracer = provider.getTracer("checkout-api", "1.0.0");
        GrafanaMessageHandler handler = tracer.getGrafanaMessageHandler("http://localhost:3100/loki/api/v1/push");

        AtomicInteger exportedSpans = new AtomicInteger(0);

        Span withoutDispatcher = handler.startSpan("without-dispatcher");
        withoutDispatcher.end();
        assertEquals(0, exportedSpans.get());

        provider.setDefaultSpanDispatcher(spanData -> exportedSpans.incrementAndGet());
        Span withDispatcher = handler.startSpan("with-dispatcher");
        withDispatcher.end();
        assertEquals(1, exportedSpans.get());

        handler.close();
    }

    private static void waitUntilClosed(final GrafanaMessageHandler handler, final long timeoutMillis) throws Exception {
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

    private static final class CapturingDispatcher extends GrafanaLogRecordDispatcher {
        private final List<String> payloads = new CopyOnWriteArrayList<String>();
        private volatile IOException throwable;

        private CapturingDispatcher() {
            super("http://localhost:4318/v1/logs");
        }

        @Override
        public DispatchResult dispatch(final @Nonnull LogRecord logRecord,
                                       final @Nonnull LogRecordFormatter logRecordFormatter) throws IOException {
            if (throwable != null) {
                throw throwable;
            }
            payloads.add(logRecordFormatter.format(logRecord));
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
