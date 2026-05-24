package com.threeamigos.common.util.implementations.messagehandler.utils;

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
 * HTTP dispatcher for OTLP log payloads sent to Grafana-compatible OTLP logs endpoints.
 * <p>
 * Records are serialized as OTLP {@code ExportLogsServiceRequest} JSON by default. When the
 * endpoint path ends with {@code /loki/api/v1/push}, the dispatcher automatically switches to
 * Grafana Loki push JSON format instead, so no manual format selection is required.
 *
 * <h2>Authentication</h2>
 * <p>
 * Supports HTTP Basic authentication (username/password) and Bearer token authentication.
 * When both are provided, Bearer token takes precedence. Additional custom headers can also
 * be specified to support reverse-proxy auth or Grafana Cloud tenancy headers
 * (e.g. {@code X-Scope-OrgID}).
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
 * {@link com.threeamigos.common.util.implementations.messagehandler.GrafanaMessageHandler},
 * which closes its dispatcher as part of its own {@code close()} lifecycle.
 *
 * <h2>Thread Safety</h2>
 * <p>
 * Instances are immutable after construction and safe for concurrent use.
 *
 * @author Stefano Reksten
 */
public class GrafanaLogRecordDispatcher extends AbstractLogRecordDispatcher {

    /**
     * Creates a dispatcher with no authentication and default timeouts.
     *
     * @param endpointUrl full HTTP endpoint to post to (for example {@code http://localhost:3100/loki/api/v1/push})
     */
    public GrafanaLogRecordDispatcher(final @Nonnull String endpointUrl) {
        this(endpointUrl, null, null, null, DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS, null);
    }

    /**
     * Creates a dispatcher with optional Basic authentication and default timeouts.
     *
     * @param endpointUrl full HTTP endpoint
     * @param username    basic-auth username; {@code null} to skip Basic auth
     * @param password    basic-auth password; {@code null} to skip Basic auth
     */
    public GrafanaLogRecordDispatcher(final @Nonnull String endpointUrl,
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
     * @param username             basic-auth username; nullable
     * @param password             basic-auth password; nullable
     * @param bearerToken          bearer token; nullable
     * @param connectTimeoutMillis connect timeout in milliseconds; must be positive
     * @param readTimeoutMillis    read timeout in milliseconds; must be positive
     * @param additionalHeaders    optional extra headers; {@code null} is treated as empty
     */
    public GrafanaLogRecordDispatcher(final @Nonnull String endpointUrl,
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
    public GrafanaLogRecordDispatcher(final @Nonnull String endpointUrl,
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
        return "common-utils-messagehandler/grafana-logrecord-dispatcher";
    }

    @Override
    protected boolean usesSpecialEndpointFormat() {
        return shouldUseLokiPushFormat();
    }

    @Override
    protected String buildSpecialPayload(final LogRecord logRecord) {
        return toLokiPushPayload(logRecord);
    }

    private boolean shouldUseLokiPushFormat() {
        String path = getEndpoint().getPath();
        if (path == null) {
            return false;
        }
        String normalizedPath = path.trim().toLowerCase(Locale.ROOT);
        return normalizedPath.endsWith("/loki/api/v1/push");
    }

    private String toLokiPushPayload(final LogRecord logRecord) {
        String serviceName = resolveServiceName(logRecord.getResource(), logRecord.getInstrumentationScope());
        String severity = normalizeNullable(logRecord.getSeverityText());
        String message = extractBodyAsString(logRecord);
        String traceId = normalizeNullable(logRecord.getTraceId());
        String spanId = normalizeNullable(logRecord.getSpanId());
        String timestampNs = toUnsignedNanosString(logRecord.getTimestamp());

        StringBuilder lineBuilder = new StringBuilder(192);
        lineBuilder.append("{\"message\":\"").append(escapeJson(message)).append('"');
        if (severity != null) {
            lineBuilder.append(",\"severity\":\"").append(escapeJson(severity)).append('"');
        }
        if (traceId != null) {
            lineBuilder.append(",\"trace_id\":\"").append(escapeJson(traceId)).append('"');
        }
        if (spanId != null) {
            lineBuilder.append(",\"span_id\":\"").append(escapeJson(spanId)).append('"');
        }
        lineBuilder.append('}');

        StringBuilder payloadBuilder = new StringBuilder(320);
        payloadBuilder.append("{\"streams\":[{\"stream\":{");
        payloadBuilder.append("\"service_name\":\"").append(escapeJson(serviceName)).append('"');
        if (severity != null) {
            payloadBuilder.append(",\"severity\":\"").append(escapeJson(severity)).append('"');
        }
        payloadBuilder.append("},\"values\":[[\"")
                .append(timestampNs)
                .append("\",\"")
                .append(escapeJson(lineBuilder.toString()))
                .append("\"]]}]}");
        return payloadBuilder.toString();
    }

    private static String resolveServiceName(final Resource resource, final InstrumentationScope scope) {
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
        if (scope != null) {
            String scopeName = normalizeNullable(scope.getName());
            if (scopeName != null) {
                return scopeName;
            }
        }
        return "common-utils-messagehandler";
    }
}
