package com.threeamigos.common.util.interfaces.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.GrafanaMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.JaegerMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.JULMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.Log4JMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.SLF4JMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.SwingMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.VoidMessageHandler;

import java.io.File;
import java.util.Map;

/**
 * OpenTelemetry-like tracer API.
 * <p>
 * Specification references:
 * <ul>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/trace/api/">OpenTelemetry Trace API</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/trace/api/#tracer">OpenTelemetry Trace API: Tracer</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/trace/api/#span-operations">OpenTelemetry Trace API:
 *   Span operations</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/trace/api/#enabled">OpenTelemetry Trace API: Enabled</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/trace/api/#concurrency-requirements">OpenTelemetry
 *   Trace API: Concurrency requirements</a></li>
 * </ul>
 * Implementations are expected to be safe for concurrent use by default.
 *
 * @author Stefano Reksten
 */
public interface Tracer {

    /**
     * Creates and starts a new span with the given name.
     * <p>
     * If no explicit parent is provided, the implementation may start a root span.
     *
     * @param name span name
     * @return a started span
     */
    Span createSpan(String name);

    /**
     * Creates and starts a new span with an explicit parent span context.
     * <p>
     * If the parent is null or invalid, the implementation may start a root span.
     *
     * @param name span name
     * @param parentSpanContext explicit parent span context
     * @return a started span
     */
    Span createSpan(String name, SpanContext parentSpanContext);

    /**
     * Returns the effective instrumentation scope for this tracer.
     * <p>
     * Implementations may derive this value from a provider and/or tracer-local configuration.
     *
     * @return effective instrumentation scope, or {@code null} when unavailable
     */
    InstrumentationScope getInstrumentationScope();

    /**
     * Returns a log-record factory already configured for this tracer scope.
     * <p>
     * Implementations should enrich records with tracer/provider resource and
     * correlation context when available.
     *
     * @return tracer-aware log-record factory
     */
    LogRecordFactory getLogRecordFactory();

    /**
     * Creates a console-backed handler enriched with this tracer's scope/resource/correlation context.
     *
     * @return a tracer-aware console message handler
     */
    ConsoleMessageHandler getConsoleMessageHandler();

    /**
     * Creates a console-backed handler enriched with this tracer context and filtered by the provided rules.
     * <p>
     * If {@code filter} is {@code null}, implementations may fall back to tracer-level filter configuration.
     *
     * @param filter per-handler filter, nullable
     * @return a tracer-aware console message handler
     */
    ConsoleMessageHandler getConsoleMessageHandler(Filter filter);

    /**
     * Creates a console-backed handler enriched with this tracer context and using the provided formatter.
     *
     * @param logRecordFormatter formatter used to render produced log records
     * @return a tracer-aware console message handler
     */
    ConsoleMessageHandler getConsoleMessageHandler(LogRecordFormatter logRecordFormatter);

    /**
     * Creates a console-backed handler enriched with this tracer context, using the provided formatter,
     * and filtered by the provided rules.
     * <p>
     * If {@code filter} is {@code null}, implementations may fall back to tracer-level filter configuration.
     *
     * @param logRecordFormatter formatter used to render produced log records
     * @param filter per-handler filter, nullable
     * @return a tracer-aware console message handler
     */
    ConsoleMessageHandler getConsoleMessageHandler(LogRecordFormatter logRecordFormatter, Filter filter);

    /**
     * Creates a file-backed handler enriched with this tracer context.
     * <p>
     * If {@code filePath} is {@code null} or blank, implementations may use a provider default file path.
     *
     * @param filePath target file path, nullable
     * @return a tracer-aware file message handler
     */
    FileMessageHandler getFileMessageHandler(String filePath);

    /**
     * Creates a file-backed handler enriched with this tracer context and filtered by the provided rules.
     * <p>
     * If {@code filePath} is {@code null} or blank, implementations may use a provider default file path.
     * If {@code filter} is {@code null}, implementations may fall back to tracer-level filter configuration.
     *
     * @param filePath target file path, nullable
     * @param filter per-handler filter, nullable
     * @return a tracer-aware file message handler
     */
    FileMessageHandler getFileMessageHandler(String filePath, Filter filter);

    /**
     * Creates a file-backed handler enriched with this tracer context, using the provided formatter,
     * and the provided file path.
     * <p>
     * If {@code filePath} is {@code null} or blank, implementations may use a provider default file path.
     *
     * @param filePath target file path, nullable
     * @param logRecordFormatter formatter used to render produced log records
     * @return a tracer-aware file message handler
     */
    FileMessageHandler getFileMessageHandler(String filePath, LogRecordFormatter logRecordFormatter);

    /**
     * Creates a file-backed handler enriched with this tracer context, using the provided formatter,
     * and filtered by the provided rules.
     * <p>
     * If {@code filePath} is {@code null} or blank, implementations may use a provider default file path.
     * If {@code filter} is {@code null}, implementations may fall back to tracer-level filter configuration.
     *
     * @param filePath target file path, nullable
     * @param logRecordFormatter formatter used to render produced log records
     * @param filter per-handler filter, nullable
     * @return a tracer-aware file message handler
     */
    FileMessageHandler getFileMessageHandler(String filePath, LogRecordFormatter logRecordFormatter, Filter filter);

    /**
     * Creates a file-backed handler enriched with this tracer context.
     *
     * @param file target file reference
     * @return a tracer-aware file message handler
     */
    FileMessageHandler getFileMessageHandler(File file);

    /**
     * Creates a file-backed handler enriched with this tracer context and filtered by the provided rules.
     * <p>
     * If {@code filter} is {@code null}, implementations may fall back to tracer-level filter configuration.
     *
     * @param file target file reference
     * @param filter per-handler filter, nullable
     * @return a tracer-aware file message handler
     */
    FileMessageHandler getFileMessageHandler(File file, Filter filter);

    /**
     * Creates a file-backed handler enriched with this tracer context, using the provided formatter,
     * and the provided file reference.
     *
     * @param file target file reference
     * @param logRecordFormatter formatter used to render produced log records
     * @return a tracer-aware file message handler
     */
    FileMessageHandler getFileMessageHandler(File file, LogRecordFormatter logRecordFormatter);

    /**
     * Creates a file-backed handler enriched with this tracer context, using the provided formatter,
     * and filtered by the provided rules.
     * <p>
     * If {@code filter} is {@code null}, implementations may fall back to tracer-level filter configuration.
     *
     * @param file target file reference
     * @param logRecordFormatter formatter used to render produced log records
     * @param filter per-handler filter, nullable
     * @return a tracer-aware file message handler
     */
    FileMessageHandler getFileMessageHandler(File file, LogRecordFormatter logRecordFormatter, Filter filter);

    /**
     * Creates an in-memory handler enriched with this tracer context.
     *
     * @return a tracer-aware in-memory message handler
     */
    InMemoryMessageHandler getInMemoryMessageHandler();

    /**
     * Creates an in-memory handler enriched with this tracer context and filtered by the provided rules.
     * <p>
     * If {@code filter} is {@code null}, implementations may fall back to tracer-level filter configuration.
     *
     * @param filter per-handler filter, nullable
     * @return a tracer-aware in-memory message handler
     */
    InMemoryMessageHandler getInMemoryMessageHandler(Filter filter);

    /**
     * Creates a JUL-backed handler bound to the given logger and enriched with this tracer context.
     *
     * @param logger target JUL logger
     * @return a tracer-aware JUL message handler
     */
    JULMessageHandler getJULMessageHandler(java.util.logging.Logger logger);

    /**
     * Creates a JUL-backed handler bound to the given logger, enriched with this tracer context,
     * and filtered by the provided rules.
     * <p>
     * If {@code filter} is {@code null}, implementations may fall back to tracer-level filter configuration.
     *
     * @param logger target JUL logger
     * @param filter per-handler filter, nullable
     * @return a tracer-aware JUL message handler
     */
    JULMessageHandler getJULMessageHandler(java.util.logging.Logger logger, Filter filter);

    /**
     * Creates a Log4J-backed handler bound to the given logger and enriched with this tracer context.
     *
     * @param logger target Log4J logger
     * @return a tracer-aware Log4J message handler
     */
    Log4JMessageHandler getLog4JMessageHandler(org.apache.logging.log4j.Logger logger);

    /**
     * Creates a Log4J-backed handler bound to the given logger, enriched with this tracer context,
     * and filtered by the provided rules.
     * <p>
     * If {@code filter} is {@code null}, implementations may fall back to tracer-level filter configuration.
     *
     * @param logger target Log4J logger
     * @param filter per-handler filter, nullable
     * @return a tracer-aware Log4J message handler
     */
    Log4JMessageHandler getLog4JMessageHandler(org.apache.logging.log4j.Logger logger, Filter filter);

    /**
     * Creates an SLF4J-backed handler bound to the given logger and enriched with this tracer context.
     *
     * @param logger target SLF4J logger
     * @return a tracer-aware SLF4J message handler
     */
    SLF4JMessageHandler getSLF4JMessageHandler(org.slf4j.Logger logger);

    /**
     * Creates an SLF4J-backed handler bound to the given logger, enriched with this tracer context,
     * and filtered by the provided rules.
     * <p>
     * If {@code filter} is {@code null}, implementations may fall back to tracer-level filter configuration.
     *
     * @param logger target SLF4J logger
     * @param filter per-handler filter, nullable
     * @return a tracer-aware SLF4J message handler
     */
    SLF4JMessageHandler getSLF4JMessageHandler(org.slf4j.Logger logger, Filter filter);

    /**
     * Creates a Swing-backed handler enriched with this tracer context.
     *
     * @return a tracer-aware Swing message handler
     */
    SwingMessageHandler getSwingMessageHandler();

    /**
     * Creates a Swing-backed handler enriched with this tracer context and filtered by the provided rules.
     * <p>
     * If {@code filter} is {@code null}, implementations may fall back to tracer-level filter configuration.
     *
     * @param filter per-handler filter, nullable
     * @return a tracer-aware Swing message handler
     */
    SwingMessageHandler getSwingMessageHandler(Filter filter);

    /**
     * Creates a Jaeger-oriented handler that exports through HTTP to the given endpoint.
     * <p>
     * The endpoint must be an ingestion endpoint, not the Jaeger UI endpoint.
     * Typical local OTLP ingestion endpoint is {@code http://localhost:4318/v1/logs}
     * (usually via OpenTelemetry Collector/Alloy).
     *
     * @param endpointUrl target HTTP endpoint URL
     * @return a tracer-aware Jaeger message handler
     */
    JaegerMessageHandler getJaegerMessageHandler(String endpointUrl);

    /**
     * Creates a Jaeger-oriented handler that exports through HTTP to the given endpoint and applies
     * per-handler filtering.
     * <p>
     * If {@code filter} is {@code null}, implementations may fall back to tracer-level filter configuration.
     *
     * @param endpointUrl target HTTP endpoint URL
     * @param filter per-handler filter, nullable
     * @return a tracer-aware Jaeger message handler
     */
    JaegerMessageHandler getJaegerMessageHandler(String endpointUrl, Filter filter);

    /**
     * Creates a Jaeger-oriented handler configured with optional HTTP Basic authentication.
     * <p>
     * {@code username} and {@code password} must be provided together (or both null).
     *
     * @param endpointUrl target HTTP endpoint URL
     * @param username basic-auth username, nullable
     * @param password basic-auth password, nullable
     * @return a tracer-aware Jaeger message handler
     */
    JaegerMessageHandler getJaegerMessageHandler(String endpointUrl, String username, String password);

    /**
     * Creates a Jaeger-oriented handler with full HTTP transport configuration.
     * <p>
     * Notes for endpoint selection:
     * <ul>
     *   <li>{@code http://localhost:4318/v1/logs}: OTLP logs ingestion path (common collector endpoint).</li>
     *   <li>{@code http://localhost:4318/v1/traces}: trace ingestion path used by some Jaeger pipelines.</li>
     * </ul>
     * Authentication rules:
     * <ul>
     *   <li>If {@code bearerToken} is provided, it takes precedence over basic auth.</li>
     *   <li>{@code username} and {@code password} must be provided together (or both null).</li>
     * </ul>
     *
     * @param endpointUrl target HTTP endpoint URL
     * @param username basic-auth username, nullable
     * @param password basic-auth password, nullable
     * @param bearerToken bearer token for {@code Authorization: Bearer ...}, nullable
     * @param connectTimeoutMillis connection timeout in milliseconds; must be {@code > 0}
     * @param readTimeoutMillis read timeout in milliseconds; must be {@code > 0}
     * @param additionalHeaders optional custom HTTP headers, nullable
     * @param async when {@code true}, dispatches on a background worker thread
     * @param queueCapacity async queue capacity; {@code <= 0} means unbounded queue
     * @param registerShutdownHook when {@code true}, registers a JVM shutdown hook to close the handler
     * @return a tracer-aware Jaeger message handler
     */
    JaegerMessageHandler getJaegerMessageHandler(String endpointUrl,
                                                 String username,
                                                 String password,
                                                 String bearerToken,
                                                 int connectTimeoutMillis,
                                                 int readTimeoutMillis,
                                                 Map<String, String> additionalHeaders,
                                                 boolean async,
                                                 int queueCapacity,
                                                 boolean registerShutdownHook);

    /**
     * Creates a Jaeger-oriented handler with full HTTP transport configuration and per-handler filtering.
     * <p>
     * If {@code filter} is {@code null}, implementations may fall back to tracer-level filter configuration.
     *
     * @param endpointUrl target HTTP endpoint URL
     * @param username basic-auth username, nullable
     * @param password basic-auth password, nullable
     * @param bearerToken bearer token for {@code Authorization: Bearer ...}, nullable
     * @param connectTimeoutMillis connection timeout in milliseconds; must be {@code > 0}
     * @param readTimeoutMillis read timeout in milliseconds; must be {@code > 0}
     * @param additionalHeaders optional custom HTTP headers, nullable
     * @param async when {@code true}, dispatches on a background worker thread
     * @param queueCapacity async queue capacity; {@code <= 0} means unbounded queue
     * @param registerShutdownHook when {@code true}, registers a JVM shutdown hook to close the handler
     * @param filter per-handler filter, nullable
     * @return a tracer-aware Jaeger message handler
     */
    JaegerMessageHandler getJaegerMessageHandler(String endpointUrl,
                                                 String username,
                                                 String password,
                                                 String bearerToken,
                                                 int connectTimeoutMillis,
                                                 int readTimeoutMillis,
                                                 Map<String, String> additionalHeaders,
                                                 boolean async,
                                                 int queueCapacity,
                                                 boolean registerShutdownHook,
                                                 Filter filter);

    /**
     * Creates a Grafana-oriented handler that exports through HTTP to the given endpoint.
     * <p>
     * Supported ingestion endpoints depend on your topology, commonly:
     * <ul>
     *   <li>{@code http://localhost:4318/v1/logs} for OTLP/HTTP ingestion (Collector/Alloy).</li>
     *   <li>{@code http://localhost:3100/loki/api/v1/push} for direct Loki push ingestion.</li>
     * </ul>
     *
     * @param endpointUrl target HTTP endpoint URL
     * @return a tracer-aware Grafana message handler
     */
    GrafanaMessageHandler getGrafanaMessageHandler(String endpointUrl);

    /**
     * Creates a Grafana-oriented handler that exports through HTTP to the given endpoint and applies
     * per-handler filtering.
     * <p>
     * If {@code filter} is {@code null}, implementations may fall back to tracer-level filter configuration.
     *
     * @param endpointUrl target HTTP endpoint URL
     * @param filter per-handler filter, nullable
     * @return a tracer-aware Grafana message handler
     */
    GrafanaMessageHandler getGrafanaMessageHandler(String endpointUrl, Filter filter);

    /**
     * Creates a Grafana-oriented handler configured with optional HTTP Basic authentication.
     * <p>
     * {@code username} and {@code password} must be provided together (or both null).
     *
     * @param endpointUrl target HTTP endpoint URL
     * @param username basic-auth username, nullable
     * @param password basic-auth password, nullable
     * @return a tracer-aware Grafana message handler
     */
    GrafanaMessageHandler getGrafanaMessageHandler(String endpointUrl, String username, String password);

    /**
     * Creates a Grafana-oriented handler with full HTTP transport configuration.
     * <p>
     * Authentication rules:
     * <ul>
     *   <li>If {@code bearerToken} is provided, it takes precedence over basic auth.</li>
     *   <li>{@code username} and {@code password} must be provided together (or both null).</li>
     * </ul>
     *
     * @param endpointUrl target HTTP endpoint URL
     * @param username basic-auth username, nullable
     * @param password basic-auth password, nullable
     * @param bearerToken bearer token for {@code Authorization: Bearer ...}, nullable
     * @param connectTimeoutMillis connection timeout in milliseconds; must be {@code > 0}
     * @param readTimeoutMillis read timeout in milliseconds; must be {@code > 0}
     * @param additionalHeaders optional custom HTTP headers, nullable
     * @param async when {@code true}, dispatches on a background worker thread
     * @param queueCapacity async queue capacity; {@code <= 0} means unbounded queue
     * @param registerShutdownHook when {@code true}, registers a JVM shutdown hook to close the handler
     * @return a tracer-aware Grafana message handler
     */
    GrafanaMessageHandler getGrafanaMessageHandler(String endpointUrl,
                                                   String username,
                                                   String password,
                                                   String bearerToken,
                                                   int connectTimeoutMillis,
                                                   int readTimeoutMillis,
                                                   Map<String, String> additionalHeaders,
                                                   boolean async,
                                                   int queueCapacity,
                                                   boolean registerShutdownHook);

    /**
     * Creates a Grafana-oriented handler with full HTTP transport configuration and per-handler filtering.
     * <p>
     * If {@code filter} is {@code null}, implementations may fall back to tracer-level filter configuration.
     *
     * @param endpointUrl target HTTP endpoint URL
     * @param username basic-auth username, nullable
     * @param password basic-auth password, nullable
     * @param bearerToken bearer token for {@code Authorization: Bearer ...}, nullable
     * @param connectTimeoutMillis connection timeout in milliseconds; must be {@code > 0}
     * @param readTimeoutMillis read timeout in milliseconds; must be {@code > 0}
     * @param additionalHeaders optional custom HTTP headers, nullable
     * @param async when {@code true}, dispatches on a background worker thread
     * @param queueCapacity async queue capacity; {@code <= 0} means unbounded queue
     * @param registerShutdownHook when {@code true}, registers a JVM shutdown hook to close the handler
     * @param filter per-handler filter, nullable
     * @return a tracer-aware Grafana message handler
     */
    GrafanaMessageHandler getGrafanaMessageHandler(String endpointUrl,
                                                   String username,
                                                   String password,
                                                   String bearerToken,
                                                   int connectTimeoutMillis,
                                                   int readTimeoutMillis,
                                                   Map<String, String> additionalHeaders,
                                                   boolean async,
                                                   int queueCapacity,
                                                   boolean registerShutdownHook,
                                                   Filter filter);

    /**
     * Creates a no-op handler that discards all messages.
     * <p>
     * Useful when a caller needs a {@link com.threeamigos.common.util.interfaces.messagehandler.MessageHandler}
     * instance but wants to suppress output.
     *
     * @return a void/no-op message handler
     */
    VoidMessageHandler getVoidMessageHandler();

    /**
     * Returns whether this tracer is currently enabled for span creation.
     * <p>
     * Callers should not cache this value because it may change over time.
     *
     * @return {@code true} when tracing is enabled for this tracer
     */
    boolean isEnabled();
}
