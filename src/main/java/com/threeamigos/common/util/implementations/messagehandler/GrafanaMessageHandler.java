package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.durability.HttpDispatchDurabilityStore;
import com.threeamigos.common.util.implementations.messagehandler.durability.InMemoryHttpDispatchDurabilityStore;
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
 * Default constructors serialize logs with {@link ExportLogsServiceRequestLogRecordFormatter},
 * so payloads are JSON by default (OTLP ExportLogsServiceRequest JSON; or Loki JSON payload
 * when using a Loki push endpoint).
 * <p>
 * Dispatch failures are reported via a configurable error consumer (default:
 * {@link InnerErrorMessageHandler#consume(String)}, which defaults to {@code System.err::println}).
 * Optional close-on-dispatch-error behavior can be enabled through
 * {@link #setCloseOnDispatchError(boolean)}. In async mode, close scheduling is one-shot, so
 * repeated failures do not create unbounded close threads.
 * Durable pending storage is configurable through {@link HttpDispatchDurabilityStore};
 * constructors that do not expose a policy use {@link InMemoryHttpDispatchDurabilityStore}.
 * Dead-letter reporting is configurable via a dedicated consumer and defaults to {@code System.err::println}.
 * <p>
 * Async convenience constructors (without an explicit {@code registerShutdownHook} argument)
 * enable shutdown-hook registration by default. When using constructors where
 * {@code registerShutdownHook=false}, callers must invoke {@link #close()} during shutdown.
 */
public class GrafanaMessageHandler extends AbstractHTTPOutputMessageHandler {

    private final LogRecordDispatcher dispatcher;
    private volatile Consumer<String> errorConsumer = InnerErrorMessageHandler::consume;
    private volatile boolean closeOnDispatchError = false;

    public GrafanaMessageHandler(final @Nonnull String endpointUrl) {
        this(new LogRecordFactoryImpl(),
                new ExportLogsServiceRequestLogRecordFormatter(),
                new GrafanaLogRecordDispatcher(endpointUrl),
                false, 0, false);
    }

    /**
     * Creates a synchronous handler with explicit durability policy and default dead-letter consumer.
     *
     * @param endpointUrl OTLP HTTP endpoint
     * @param durabilityStore pending-record durability policy
     */
    public GrafanaMessageHandler(final @Nonnull String endpointUrl,
                                 final @Nonnull HttpDispatchDurabilityStore durabilityStore) {
        this(endpointUrl, durabilityStore, System.err::println);
    }

    /**
     * Creates a synchronous handler with explicit durability policy and dead-letter consumer.
     *
     * @param endpointUrl OTLP HTTP endpoint
     * @param durabilityStore pending-record durability policy
     * @param deadLetterConsumer consumer invoked for dead-letter records
     */
    public GrafanaMessageHandler(final @Nonnull String endpointUrl,
                                 final @Nonnull HttpDispatchDurabilityStore durabilityStore,
                                 final @Nonnull Consumer<String> deadLetterConsumer) {
        this(new LogRecordFactoryImpl(),
                new ExportLogsServiceRequestLogRecordFormatter(),
                new GrafanaLogRecordDispatcher(endpointUrl),
                false, 0, false, durabilityStore, deadLetterConsumer);
    }

    public GrafanaMessageHandler(final @Nonnull String endpointUrl,
                                 final @Nullable String username,
                                 final @Nullable String password) {
        this(new LogRecordFactoryImpl(),
                new ExportLogsServiceRequestLogRecordFormatter(),
                new GrafanaLogRecordDispatcher(endpointUrl, username, password),
                false, 0, false);
    }

    /**
     * Creates a handler with optional asynchronous dispatch.
     * <p>
     * Convenience default: when {@code async} is {@code true}, a JVM shutdown hook is
     * registered automatically to close the handler and flush queued dispatch tasks.
     *
     * @param endpointUrl OTLP HTTP endpoint
     * @param async whether to use background dispatch
     * @param queueCapacity async queue capacity (0 or negative means unbounded)
     */
    public GrafanaMessageHandler(final @Nonnull String endpointUrl,
                                 final boolean async,
                                 final int queueCapacity) {
        this(new LogRecordFactoryImpl(),
                new ExportLogsServiceRequestLogRecordFormatter(),
                new GrafanaLogRecordDispatcher(endpointUrl),
                async, queueCapacity, true);
    }

    /**
     * Creates a handler with optional asynchronous dispatch and optional Basic authentication.
     * <p>
     * Convenience default: when {@code async} is {@code true}, a JVM shutdown hook is
     * registered automatically to close the handler and flush queued dispatch tasks.
     *
     * @param endpointUrl OTLP HTTP endpoint
     * @param username basic-auth username, nullable
     * @param password basic-auth password, nullable
     * @param async whether to use background dispatch
     * @param queueCapacity async queue capacity (0 or negative means unbounded)
     */
    public GrafanaMessageHandler(final @Nonnull String endpointUrl,
                                 final @Nullable String username,
                                 final @Nullable String password,
                                 final boolean async,
                                 final int queueCapacity) {
        this(new LogRecordFactoryImpl(),
                new ExportLogsServiceRequestLogRecordFormatter(),
                new GrafanaLogRecordDispatcher(endpointUrl, username, password),
                async, queueCapacity, true);
    }

    /**
     * Creates a handler with full dispatcher configuration and optional asynchronous dispatch.
     * <p>
     * Convenience default: when {@code async} is {@code true}, a JVM shutdown hook is
     * registered automatically to close the handler and flush queued dispatch tasks.
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
     */
    public GrafanaMessageHandler(final @Nonnull String endpointUrl,
                                 final @Nullable String username,
                                 final @Nullable String password,
                                 final @Nullable String bearerToken,
                                 final int connectTimeoutMillis,
                                 final int readTimeoutMillis,
                                 final @Nullable Map<String, String> additionalHeaders,
                                 final boolean async,
                                 final int queueCapacity) {
        this(endpointUrl, username, password, bearerToken, connectTimeoutMillis, readTimeoutMillis, additionalHeaders,
                async, queueCapacity, true);
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
     * @param registerShutdownHook whether to register a JVM shutdown hook that closes the handler;
     *                             when {@code false}, callers should invoke {@link #close()} at shutdown
     */
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
        this(endpointUrl, username, password, bearerToken, connectTimeoutMillis, readTimeoutMillis, additionalHeaders,
                async, queueCapacity, registerShutdownHook, new InMemoryHttpDispatchDurabilityStore(), System.err::println);
    }

    /**
     * Creates a handler with full dispatcher, durability, and dead-letter configuration.
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
     * @param durabilityStore pending-record durability policy
     * @param deadLetterConsumer consumer invoked for dead-letter records
     */
    public GrafanaMessageHandler(final @Nonnull String endpointUrl,
                                 final @Nullable String username,
                                 final @Nullable String password,
                                 final @Nullable String bearerToken,
                                 final int connectTimeoutMillis,
                                 final int readTimeoutMillis,
                                 final @Nullable Map<String, String> additionalHeaders,
                                 final boolean async,
                                 final int queueCapacity,
                                 final boolean registerShutdownHook,
                                 final @Nonnull HttpDispatchDurabilityStore durabilityStore,
                                 final @Nonnull Consumer<String> deadLetterConsumer) {
        this(new LogRecordFactoryImpl(),
                new ExportLogsServiceRequestLogRecordFormatter(),
                new GrafanaLogRecordDispatcher(endpointUrl, username, password, bearerToken,
                        connectTimeoutMillis, readTimeoutMillis, additionalHeaders),
                async, queueCapacity, registerShutdownHook, durabilityStore, deadLetterConsumer);
    }

    /**
     * Creates a handler with explicit dependencies and optional asynchronous dispatch.
     *
     * @param logRecordFactory factory used to create log records from incoming message calls
     * @param logRecordFormatter formatter used to encode each log record before dispatch
     * @param dispatcher dispatcher responsible for HTTP transport
     * @param async whether to use background dispatch
     * @param queueCapacity async queue capacity (0 or negative means unbounded)
     * @param registerShutdownHook whether to register a JVM shutdown hook that closes the handler;
     *                             when {@code false}, callers should invoke {@link #close()} at shutdown
     */
    public GrafanaMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                                 final @Nonnull LogRecordFormatter logRecordFormatter,
                                 final @Nonnull GrafanaLogRecordDispatcher dispatcher,
                                 final boolean async,
                                 final int queueCapacity,
                                 final boolean registerShutdownHook) {
        this(logRecordFactory, logRecordFormatter, (LogRecordDispatcher) dispatcher, async, queueCapacity,
                registerShutdownHook, new InMemoryHttpDispatchDurabilityStore(), System.err::println);
    }

    /**
     * Creates a handler with explicit dependencies and optional asynchronous dispatch.
     * <p>
     * Convenience default: when {@code async} is {@code true}, a JVM shutdown hook is
     * registered automatically to close the handler and flush queued dispatch tasks.
     *
     * @param logRecordFactory factory used to create log records from incoming message calls
     * @param logRecordFormatter formatter used to encode each log record before dispatch
     * @param dispatcher dispatcher responsible for HTTP transport
     * @param async whether to use background dispatch
     * @param queueCapacity async queue capacity (0 or negative means unbounded)
     */
    public GrafanaMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                                 final @Nonnull LogRecordFormatter logRecordFormatter,
                                 final @Nonnull GrafanaLogRecordDispatcher dispatcher,
                                 final boolean async,
                                 final int queueCapacity) {
        this(logRecordFactory, logRecordFormatter, (LogRecordDispatcher) dispatcher, async, queueCapacity, true);
    }

    /**
     * Creates a handler with explicit dependencies and optional asynchronous dispatch.
     * <p>
     * Convenience default: when {@code async} is {@code true}, a JVM shutdown hook is
     * registered automatically to close the handler and flush queued dispatch tasks.
     *
     * @param logRecordFactory factory used to create log records from incoming message calls
     * @param logRecordFormatter formatter used to encode each log record before dispatch
     * @param dispatcher dispatcher responsible for HTTP transport
     * @param async whether to use background dispatch
     * @param queueCapacity async queue capacity (0 or negative means unbounded)
     */
    public GrafanaMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                                 final @Nonnull LogRecordFormatter logRecordFormatter,
                                 final @Nonnull LogRecordDispatcher dispatcher,
                                 final boolean async,
                                 final int queueCapacity) {
        this(logRecordFactory, logRecordFormatter, dispatcher, async, queueCapacity, true);
    }

    /**
     * Creates a handler with explicit dependencies and optional asynchronous dispatch.
     *
     * @param logRecordFactory factory used to create log records from incoming message calls
     * @param logRecordFormatter formatter used to encode each log record before dispatch
     * @param dispatcher dispatcher responsible for HTTP transport
     * @param async whether to use background dispatch
     * @param queueCapacity async queue capacity (0 or negative means unbounded)
     * @param registerShutdownHook whether to register a JVM shutdown hook that closes the handler;
     *                             when {@code false}, callers should invoke {@link #close()} at shutdown
     */
    public GrafanaMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                                 final @Nonnull LogRecordFormatter logRecordFormatter,
                                 final @Nonnull LogRecordDispatcher dispatcher,
                                 final boolean async,
                                 final int queueCapacity,
                                 final boolean registerShutdownHook) {
        this(logRecordFactory, logRecordFormatter, dispatcher, async, queueCapacity, registerShutdownHook,
                new InMemoryHttpDispatchDurabilityStore(), System.err::println);
    }

    /**
     * Creates a handler with explicit dependencies, durability policy, and dead-letter consumer.
     *
     * @param logRecordFactory factory used to create log records from incoming message calls
     * @param logRecordFormatter formatter used to encode each log record before dispatch
     * @param dispatcher dispatcher responsible for HTTP transport
     * @param async whether to use background dispatch
     * @param queueCapacity async queue capacity (0 or negative means unbounded)
     * @param registerShutdownHook whether to register a JVM shutdown hook that closes the handler
     * @param durabilityStore pending-record durability policy
     * @param deadLetterConsumer consumer invoked for dead-letter records
     */
    public GrafanaMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                                 final @Nonnull LogRecordFormatter logRecordFormatter,
                                 final @Nonnull LogRecordDispatcher dispatcher,
                                 final boolean async,
                                 final int queueCapacity,
                                 final boolean registerShutdownHook,
                                 final @Nonnull HttpDispatchDurabilityStore durabilityStore,
                                 final @Nonnull Consumer<String> deadLetterConsumer) {
        super(logRecordFactory, logRecordFormatter, durabilityStore, deadLetterConsumer);
        this.dispatcher = Objects.requireNonNull(dispatcher, MessageHandlerResourceBundle.get("nullDispatcherProvided"));
        initializeOutputDispatch(async, queueCapacity, registerShutdownHook,
                "GrafanaMessageHandler-async", "GrafanaMessageHandler-shutdown");
    }

    @Override
    protected void closeOutput() {
        super.closeOutput();
        if (dispatcher instanceof java.io.Closeable) {
            try {
                ((java.io.Closeable) dispatcher).close();
            } catch (java.io.IOException e) {
                reportInnerFailure("closing Grafana dispatcher", e);
            }
        }
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
        try {
            LogRecord logRecord = createLogRecord(level, message);
            dispatchRecord(logRecord);
        } catch (RuntimeException runtimeException) {
            recordAndReportHttpMessageFailure(
                    runtimeException,
                    errorConsumer,
                    "grafanaDispatchError",
                    closeOnDispatchError,
                    "GrafanaMessageHandler-close-on-error"
            );
        }
    }

    @Override
    protected void handleExceptionInternal(final @Nonnull String message, final @Nonnull Throwable throwable) {
        try {
            LogRecord logRecord = createLogRecord(message, throwable);
            dispatchRecord(logRecord);
        } catch (RuntimeException runtimeException) {
            recordAndReportHttpMessageFailure(
                    runtimeException,
                    errorConsumer,
                    "grafanaDispatchError",
                    closeOnDispatchError,
                    "GrafanaMessageHandler-close-on-error"
            );
        }
    }

    private void dispatchRecord(final LogRecord logRecord) {
        dispatchHttpRecord(
                logRecord,
                (records, formatter) -> dispatcher.dispatchLogRecords(records, formatter),
                errorConsumer,
                "grafanaDispatchError",
                closeOnDispatchError,
                "GrafanaMessageHandler-close-on-error"
        );
    }
}
