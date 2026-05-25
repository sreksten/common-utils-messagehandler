package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.durability.InMemoryHttpDispatchDurabilityStore;
import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordFactoryImpl;
import com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ExportLogsServiceRequestLogRecordFormatter;
import com.threeamigos.common.util.implementations.messagehandler.utils.GrafanaLogRecordDispatcher;
import com.threeamigos.common.util.implementations.messagehandler.utils.HttpDispatchStatusException;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        AbstractOutputMessageHandler.HandlerHealthMetrics metrics = sut.getHandlerHealthMetrics();
        sut.close();

        assertEquals(1, dispatcher.payloads.size());
        assertEquals("{\"msg\":\"hello\"}", dispatcher.payloads.get(0));
        assertEquals(1L, metrics.getSuccessfulOperations());
        assertEquals(0L, metrics.getFailedOperations());
        assertTrue(metrics.isHealthy());
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
        AbstractOutputMessageHandler.HandlerHealthMetrics metrics = sut.getHandlerHealthMetrics();
        sut.close();

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("Failed to dispatch log record to Grafana endpoint"));
        assertTrue(errors.get(0).contains("network down"));
        assertEquals(0L, metrics.getSuccessfulOperations());
        assertEquals(1L, metrics.getFailedOperations());
        assertEquals(1L, metrics.getConsecutiveFailures());
        assertFalse(metrics.isHealthy());
    }

    @Test
    @DisplayName("async convenience constructor should register shutdown hook by default")
    void asyncConvenienceConstructorShouldRegisterShutdownHookByDefault() throws Exception {
        GrafanaMessageHandler handler = new GrafanaMessageHandler("http://localhost:4318/v1/logs", true, 10);
        try {
            assertNotNull(getShutdownHook(handler));
        } finally {
            handler.close();
        }
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
        sut.info("after-close");

        AbstractOutputMessageHandler.HandlerHealthMetrics metrics = sut.getHandlerHealthMetrics();
        assertTrue(metrics.isClosed());
        assertEquals(1L, metrics.getFailedOperations());
        assertEquals(1L, metrics.getDroppedOperations());
    }

    @Test
    @DisplayName("should retry transient IO failures and expose retry metrics")
    void shouldRetryTransientIoFailuresAndExposeRetryMetrics() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        dispatcher.failNextDispatches(1, new IOException("transient timeout"));

        GrafanaMessageHandler sut = new GrafanaMessageHandler(
                new LogRecordFactoryImpl(),
                logRecord -> "{}",
                dispatcher,
                false, 0, false);
        sut.setHttpRetryPolicy(2, 0L, 0L);

        sut.info("x");
        AbstractOutputMessageHandler.HandlerHealthMetrics metrics = sut.getHandlerHealthMetrics();
        sut.close();

        assertEquals(2, dispatcher.invocations.get());
        assertEquals(1, dispatcher.payloads.size());
        assertEquals(1L, metrics.getSuccessfulOperations());
        assertEquals(0L, metrics.getFailedOperations());
        assertEquals(1L, metrics.getRetryAttempts());
        assertEquals(1L, metrics.getRetrySuccesses());
        assertEquals(0L, metrics.getRetryFailures());
    }

    @Test
    @DisplayName("failed records should be batched with next dispatch attempt")
    void failedRecordsShouldBeBatchedWithNextDispatchAttempt() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        dispatcher.failNextBatches(2, new IOException("temporarily unavailable"));
        List<String> errors = new ArrayList<String>();

        GrafanaMessageHandler sut = new GrafanaMessageHandler(
                new LogRecordFactoryImpl(),
                logRecord -> "{}",
                dispatcher,
                false, 0, false);
        sut.setHttpRetryPolicy(1, 0L, 0L);
        sut.setErrorConsumer(errors::add);

        sut.info("first");
        sut.info("second");

        AbstractOutputMessageHandler.HandlerHealthMetrics metrics = sut.getHandlerHealthMetrics();
        sut.close();

        assertEquals(Arrays.asList(1, 1, 2), dispatcher.batchSizes);
        assertEquals(1L, metrics.getFailedOperations());
        assertEquals(1L, metrics.getSuccessfulOperations());
        assertEquals(1L, metrics.getRetryAttempts());
        assertEquals(0L, metrics.getRetrySuccesses());
        assertEquals(1L, metrics.getRetryFailures());
        assertEquals(1, errors.size());
    }

    @Test
    @DisplayName("should not retry non-retryable HTTP status failures")
    void shouldNotRetryNonRetryableHttpStatusFailures() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        dispatcher.throwable = new HttpDispatchStatusException(
                "http://localhost:4318/v1/logs",
                400,
                "bad-request");
        List<String> errors = new ArrayList<String>();
        GrafanaMessageHandler sut = new GrafanaMessageHandler(
                new LogRecordFactoryImpl(),
                logRecord -> "{}",
                dispatcher,
                false, 0, false);
        sut.setHttpRetryPolicy(3, 0L, 0L);
        sut.setErrorConsumer(errors::add);

        sut.info("x");
        AbstractOutputMessageHandler.HandlerHealthMetrics metrics = sut.getHandlerHealthMetrics();
        sut.close();

        assertEquals(1, dispatcher.invocations.get());
        assertEquals(1, errors.size());
        assertEquals(1L, metrics.getFailedOperations());
        assertEquals(0L, metrics.getRetryAttempts());
        assertEquals(0L, metrics.getRetrySuccesses());
        assertEquals(0L, metrics.getRetryFailures());
    }

    @Test
    @DisplayName("runtime dispatch failures should not propagate to caller and should update metrics")
    void runtimeDispatchFailuresShouldNotPropagateAndShouldUpdateMetrics() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        List<String> errors = new ArrayList<String>();
        GrafanaMessageHandler sut = new GrafanaMessageHandler(
                new LogRecordFactoryImpl(),
                logRecord -> {
                    throw new IllegalStateException("formatter crash");
                },
                dispatcher,
                false, 0, false);
        sut.setErrorConsumer(errors::add);

        sut.info("x");
        AbstractOutputMessageHandler.HandlerHealthMetrics metrics = sut.getHandlerHealthMetrics();
        sut.close();

        assertEquals(1L, metrics.getFailedOperations());
        assertEquals(0L, metrics.getSuccessfulOperations());
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("formatter crash"));
    }

    @Test
    @DisplayName("adaptive retry budget should scale with throughput and cap per-window retries")
    void adaptiveRetryBudgetShouldScaleWithThroughputAndCapPerWindowRetries() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        dispatcher.failNextDispatches(3, new IOException("transient overload"));
        List<String> errors = new ArrayList<String>();

        GrafanaMessageHandler sut = new GrafanaMessageHandler(
                new LogRecordFactoryImpl(),
                logRecord -> "{}",
                dispatcher,
                false, 0, false);
        sut.setErrorConsumer(errors::add);
        sut.setHttpRetryPolicy(3, 0L, 0L);
        sut.setHttpAdaptiveRetryBudgetPolicy(100, 0, 30_000L, 0.0d);

        sut.info("first");
        sut.info("second");

        AbstractOutputMessageHandler.HandlerHealthMetrics metrics = sut.getHandlerHealthMetrics();
        sut.close();

        assertEquals(Arrays.asList(1, 1, 2, 2), dispatcher.batchSizes);
        assertEquals(5, dispatcher.invocations.get());
        assertEquals(2, dispatcher.payloads.size());
        assertEquals(1, errors.size());
        assertEquals(1L, metrics.getSuccessfulOperations());
        assertEquals(1L, metrics.getFailedOperations());
        assertEquals(2L, metrics.getRetryAttempts());
        assertEquals(1L, metrics.getRetrySuccesses());
        assertEquals(1L, metrics.getRetryFailures());
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
        sut.info("second");

        AbstractOutputMessageHandler.HandlerHealthMetrics metrics = sut.getHandlerHealthMetrics();
        assertTrue(metrics.isClosed());
        assertTrue(metrics.getDroppedOperations() >= 1L);
    }

    @Test
    @DisplayName("closeOnDispatchError in async mode should schedule close only once")
    void closeOnDispatchErrorAsyncShouldScheduleCloseOnlyOnce() throws Exception {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        dispatcher.throwable = new IOException("boom");
        CloseCountingGrafanaMessageHandler sut = new CloseCountingGrafanaMessageHandler(dispatcher);
        sut.setErrorConsumer(msg -> {
        });
        sut.setCloseOnDispatchError(true);

        try {
            for (int i = 0; i < 300; i++) {
                sut.info("trigger-" + i);
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
        assertThrows(NullPointerException.class, () -> new GrafanaMessageHandler(
                new LogRecordFactoryImpl(), logRecord -> "{}", dispatcher, false, 0, false,
                null, System.err::println));
        assertThrows(NullPointerException.class, () -> new GrafanaMessageHandler(
                new LogRecordFactoryImpl(), logRecord -> "{}", dispatcher, false, 0, false,
                new InMemoryHttpDispatchDurabilityStore(), null));
        assertThrows(IllegalArgumentException.class, () -> sut.setHttpCircuitBreakerPolicy(0, 100L, 1));
        assertThrows(IllegalArgumentException.class, () -> sut.setHttpCircuitBreakerPolicy(1, 0L, 1));
        assertThrows(IllegalArgumentException.class, () -> sut.setHttpCircuitBreakerPolicy(1, 100L, 0));
        assertThrows(IllegalArgumentException.class, () -> sut.setHttpAdaptiveRetryBudgetPolicy(-1, 0, 1000L, 0.0d));
        assertThrows(IllegalArgumentException.class, () -> sut.setHttpAdaptiveRetryBudgetPolicy(100, -1, 1000L, 0.0d));
        assertThrows(IllegalArgumentException.class, () -> sut.setHttpAdaptiveRetryBudgetPolicy(100, 0, 0L, 0.0d));
        assertThrows(IllegalArgumentException.class, () -> sut.setHttpAdaptiveRetryBudgetPolicy(100, 0, 1000L, 1.5d));
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
            if (handler.getHandlerHealthMetrics().isClosed()) {
                return;
            }
            handler.info("probe");
            Thread.sleep(10L);
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

    private static Thread getShutdownHook(final GrafanaMessageHandler handler) throws Exception {
        Field shutdownHookField = AbstractOutputMessageHandler.class.getDeclaredField("shutdownHook");
        shutdownHookField.setAccessible(true);
        return (Thread) shutdownHookField.get(handler);
    }

    private static final class CapturingDispatcher extends GrafanaLogRecordDispatcher {
        private final List<String> payloads = new CopyOnWriteArrayList<String>();
        private final List<Integer> batchSizes = new CopyOnWriteArrayList<Integer>();
        private final AtomicInteger invocations = new AtomicInteger(0);
        private volatile IOException throwable;
        private volatile int transientFailuresRemaining = 0;
        private volatile IOException transientThrowable = new IOException("transient dispatch failure");
        private volatile int batchesToFail = 0;
        private volatile IOException batchFailure = new IOException("batch dispatch failure");

        private CapturingDispatcher() {
            super("http://localhost:4318/v1/logs");
        }

        @Override
        public synchronized void dispatchLogRecord(final @Nonnull LogRecord logRecord,
                                                   final @Nonnull LogRecordFormatter logRecordFormatter) throws IOException {
            invocations.incrementAndGet();
            if (throwable != null) {
                throw throwable;
            }
            if (transientFailuresRemaining > 0) {
                transientFailuresRemaining--;
                throw transientThrowable == null ? new IOException("transient dispatch failure") : transientThrowable;
            }
            payloads.add(logRecordFormatter.format(logRecord));
        }

        @Override
        public synchronized void dispatchLogRecords(final @Nonnull List<LogRecord> logRecords,
                                                    final @Nonnull LogRecordFormatter logRecordFormatter) throws IOException {
            batchSizes.add(logRecords == null ? -1 : logRecords.size());
            if (batchesToFail > 0) {
                batchesToFail--;
                throw batchFailure == null ? new IOException("batch dispatch failure") : batchFailure;
            }
            if (logRecords == null) {
                return;
            }
            for (LogRecord logRecord : logRecords) {
                if (logRecord == null) {
                    continue;
                }
                dispatchLogRecord(logRecord, logRecordFormatter);
            }
        }

        private synchronized void failNextDispatches(final int failures, final IOException failureToThrow) {
            this.transientFailuresRemaining = failures;
            this.transientThrowable = failureToThrow;
        }

        private synchronized void failNextBatches(final int failures, final IOException failureToThrow) {
            this.batchesToFail = failures;
            this.batchFailure = failureToThrow;
        }
    }

    private static final class CloseCountingGrafanaMessageHandler extends GrafanaMessageHandler {
        private final AtomicInteger closeInvocations = new AtomicInteger();

        private CloseCountingGrafanaMessageHandler(final CapturingDispatcher dispatcher) {
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

    // -------------------------------------------------------------------------
    // closeOutput() dispatcher lifecycle tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("close() should invoke close() on a Closeable dispatcher")
    void close_invokesCloseOnCloseableDispatcher() {
        CloseTrackingDispatcher dispatcher = new CloseTrackingDispatcher();
        GrafanaMessageHandler sut = new GrafanaMessageHandler(
                new LogRecordFactoryImpl(), logRecord -> "{}", dispatcher, false, 0, false);
        sut.close();
        assertTrue(dispatcher.wasClosed);
    }

    @Test
    @DisplayName("close() should not fail when dispatcher does not implement Closeable")
    void close_nonCloseableDispatcher_noError() {
        NonCloseableDispatcher dispatcher = new NonCloseableDispatcher();
        GrafanaMessageHandler sut = new GrafanaMessageHandler(
                new LogRecordFactoryImpl(), logRecord -> "{}", dispatcher, false, 0, false);
        sut.close(); // must not throw
    }

    private static final class CloseTrackingDispatcher extends GrafanaLogRecordDispatcher {
        boolean wasClosed = false;

        CloseTrackingDispatcher() {
            super("http://localhost:3100/loki/api/v1/push");
        }

        @Override
        public void close() throws java.io.IOException {
            wasClosed = true;
            super.close();
        }
    }

    private static final class NonCloseableDispatcher implements
            com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordDispatcher {
        @Override
        public void dispatchLogRecord(
                final @Nonnull com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord logRecord,
                final @Nonnull com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter formatter) {
        }
    }

    private static final class MinimalThrowableFormatter implements LogRecordFormatter {
        @Nonnull
        @Override
        public String format(@Nonnull final LogRecord logRecord) {
            String body = logRecord.getBody() == null || logRecord.getBody().asString() == null
                    ? "" : logRecord.getBody().asString();
            return "{\"severity\":\"" + logRecord.getSeverityText() + "\",\"body\":\"" + body + "\"}";
        }
    }
}
