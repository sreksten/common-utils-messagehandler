package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordFactoryImpl;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ExportLogsServiceRequestLogRecordFormatter;
import com.threeamigos.common.util.implementations.messagehandler.utils.GrafanaLogRecordDispatcher;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordDispatcher;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * {@link MessageHandler} implementation that dispatches OTLP log payloads over HTTP
 * to Grafana-compatible OTLP logs endpoints.
 * <p>
 * Unlike Jaeger-focused flows, this handler keeps messages as logs and does not convert
 * them to span events.
 * <p>
 * Dispatch failures are reported via a configurable error consumer (default: {@code System.err::println}).
 * Optional close-on-dispatch-error behavior can be enabled through
 * {@link #setCloseOnDispatchError(boolean)}. In async mode, close scheduling is one-shot, so
 * repeated failures do not create unbounded close threads.
 */
public class GrafanaMessageHandler extends AbstractOutputMessageHandler {

    private final LogRecordDispatcher dispatcher;
    private volatile Consumer<String> errorConsumer = System.err::println;
    private volatile boolean closeOnDispatchError = false;

    public GrafanaMessageHandler(final @Nonnull String endpointUrl) {
        this(new LogRecordFactoryImpl(),
                new ExportLogsServiceRequestLogRecordFormatter(),
                new GrafanaLogRecordDispatcher(endpointUrl),
                false, 0, false);
    }

    public GrafanaMessageHandler(final @Nonnull String endpointUrl,
                                 final @Nullable String username,
                                 final @Nullable String password) {
        this(new LogRecordFactoryImpl(),
                new ExportLogsServiceRequestLogRecordFormatter(),
                new GrafanaLogRecordDispatcher(endpointUrl, username, password),
                false, 0, false);
    }

    public GrafanaMessageHandler(final @Nonnull String endpointUrl,
                                 final @Nullable String username,
                                 final @Nullable String password,
                                 final @Nullable String bearerToken,
                                 final int connectTimeoutMillis,
                                 final int readTimeoutMillis,
                                 final @Nullable Map<String, String> additionalHeaders,
                                 final boolean async,
                                 final int queueCapacity,
                                 final boolean registerShutdownHook) {
        this(new LogRecordFactoryImpl(),
                new ExportLogsServiceRequestLogRecordFormatter(),
                new GrafanaLogRecordDispatcher(endpointUrl, username, password, bearerToken,
                        connectTimeoutMillis, readTimeoutMillis, additionalHeaders),
                async, queueCapacity, registerShutdownHook);
    }

    public GrafanaMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                                 final @Nonnull LogRecordFormatter logRecordFormatter,
                                 final @Nonnull GrafanaLogRecordDispatcher dispatcher,
                                 final boolean async,
                                 final int queueCapacity,
                                 final boolean registerShutdownHook) {
        this(logRecordFactory, logRecordFormatter, (LogRecordDispatcher) dispatcher, async, queueCapacity, registerShutdownHook);
    }

    public GrafanaMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                                 final @Nonnull LogRecordFormatter logRecordFormatter,
                                 final @Nonnull LogRecordDispatcher dispatcher,
                                 final boolean async,
                                 final int queueCapacity,
                                 final boolean registerShutdownHook) {
        super(logRecordFactory, logRecordFormatter);
        this.dispatcher = Objects.requireNonNull(dispatcher, MessageHandlerResourceBundle.get("nullDispatcherProvided"));
        initializeOutputDispatch(async, queueCapacity, registerShutdownHook,
                "GrafanaMessageHandler-async", "GrafanaMessageHandler-shutdown");
    }

    /**
     * Sets the error consumer invoked whenever HTTP dispatch fails.
     *
     * @param errorConsumer non-null consumer for localized error messages
     */
    public void setErrorConsumer(final @Nonnull Consumer<String> errorConsumer) {
        Objects.requireNonNull(errorConsumer, MessageHandlerResourceBundle.get("nullErrorConsumerProvided"));
        this.errorConsumer = errorConsumer;
    }

    /**
     * Enables or disables auto-close after a dispatch error.
     * <p>
     * In async mode, close is triggered on a separate thread to avoid waiting on the worker thread itself.
     * Only the first failure schedules that close thread.
     *
     * @param closeOnDispatchError {@code true} to auto-close on dispatch errors
     */
    public void setCloseOnDispatchError(final boolean closeOnDispatchError) {
        this.closeOnDispatchError = closeOnDispatchError;
    }

    @Override
    public void handleMessage(final @Nonnull SeverityNumber level, final @Nonnull String message) {
        LogRecord logRecord = createLogRecord(level, message);
        dispatchRecord(logRecord);
    }

    @Override
    protected void handleExceptionInternal(final @Nonnull String message, final @Nonnull Throwable throwable) {
        LogRecord logRecord = createLogRecord(message, throwable);
        dispatchRecord(logRecord);
    }

    private void dispatchRecord(final LogRecord logRecord) {
        dispatch(() -> {
            try {
                dispatcher.dispatchLogRecord(logRecord, getLogRecordFormatter());
            } catch (IOException e) {
                handleDispatchFailure(e);
            }
        });
    }

    private void handleDispatchFailure(final IOException error) {
        String details = error.getMessage() == null ? error.getClass().getName() : error.getMessage();
        errorConsumer.accept(MessageHandlerResourceBundle.format("grafanaDispatchError", details));
        requestCloseOnErrorIfEnabled(closeOnDispatchError, "GrafanaMessageHandler-close-on-error");
    }
}
