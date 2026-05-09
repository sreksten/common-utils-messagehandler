package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordFactoryImpl;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ExportLogsServiceRequestLogRecordFormatter;
import com.threeamigos.common.util.implementations.messagehandler.utils.JaegerLogRecordDispatcher;
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
 * {@link MessageHandler} implementation that dispatches OTLP log payloads over HTTP to a configured endpoint.
 * <p>
 * This handler is intended as a transport bridge toward Jaeger-like deployments where logs are received through
 * an OTLP/HTTP endpoint (typically an OpenTelemetry Collector endpoint such as {@code /v1/logs}).
 * It serializes each {@link LogRecord} with
 * {@link ExportLogsServiceRequestLogRecordFormatter} by default and delegates the actual HTTP POST operation to
 * {@link JaegerLogRecordDispatcher}.
 *
 * <h2>Dispatch model</h2>
 * <p>
 * Inherits {@link AbstractOutputMessageHandler}'s execution modes:
 * <ul>
 *   <li><strong>Synchronous</strong>: each call performs HTTP dispatch on the caller thread.</li>
 *   <li><strong>Asynchronous</strong>: calls enqueue work and a single daemon worker performs dispatch.</li>
 * </ul>
 * Queue overflow falls back to caller-thread execution (same behavior as other output handlers).
 *
 * <h2>Failure handling</h2>
 * <p>
 * Dispatch errors are captured and reported to a configurable {@link Consumer} (default: {@code System.err::println}).
 * Optionally, the handler can auto-close itself after a dispatch failure via
 * {@link #setCloseOnDispatchError(boolean)}.
 *
 * <h2>Authentication</h2>
 * <p>
 * Use constructors that accept credentials to configure Basic or Bearer authentication on the underlying
 * {@link JaegerLogRecordDispatcher}. If both Basic and Bearer are set, Bearer is preferred by the dispatcher.
 */
public class JaegerMessageHandler extends AbstractOutputMessageHandler {

    private final LogRecordDispatcher dispatcher;
    private volatile Consumer<String> errorConsumer = System.err::println;
    private volatile boolean closeOnDispatchError = false;

    /**
     * Creates a synchronous handler with the default log-record factory / formatter and no authentication.
     *
     * @param endpointUrl OTLP HTTP endpoint (for example {@code http://localhost:4318/v1/logs})
     */
    public JaegerMessageHandler(final @Nonnull String endpointUrl) {
        this(new LogRecordFactoryImpl(),
                new ExportLogsServiceRequestLogRecordFormatter(),
                new JaegerLogRecordDispatcher(endpointUrl),
                false, 0, false);
    }

    /**
     * Creates a synchronous handler with default log-record factory/formatter and optional Basic authentication.
     *
     * @param endpointUrl OTLP HTTP endpoint
     * @param username basic-auth username, nullable
     * @param password basic-auth password, nullable
     */
    public JaegerMessageHandler(final @Nonnull String endpointUrl,
                                final @Nullable String username,
                                final @Nullable String password) {
        this(new LogRecordFactoryImpl(),
                new ExportLogsServiceRequestLogRecordFormatter(),
                new JaegerLogRecordDispatcher(endpointUrl, username, password),
                false, 0, false);
    }

    /**
     * Creates a handler with full dispatcher configuration and optional asynchronous dispatch.
     *
     * @param endpointUrl OTLP HTTP endpoint
     * @param username basic-auth username, nullable
     * @param password basic-auth password, nullable
     * @param bearerToken bearer token, nullable
     * @param connectTimeoutMillis connect timeout in milliseconds
     * @param readTimeoutMillis read timeout in milliseconds
     * @param additionalHeaders optional additional headers
     * @param async whether to use background dispatch
     * @param queueCapacity async queue capacity (0 or negative means unbounded)
     * @param registerShutdownHook whether to register a JVM shutdown hook that closes the handler
     */
    public JaegerMessageHandler(final @Nonnull String endpointUrl,
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
                new JaegerLogRecordDispatcher(endpointUrl, username, password, bearerToken,
                        connectTimeoutMillis, readTimeoutMillis, additionalHeaders),
                async, queueCapacity, registerShutdownHook);
    }

    /**
     * Creates a handler with explicit dependencies and optional asynchronous dispatch.
     *
     * @param logRecordFactory factory used to create log records from incoming message calls
     * @param logRecordFormatter formatter used to encode each log record before dispatch
     * @param dispatcher dispatcher responsible for HTTP transport
     * @param async whether to use background dispatch
     * @param queueCapacity async queue capacity (0 or negative means unbounded)
     * @param registerShutdownHook whether to register a JVM shutdown hook that closes the handler
     */
    public JaegerMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                                final @Nonnull LogRecordFormatter logRecordFormatter,
                                final @Nonnull JaegerLogRecordDispatcher dispatcher,
                                final boolean async,
                                final int queueCapacity,
                                final boolean registerShutdownHook) {
        this(logRecordFactory, logRecordFormatter, (LogRecordDispatcher) dispatcher, async, queueCapacity, registerShutdownHook);
    }

    public JaegerMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                                final @Nonnull LogRecordFormatter logRecordFormatter,
                                final @Nonnull LogRecordDispatcher dispatcher,
                                final boolean async,
                                final int queueCapacity,
                                final boolean registerShutdownHook) {
        super(logRecordFactory, logRecordFormatter);
        this.dispatcher = Objects.requireNonNull(dispatcher, MessageHandlerResourceBundle.get("nullDispatcherProvided"));
        initializeOutputDispatch(async, queueCapacity, registerShutdownHook,
                "JaegerMessageHandler-async", "JaegerMessageHandler-shutdown");
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
     *
     * @param closeOnDispatchError {@code true} to auto-close on dispatch errors
     */
    public void setCloseOnDispatchError(final boolean closeOnDispatchError) {
        this.closeOnDispatchError = closeOnDispatchError;
    }

    @Override
    public void handleMessage(final @Nonnull SeverityNumber level, final @Nonnull String message) {
        LogRecord logRecord = logRecordFactory.create(level, message);
        dispatchRecord(logRecord);
    }

    @Override
    public void handleThrowable(final @Nonnull String message, final @Nonnull Throwable throwable) {
        LogRecord logRecord = logRecordFactory.create(message, throwable);
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
        errorConsumer.accept(MessageHandlerResourceBundle.format("jaegerDispatchError", details));
        if (closeOnDispatchError) {
            if (isAsync()) {
                new Thread(this::close, "JaegerMessageHandler-close-on-error").start();
            } else {
                close();
            }
        }
    }
}
