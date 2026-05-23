package com.threeamigos.common.util.implementations.messagehandler.utils;

import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ExportLogsServiceRequestLogRecordFormatter;
import com.threeamigos.common.util.implementations.messagehandler.tracecontext.TraceContextGenerator;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordDispatcher;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP dispatcher for OTLP log payloads formatted as {@code ExportLogsServiceRequest} JSON.
 * <p>
 * This class is intentionally transport-focused and does not implement {@code MessageHandler} directly.
 * The goal is to make it reusable from a future {@code JaegerMessageHandler} (or any other component)
 * that already produces {@link LogRecord} instances.
 * <p>
 * Input records are serialized via
 * {@link ExportLogsServiceRequestLogRecordFormatter}, so each dispatched payload is a full
 * OTLP JSON {@code ExportLogsServiceRequest} document.
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
 * <h2>Thread Safety</h2>
 * <p>
 * Instances are immutable after construction and safe for concurrent use.
 * Each dispatch call opens a dedicated {@link HttpURLConnection}; when streams are fully consumed
 * and closed, the JDK keep-alive cache can reuse sockets for subsequent requests.
 *
 * <h2>References</h2>
 * <ul>
 *   <li>OpenTelemetry OTLP/HTTP specification: default logs path {@code /v1/logs} and JSON encoding.</li>
 *   <li>Jaeger documentation (2.x): collector OTLP endpoints focused on traces, typically {@code /v1/traces}.</li>
 * </ul>
 *
 * @author Stefano Reksten
 */
public class JaegerLogRecordDispatcher implements LogRecordDispatcher {

    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 10_000;
    private static final String APPLICATION_JSON = "application/json";
    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);

    private final URL endpoint;
    private final String username;
    private final String password;
    private final String bearerToken;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final Map<String, String> additionalHeaders;
    private final ExportLogsServiceRequestLogRecordFormatter formatter;

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
     * @param username basic-auth username, nullable
     * @param password basic-auth password, nullable
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
     * @param endpointUrl full HTTP endpoint
     * @param username basic-auth username, nullable
     * @param password basic-auth password, nullable
     * @param bearerToken bearer token, nullable
     * @param connectTimeoutMillis connect timeout in milliseconds, must be positive
     * @param readTimeoutMillis read timeout in milliseconds, must be positive
     * @param additionalHeaders optional extra headers
     */
    public JaegerLogRecordDispatcher(final @Nonnull String endpointUrl,
                                     final @Nullable String username,
                                     final @Nullable String password,
                                     final @Nullable String bearerToken,
                                     final int connectTimeoutMillis,
                                     final int readTimeoutMillis,
                                     final @Nullable Map<String, String> additionalHeaders) {
        this.endpoint = parseEndpoint(endpointUrl);
        this.username = normalizeNullable(username);
        this.password = normalizeNullable(password);
        this.bearerToken = normalizeNullable(bearerToken);
        this.connectTimeoutMillis = requirePositive(connectTimeoutMillis, "connectTimeoutMillis");
        this.readTimeoutMillis = requirePositive(readTimeoutMillis, "readTimeoutMillis");
        this.additionalHeaders = toImmutableHeaders(additionalHeaders);
        this.formatter = new ExportLogsServiceRequestLogRecordFormatter();
        validateAuthConfiguration();
    }

    /**
     * Formats and dispatches a {@link LogRecord}.
     *
     * @param logRecord record to send
     * @return HTTP dispatch result
     * @throws IOException network/transport errors
     */
    public DispatchResult dispatch(final @Nonnull LogRecord logRecord) throws IOException {
        return dispatch(logRecord, formatter);
    }

    /**
     * Formats and dispatches a {@link LogRecord} using the provided formatter.
     * <p>
     * When the configured endpoint path targets OTLP traces ({@code /v1/traces}),
     * the dispatcher transforms the log record into a synthetic span event payload
     * so that the message becomes queryable in Jaeger.
     *
     * @param logRecord record to send
     * @param logRecordFormatter formatter used for standard log export endpoints
     * @return HTTP dispatch result
     * @throws IOException network/transport errors
     */
    public DispatchResult dispatch(final @Nonnull LogRecord logRecord,
                                   final @Nonnull LogRecordFormatter logRecordFormatter) throws IOException {
        Objects.requireNonNull(logRecord, "logRecord must not be null");
        Objects.requireNonNull(logRecordFormatter, "logRecordFormatter must not be null");
        String payload = shouldTransformLogsIntoTraceSpanEvents()
                ? toExportTracesRequestJson(logRecord)
                : logRecordFormatter.format(logRecord);
        return dispatchFormatted(payload);
    }

    /**
     * Dispatches one log record and fails on non-2xx responses.
     *
     * @param logRecord record to dispatch
     * @param logRecordFormatter formatter used to build payloads when needed
     * @throws IOException transport errors or non-success HTTP status
     */
    @Override
    public void dispatchLogRecord(final @Nonnull LogRecord logRecord,
                                  final @Nonnull LogRecordFormatter logRecordFormatter) throws IOException {
        DispatchResult result = dispatch(logRecord, logRecordFormatter);
        throwIfNonSuccess(result);
    }

    /**
     * Dispatches multiple records, preferring one batched OTLP request when possible.
     * <p>
     * Batch fast-path is used when:
     * <ul>
     *   <li>formatter is {@link ExportLogsServiceRequestLogRecordFormatter}</li>
     *   <li>endpoint mode is OTLP logs (not trace-transformation mode on {@code /v1/traces})</li>
     * </ul>
     * Otherwise records are dispatched sequentially with single-record semantics.
     *
     * @param logRecords records to dispatch
     * @param logRecordFormatter formatter used to build payloads
     * @throws IOException transport errors or non-success HTTP status
     */
    @Override
    public void dispatchLogRecords(final @Nonnull List<LogRecord> logRecords,
                                   final @Nonnull LogRecordFormatter logRecordFormatter) throws IOException {
        Objects.requireNonNull(logRecords, "logRecords must not be null");
        Objects.requireNonNull(logRecordFormatter, "logRecordFormatter must not be null");
        List<LogRecord> sanitized = sanitizeLogRecords(logRecords);
        if (sanitized.isEmpty()) {
            return;
        }
        if (sanitized.size() == 1) {
            dispatchLogRecord(sanitized.get(0), logRecordFormatter);
            return;
        }
        if (!shouldTransformLogsIntoTraceSpanEvents()
                && logRecordFormatter instanceof ExportLogsServiceRequestLogRecordFormatter) {
            String payload = ((ExportLogsServiceRequestLogRecordFormatter) logRecordFormatter).formatBatch(sanitized);
            DispatchResult result = dispatchFormatted(payload);
            throwIfNonSuccess(result);
            return;
        }
        for (LogRecord logRecord : sanitized) {
            dispatchLogRecord(logRecord, logRecordFormatter);
        }
    }

    /**
     * Dispatches a pre-formatted OTLP JSON payload.
     * <p>
     * This method assumes the payload already follows OTLP JSON shape, typically generated by
     * {@link ExportLogsServiceRequestLogRecordFormatter}.
     *
     * @param formattedExportLogsServiceRequestJson payload body
     * @return HTTP dispatch result
     * @throws IOException network/transport errors
     */
    public DispatchResult dispatchFormatted(final @Nonnull String formattedExportLogsServiceRequestJson) throws IOException {
        Objects.requireNonNull(formattedExportLogsServiceRequestJson, "formattedExportLogsServiceRequestJson must not be null");

        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        connection.setRequestProperty("Content-Type", APPLICATION_JSON);
        connection.setRequestProperty("Accept", APPLICATION_JSON);
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setRequestProperty("User-Agent", "common-utils-messagehandler/jaeger-logrecord-dispatcher");
        applyAuthentication(connection);
        applyAdditionalHeaders(connection);

        byte[] payloadBytes = formattedExportLogsServiceRequestJson.getBytes(StandardCharsets.UTF_8);
        OutputStream outputStream = null;
        InputStream inputStream = null;
        InputStream errorStream = null;
        try {
            outputStream = connection.getOutputStream();
            outputStream.write(payloadBytes);
            outputStream.flush();

            int statusCode = connection.getResponseCode();
            if (statusCode >= 200 && statusCode < 300) {
                inputStream = connection.getInputStream();
                return new DispatchResult(statusCode, readStream(inputStream));
            }
            errorStream = connection.getErrorStream();
            return new DispatchResult(statusCode, readStream(errorStream));
        } finally {
            closeQuietly(outputStream);
            closeQuietly(inputStream);
            closeQuietly(errorStream);
        }
    }

    /**
     * Dispatches a record and fails fast when the remote endpoint does not return a 2xx status code.
     *
     * @param logRecord record to send
     * @return successful dispatch result
     * @throws IOException network/transport errors or non-success HTTP status
     */
    public DispatchResult dispatchOrThrow(final @Nonnull LogRecord logRecord) throws IOException {
        DispatchResult result = dispatch(logRecord);
        throwIfNonSuccess(result);
        return result;
    }

    /**
     * Throws an {@link IOException} when the provided result is null or non-success.
     *
     * @param result dispatch result to validate
     * @throws IOException when result is null or status code is non-2xx
     */
    private void throwIfNonSuccess(final DispatchResult result) throws IOException {
        if (result == null) {
            throw new IOException("Dispatcher returned null dispatch result for endpoint " + endpoint);
        }
        if (!result.isSuccessful()) {
            throw new HttpDispatchStatusException(endpoint.toString(), result.getStatusCode(), result.getResponseBody());
        }
    }

    /**
     * Returns a copy of input records without null entries.
     *
     * @param logRecords input records
     * @return non-null records preserving input order
     */
    private static List<LogRecord> sanitizeLogRecords(final List<LogRecord> logRecords) {
        List<LogRecord> sanitized = new ArrayList<LogRecord>(logRecords.size());
        for (LogRecord logRecord : logRecords) {
            if (logRecord == null) {
                continue;
            }
            sanitized.add(logRecord);
        }
        return sanitized;
    }

    private boolean shouldTransformLogsIntoTraceSpanEvents() {
        String path = endpoint.getPath();
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

    private static String extractBodyAsString(final LogRecord logRecord) {
        if (logRecord.getBody() == null) {
            return "";
        }
        String value = logRecord.getBody().asString();
        return value == null ? "" : value;
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

    private static String toUnsignedNanosString(final Instant timestamp) {
        Instant safeTimestamp = timestamp == null ? Instant.now() : timestamp;
        BigInteger nanos = BigInteger.valueOf(safeTimestamp.getEpochSecond())
                .multiply(NANOS_PER_SECOND)
                .add(BigInteger.valueOf(safeTimestamp.getNano()));
        if (nanos.signum() < 0) {
            return "0";
        }
        return nanos.toString();
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

    private static String escapeJson(final String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        String hex = Integer.toHexString(c);
                        escaped.append("\\u");
                        for (int j = hex.length(); j < 4; j++) {
                            escaped.append('0');
                        }
                        escaped.append(hex);
                    } else {
                        escaped.append(c);
                    }
            }
        }
        return escaped.toString();
    }

    private void applyAuthentication(final HttpURLConnection connection) {
        if (hasText(bearerToken)) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
            return;
        }
        if (hasText(username) && password != null) {
            String raw = username + ":" + password;
            String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            connection.setRequestProperty("Authorization", "Basic " + encoded);
        }
    }

    private void applyAdditionalHeaders(final HttpURLConnection connection) {
        for (Map.Entry<String, String> entry : additionalHeaders.entrySet()) {
            if (!hasText(entry.getKey())) {
                continue;
            }
            if (entry.getValue() == null) {
                continue;
            }
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
    }

    private void validateAuthConfiguration() {
        if (hasText(username) && password == null) {
            throw new IllegalArgumentException("Basic auth password is required when username is set");
        }
        if (!hasText(username) && password != null) {
            throw new IllegalArgumentException("Basic auth username is required when password is set");
        }
    }

    private static URL parseEndpoint(final String endpointUrl) {
        String normalized = normalizeNullable(endpointUrl);
        if (normalized == null) {
            throw new IllegalArgumentException("endpointUrl must not be null or blank");
        }
        try {
            return new URL(normalized);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid endpointUrl: " + endpointUrl, ex);
        }
    }

    private static int requirePositive(final int value, final String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be > 0");
        }
        return value;
    }

    private static Map<String, String> toImmutableHeaders(final Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    private static String normalizeNullable(final String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean hasText(final String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String readStream(final InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(line);
        }
        return out.toString();
    }

    private static void closeQuietly(final InputStream inputStream) {
        if (inputStream == null) {
            return;
        }
        try {
            inputStream.close();
        } catch (IOException ignored) {
            // no-op
        }
    }

    private static void closeQuietly(final OutputStream outputStream) {
        if (outputStream == null) {
            return;
        }
        try {
            outputStream.close();
        } catch (IOException ignored) {
            // no-op
        }
    }

    /**
     * Immutable result of a single HTTP dispatch call.
     */
    public static final class DispatchResult {
        private final int statusCode;
        private final String responseBody;

        private DispatchResult(final int statusCode, final String responseBody) {
            this.statusCode = statusCode;
            this.responseBody = responseBody == null ? "" : responseBody;
        }

        /**
         * @return HTTP status code returned by the endpoint
         */
        public int getStatusCode() {
            return statusCode;
        }

        /**
         * @return response body (empty string when absent)
         */
        public String getResponseBody() {
            return responseBody;
        }

        /**
         * @return {@code true} when status code is in {@code [200..299]}
         */
        public boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
