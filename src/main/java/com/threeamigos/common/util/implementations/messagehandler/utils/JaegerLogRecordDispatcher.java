package com.threeamigos.common.util.implementations.messagehandler.utils;

import com.threeamigos.common.util.implementations.messagehandler.tracecontext.TraceContextGenerator;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import com.threeamigos.common.util.interfaces.messagehandler.transport.HttpTransport;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HTTP dispatcher for OTLP log payloads formatted as {@code ExportLogsServiceRequest} JSON.
 * <p>
 * This class is intentionally transport-focused and does not implement {@code MessageHandler} directly.
 * The goal is to make it reusable from a future {@code JaegerMessageHandler} (or any other component)
 * that already produces {@link LogRecord} instances.
 * <p>
 * Input records are serialized via
 * {@link com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ExportLogsServiceRequestLogRecordFormatter},
 * so each dispatched payload is a full OTLP JSON {@code ExportLogsServiceRequest} document.
 *
 * <h2>Jaeger Compatibility Note</h2>
 * <p>
 * Based on current Jaeger documentation (latest 2.x docs at the time of implementation),
 * Jaeger exposes OTLP ingestion mainly for traces (commonly {@code /v1/traces}).
 * The APIs page also states Jaeger does not store non-trace telemetry directly.
 * Therefore, posting OTLP logs to a plain Jaeger collector endpoint may be rejected.
 * <p>
 * For log ingestion, a common topology is:
 * <ol>
 *   <li>Application posts OTLP logs to an OpenTelemetry Collector endpoint (usually {@code /v1/logs}).</li>
 *   <li>The collector exports traces to Jaeger and logs to a log backend.</li>
 * </ol>
 * <p>
 * This dispatcher remains useful in both cases because the endpoint is fully configurable.
 *
 * <h2>Recommended Usage</h2>
 * <p>
 * For Jaeger-first observability, prefer exporting spans with {@code JaegerSpanDispatcher}
 * and attaching messages as span events before ending the span.
 * This produces clean parent/child traces in Jaeger and avoids "parent span ID ... is not in the trace"
 * warnings caused by synthetic spans.
 * <p>
 * Use this dispatcher mainly as a compatibility/fallback transport when logs must be sent directly
 * to a Jaeger-facing endpoint.
 * For log-native backends (for example Grafana Loki), use a log dispatcher such as
 * {@code GrafanaLogRecordDispatcher}.
 *
 * <h2>Authentication</h2>
 * <p>
 * Jaeger itself is often deployed without built-in collector auth and protected via reverse proxy/gateway.
 * For this reason, the dispatcher supports:
 * <ul>
 *   <li>HTTP Basic authentication (username and password)</li>
 *   <li>Bearer token authentication</li>
 *   <li>Additional custom headers</li>
 * </ul>
 * If both Basic and Bearer are configured, Bearer is used.
 *
 * <h2>HTTP Transport</h2>
 * <p>
 * When no explicit {@link HttpTransport} is provided, the dispatcher auto-detects the preferred
 * implementation at construction time via
 * {@link com.threeamigos.common.util.implementations.messagehandler.transport.HttpTransports#createPreferred()}:
 * if Apache HttpClient 4.x is on the classpath, {@code ApacheHttpClientTransport} (connection
 * pooling) is used; otherwise
 * {@link com.threeamigos.common.util.implementations.messagehandler.transport.HttpUrlConnectionTransport}
 * is used (zero extra dependencies).
 * <p>
 * When the dispatcher owns the transport (auto-detected), calling {@link #close()} releases any
 * pooled connections. This is typically handled automatically when the dispatcher is owned by
 * {@link com.threeamigos.common.util.implementations.messagehandler.JaegerMessageHandler},
 * which closes its dispatcher as part of its own {@code close()} lifecycle.
 *
 * <h2>Thread Safety</h2>
 * <p>
 * Instances are immutable after construction and safe for concurrent use.
 *
 * <h2>References</h2>
 * <ul>
 *   <li>OpenTelemetry OTLP/HTTP specification: default logs path {@code /v1/logs} and JSON encoding.</li>
 *   <li>Jaeger documentation (2.x): collector OTLP endpoints focused on traces, typically {@code /v1/traces}.</li>
 * </ul>
 *
 * @author Stefano Reksten
 */
public class JaegerLogRecordDispatcher extends AbstractLogRecordDispatcher {

    /**
     * Creates a dispatcher with no authentication and default timeouts.
     *
     * @param endpointUrl full HTTP endpoint to post to (for example {@code http://localhost:4318/v1/logs})
     */
    public JaegerLogRecordDispatcher(final @Nonnull String endpointUrl) {
        this(endpointUrl, null, null, null, DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS, null);
    }

    /**
     * Creates a dispatcher with optional Basic authentication and default timeouts.
     *
     * @param endpointUrl full HTTP endpoint
     * @param username    basic-auth username, nullable
     * @param password    basic-auth password, nullable
     */
    public JaegerLogRecordDispatcher(final @Nonnull String endpointUrl,
                                     final @Nullable String username,
                                     final @Nullable String password) {
        this(endpointUrl, username, password, null, DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS, null);
    }

    /**
     * Creates a dispatcher with full configuration.
     * <p>
     * {@code bearerToken} takes precedence over Basic authentication when both are provided.
     *
     * @param endpointUrl          full HTTP endpoint
     * @param username             basic-auth username, nullable
     * @param password             basic-auth password, nullable
     * @param bearerToken          bearer token, nullable
     * @param connectTimeoutMillis connect timeout in milliseconds, must be positive
     * @param readTimeoutMillis    read timeout in milliseconds, must be positive
     * @param additionalHeaders    optional extra headers
     */
    public JaegerLogRecordDispatcher(final @Nonnull String endpointUrl,
                                     final @Nullable String username,
                                     final @Nullable String password,
                                     final @Nullable String bearerToken,
                                     final int connectTimeoutMillis,
                                     final int readTimeoutMillis,
                                     final @Nullable Map<String, String> additionalHeaders) {
        this(endpointUrl, username, password, bearerToken, connectTimeoutMillis, readTimeoutMillis,
                additionalHeaders, null);
    }

    /**
     * Creates a dispatcher with full configuration and an optional custom HTTP transport.
     * <p>
     * When {@code httpTransport} is {@code null}, the preferred transport is selected
     * automatically via
     * {@link com.threeamigos.common.util.implementations.messagehandler.transport.HttpTransports#createPreferred()}:
     * {@code ApacheHttpClientTransport} if Apache HttpClient is on the classpath,
     * otherwise {@link com.threeamigos.common.util.implementations.messagehandler.transport.HttpUrlConnectionTransport}.
     * <p>
     * When a non-null transport is supplied, the caller owns its lifecycle and must close it
     * independently. The dispatcher will not close a caller-provided transport.
     *
     * @param endpointUrl          full HTTP endpoint
     * @param username             basic-auth username, nullable
     * @param password             basic-auth password, nullable
     * @param bearerToken          bearer token, nullable
     * @param connectTimeoutMillis connect timeout in milliseconds, must be positive
     * @param readTimeoutMillis    read timeout in milliseconds, must be positive
     * @param additionalHeaders    optional extra headers
     * @param httpTransport        transport to use for HTTP calls; {@code null} selects automatically
     */
    public JaegerLogRecordDispatcher(final @Nonnull String endpointUrl,
                                     final @Nullable String username,
                                     final @Nullable String password,
                                     final @Nullable String bearerToken,
                                     final int connectTimeoutMillis,
                                     final int readTimeoutMillis,
                                     final @Nullable Map<String, String> additionalHeaders,
                                     final @Nullable HttpTransport httpTransport) {
        super(endpointUrl, username, password, bearerToken, connectTimeoutMillis, readTimeoutMillis,
                additionalHeaders, httpTransport);
    }

    @Override
    protected String getUserAgent() {
        return "common-utils-messagehandler/jaeger-logrecord-dispatcher";
    }

    @Override
    protected boolean usesSpecialEndpointFormat() {
        return shouldTransformLogsIntoTraceSpanEvents();
    }

    @Override
    protected String buildSpecialPayload(final LogRecord logRecord) {
        return toExportTracesRequestJson(logRecord);
    }

    private boolean shouldTransformLogsIntoTraceSpanEvents() {
        String path = getEndpoint().getPath();
        if (path == null) {
            return false;
        }
        String normalizedPath = path.trim().toLowerCase(Locale.ROOT);
        return normalizedPath.endsWith("/v1/traces");
    }

    private String toExportTracesRequestJson(final LogRecord logRecord) {
        String traceId = normalizeHexId(logRecord.getTraceId(), 32);
        if (traceId == null) {
            traceId = TraceContextGenerator.generateTraceId();
        }
        String parentSpanId = normalizeHexId(logRecord.getSpanId(), 16);
        String generatedSpanId = TraceContextGenerator.generateParentId();

        Instant timestamp = logRecord.getTimestamp() == null ? Instant.now() : logRecord.getTimestamp();
        String eventTimeNanos = toUnsignedNanosString(timestamp);
        String endTimeNanos = toUnsignedNanosString(timestamp.plusNanos(1L));

        String message = extractBodyAsString(logRecord);
        String severity = normalizeNullable(logRecord.getSeverityText());
        String scopeName = resolveScopeName(logRecord.getInstrumentationScope());
        String scopeVersion = resolveScopeVersion(logRecord.getInstrumentationScope());
        String serviceName = resolveServiceName(logRecord.getResource(), scopeName);

        String eventName = normalizeNullable(logRecord.getEventName());
        if (eventName == null) {
            eventName = "log";
        }
        String spanName = severity == null ? "log-event" : "log-" + severity.toLowerCase(Locale.ROOT);

        StringBuilder sb = new StringBuilder(768);
        sb.append("{\"resourceSpans\":[{\"resource\":{\"attributes\":[");
        appendStringKeyValueAttribute(sb, "service.name", serviceName);
        sb.append("]},\"scopeSpans\":[{\"scope\":{");
        boolean firstScopeField = true;
        if (scopeName != null) {
            sb.append("\"name\":\"").append(escapeJson(scopeName)).append('"');
            firstScopeField = false;
        }
        if (scopeVersion != null) {
            if (!firstScopeField) {
                sb.append(',');
            }
            sb.append("\"version\":\"").append(escapeJson(scopeVersion)).append('"');
        }
        sb.append("},\"spans\":[{\"traceId\":\"").append(traceId)
                .append("\",\"spanId\":\"").append(generatedSpanId).append('"');
        if (parentSpanId != null) {
            sb.append(",\"parentSpanId\":\"").append(parentSpanId).append('"');
        }
        sb.append(",\"name\":\"").append(escapeJson(spanName))
                .append("\",\"startTimeUnixNano\":\"").append(eventTimeNanos)
                .append("\",\"endTimeUnixNano\":\"").append(endTimeNanos)
                .append("\",\"events\":[{\"timeUnixNano\":\"").append(eventTimeNanos)
                .append("\",\"name\":\"").append(escapeJson(eventName))
                .append("\",\"attributes\":[");
        appendStringKeyValueAttribute(sb, "log.message", message);
        if (severity != null) {
            sb.append(',');
            appendStringKeyValueAttribute(sb, "log.severity", severity);
        }
        sb.append("]}]}]}]}]}");
        return sb.toString();
    }

    private static String resolveScopeName(final InstrumentationScope scope) {
        if (scope == null) {
            return null;
        }
        return normalizeNullable(scope.getName());
    }

    private static String resolveScopeVersion(final InstrumentationScope scope) {
        if (scope == null) {
            return null;
        }
        return normalizeNullable(scope.getVersion());
    }

    private static String resolveServiceName(final Resource resource, final String scopeName) {
        if (resource != null) {
            List<KeyValue> attributes = resource.getAttributes();
            if (attributes != null) {
                for (KeyValue keyValue : attributes) {
                    if (keyValue == null || keyValue.getKey() == null || keyValue.getValue() == null) {
                        continue;
                    }
                    if ("service.name".equals(keyValue.getKey())) {
                        String serviceName = normalizeNullable(keyValue.getValue().asString());
                        if (serviceName != null) {
                            return serviceName;
                        }
                    }
                }
            }
        }
        if (scopeName != null) {
            return scopeName;
        }
        return "common-utils-messagehandler";
    }

    private static String normalizeHexId(final String id, final int expectedLength) {
        String normalized = normalizeNullable(id);
        if (normalized == null || normalized.length() != expectedLength) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        boolean allZero = true;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean isHexDigit = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!isHexDigit) {
                return null;
            }
            if (c != '0') {
                allZero = false;
            }
        }
        return allZero ? null : lower;
    }

    private static void appendStringKeyValueAttribute(final StringBuilder sb,
                                                      final String key,
                                                      final String value) {
        sb.append("{\"key\":\"").append(escapeJson(key))
                .append("\",\"value\":{\"stringValue\":\"")
                .append(escapeJson(value == null ? "" : value)).append("\"}}");
    }
}
