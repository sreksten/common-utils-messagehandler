package com.threeamigos.common.util.implementations.messagehandler.utils;

import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ExportLogsServiceRequestLogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordDispatcher;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import com.threeamigos.common.util.implementations.messagehandler.transport.HttpUrlConnectionTransport;
import com.threeamigos.common.util.interfaces.messagehandler.transport.HttpTransport;
import com.threeamigos.common.util.interfaces.messagehandler.transport.HttpTransportResponse;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.IOException;
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
 * HTTP dispatcher for OTLP log payloads sent to Grafana-compatible OTLP logs endpoints.
 * <p>
 * This dispatcher always ships logs as OTLP logs payloads (ExportLogsServiceRequest JSON).
 * It does not transform logs into spans/events.
 */
public class GrafanaLogRecordDispatcher implements LogRecordDispatcher {

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
    private final HttpTransport httpTransport;

    public GrafanaLogRecordDispatcher(final @Nonnull String endpointUrl) {
        this(endpointUrl, null, null, null, DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS, null);
    }

    public GrafanaLogRecordDispatcher(final @Nonnull String endpointUrl,
                                      final @Nullable String username,
                                      final @Nullable String password) {
        this(endpointUrl, username, password, null, DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS, null);
    }

    public GrafanaLogRecordDispatcher(final @Nonnull String endpointUrl,
                                      final @Nullable String username,
                                      final @Nullable String password,
                                      final @Nullable String bearerToken,
                                      final int connectTimeoutMillis,
                                      final int readTimeoutMillis,
                                      final @Nullable Map<String, String> additionalHeaders) {
        this(endpointUrl, username, password, bearerToken, connectTimeoutMillis, readTimeoutMillis,
                additionalHeaders, new HttpUrlConnectionTransport());
    }

    /**
     * Creates a dispatcher with full configuration and a custom HTTP transport.
     * <p>
     * Use this constructor to provide an {@link HttpTransport} with connection-pooling
     * support (for example {@code new ApacheHttpClientTransport()}).
     *
     * @param endpointUrl          full HTTP endpoint
     * @param username             basic-auth username, nullable
     * @param password             basic-auth password, nullable
     * @param bearerToken          bearer token, nullable
     * @param connectTimeoutMillis connect timeout in milliseconds, must be positive
     * @param readTimeoutMillis    read timeout in milliseconds, must be positive
     * @param additionalHeaders    optional extra headers
     * @param httpTransport        transport to use for HTTP calls; defaults to
     *                             {@link HttpUrlConnectionTransport} when {@code null}
     */
    public GrafanaLogRecordDispatcher(final @Nonnull String endpointUrl,
                                      final @Nullable String username,
                                      final @Nullable String password,
                                      final @Nullable String bearerToken,
                                      final int connectTimeoutMillis,
                                      final int readTimeoutMillis,
                                      final @Nullable Map<String, String> additionalHeaders,
                                      final @Nullable HttpTransport httpTransport) {
        this.endpoint = parseEndpoint(endpointUrl);
        this.username = normalizeNullable(username);
        this.password = normalizeNullable(password);
        this.bearerToken = normalizeNullable(bearerToken);
        this.connectTimeoutMillis = requirePositive(connectTimeoutMillis, "connectTimeoutMillis");
        this.readTimeoutMillis = requirePositive(readTimeoutMillis, "readTimeoutMillis");
        this.additionalHeaders = toImmutableHeaders(additionalHeaders);
        this.formatter = new ExportLogsServiceRequestLogRecordFormatter();
        this.httpTransport = httpTransport != null ? httpTransport : new HttpUrlConnectionTransport();
        validateAuthConfiguration();
    }

    public DispatchResult dispatch(final @Nonnull LogRecord logRecord) throws IOException {
        return dispatch(logRecord, formatter);
    }

    public DispatchResult dispatch(final @Nonnull LogRecord logRecord,
                                   final @Nonnull LogRecordFormatter logRecordFormatter) throws IOException {
        Objects.requireNonNull(logRecord, "logRecord must not be null");
        Objects.requireNonNull(logRecordFormatter, "logRecordFormatter must not be null");
        String payload = shouldUseLokiPushFormat()
                ? toLokiPushPayload(logRecord)
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
     *   <li>endpoint mode is OTLP logs (not Loki push mode)</li>
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
        if (!shouldUseLokiPushFormat()
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
     * Dispatches a pre-formatted JSON payload to the configured endpoint.
     *
     * @param formattedExportLogsServiceRequestJson payload body to post
     * @return HTTP dispatch result
     * @throws IOException network/transport errors
     */
    public DispatchResult dispatchFormatted(final @Nonnull String formattedExportLogsServiceRequestJson) throws IOException {
        Objects.requireNonNull(formattedExportLogsServiceRequestJson, "formattedExportLogsServiceRequestJson must not be null");
        byte[] payloadBytes = formattedExportLogsServiceRequestJson.getBytes(StandardCharsets.UTF_8);
        HttpTransportResponse response = httpTransport.post(endpoint, payloadBytes, buildHeaders(),
                connectTimeoutMillis, readTimeoutMillis);
        return new DispatchResult(response.getStatusCode(), response.getBody());
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", APPLICATION_JSON);
        headers.put("Accept", APPLICATION_JSON);
        headers.put("Connection", "keep-alive");
        headers.put("User-Agent", "common-utils-messagehandler/grafana-logrecord-dispatcher");
        applyAuthentication(headers);
        applyAdditionalHeaders(headers);
        return headers;
    }

    /**
     * Dispatches one record and throws when the endpoint response is non-2xx.
     *
     * @param logRecord record to dispatch
     * @return successful dispatch result
     * @throws IOException transport errors or non-success HTTP status
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

    private boolean shouldUseLokiPushFormat() {
        String path = endpoint.getPath();
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

    private static String extractBodyAsString(final LogRecord logRecord) {
        if (logRecord.getBody() == null) {
            return "";
        }
        String value = logRecord.getBody().asString();
        return value == null ? "" : value;
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

    private void applyAuthentication(final Map<String, String> headers) {
        if (hasText(bearerToken)) {
            headers.put("Authorization", "Bearer " + bearerToken);
            return;
        }
        if (hasText(username) && password != null) {
            String raw = username + ":" + password;
            String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            headers.put("Authorization", "Basic " + encoded);
        }
    }

    private void applyAdditionalHeaders(final Map<String, String> headers) {
        for (Map.Entry<String, String> entry : additionalHeaders.entrySet()) {
            if (!hasText(entry.getKey())) {
                continue;
            }
            if (entry.getValue() == null) {
                continue;
            }
            headers.put(entry.getKey(), entry.getValue());
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
        return Collections.unmodifiableMap(new LinkedHashMap<String, String>(headers));
    }

    private static String normalizeNullable(final String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private static boolean hasText(final String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static final class DispatchResult {
        private final int statusCode;
        private final String responseBody;

        private DispatchResult(final int statusCode, final String responseBody) {
            this.statusCode = statusCode;
            this.responseBody = responseBody == null ? "" : responseBody;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getResponseBody() {
            return responseBody;
        }

        public boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
