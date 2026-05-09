package com.threeamigos.common.util.implementations.messagehandler.utils;

import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ExportLogsServiceRequestLogRecordFormatter;
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

    @Override
    public void dispatchLogRecord(final @Nonnull LogRecord logRecord,
                                  final @Nonnull LogRecordFormatter logRecordFormatter) throws IOException {
        dispatch(logRecord, logRecordFormatter);
    }

    public DispatchResult dispatchFormatted(final @Nonnull String formattedExportLogsServiceRequestJson) throws IOException {
        Objects.requireNonNull(formattedExportLogsServiceRequestJson, "formattedExportLogsServiceRequestJson must not be null");

        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        connection.setRequestProperty("Content-Type", APPLICATION_JSON);
        connection.setRequestProperty("Accept", APPLICATION_JSON);
        connection.setRequestProperty("User-Agent", "common-utils-messagehandler/grafana-logrecord-dispatcher");
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
            if (statusCode >= 200 && statusCode < 400) {
                inputStream = connection.getInputStream();
                return new DispatchResult(statusCode, readStream(inputStream));
            }
            errorStream = connection.getErrorStream();
            return new DispatchResult(statusCode, readStream(errorStream));
        } finally {
            closeQuietly(outputStream);
            closeQuietly(inputStream);
            closeQuietly(errorStream);
            connection.disconnect();
        }
    }

    public DispatchResult dispatchOrThrow(final @Nonnull LogRecord logRecord) throws IOException {
        DispatchResult result = dispatch(logRecord);
        if (!result.isSuccessful()) {
            throw new IOException("Dispatcher received non-success HTTP status "
                    + result.getStatusCode() + " from endpoint " + endpoint
                    + ", responseBody=" + result.getResponseBody());
        }
        return result;
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
