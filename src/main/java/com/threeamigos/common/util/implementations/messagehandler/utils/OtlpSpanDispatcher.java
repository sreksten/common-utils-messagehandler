package com.threeamigos.common.util.implementations.messagehandler.utils;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Event;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanData;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanDispatcher;
import com.threeamigos.common.util.interfaces.messagehandler.otel.StatusCode;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generic OTLP/HTTP span dispatcher that posts ExportTraceServiceRequest JSON.
 */
public class OtlpSpanDispatcher implements SpanDispatcher {

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

    public OtlpSpanDispatcher(final @Nonnull String endpointUrl) {
        this(endpointUrl, null, null, null, DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS, null);
    }

    public OtlpSpanDispatcher(final @Nonnull String endpointUrl,
                              final @Nullable String username,
                              final @Nullable String password) {
        this(endpointUrl, username, password, null, DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS, null);
    }

    public OtlpSpanDispatcher(final @Nonnull String endpointUrl,
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
        validateAuthConfiguration();
    }

    @Override
    public void dispatchSpan(final @Nonnull SpanData spanData) throws IOException {
        Objects.requireNonNull(spanData, "spanData must not be null");
        DispatchResult result = dispatchFormatted(toExportTracesPayload(spanData));
        if (!result.isSuccessful()) {
            throw new IOException("Dispatcher received non-success HTTP status "
                    + result.getStatusCode() + " from endpoint " + endpoint
                    + ", responseBody=" + result.getResponseBody());
        }
    }

    public DispatchResult dispatchFormatted(final @Nonnull String formattedExportTraceServiceRequestJson) throws IOException {
        Objects.requireNonNull(formattedExportTraceServiceRequestJson, "formattedExportTraceServiceRequestJson must not be null");

        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        connection.setRequestProperty("Content-Type", APPLICATION_JSON);
        connection.setRequestProperty("Accept", APPLICATION_JSON);
        connection.setRequestProperty("User-Agent", "common-utils-messagehandler/otlp-span-dispatcher");
        applyAuthentication(connection);
        applyAdditionalHeaders(connection);

        byte[] payloadBytes = formattedExportTraceServiceRequestJson.getBytes(StandardCharsets.UTF_8);
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

    protected String toExportTracesPayload(final SpanData spanData) {
        InstrumentationScope scope = spanData.getInstrumentationScope();
        String scopeName = scope == null ? null : normalizeNullable(scope.getName());
        String scopeVersion = scope == null ? null : normalizeNullable(scope.getVersion());
        String startNs = toUnsignedNanosString(spanData.getStartTimestamp());
        String endNs = toUnsignedNanosString(spanData.getEndTimestamp());

        StringBuilder sb = new StringBuilder(1024);
        sb.append("{\"resourceSpans\":[{\"scopeSpans\":[{\"scope\":{");
        boolean hasScopeField = false;
        if (scopeName != null) {
            sb.append("\"name\":\"").append(escapeJson(scopeName)).append('"');
            hasScopeField = true;
        }
        if (scopeVersion != null) {
            if (hasScopeField) {
                sb.append(',');
            }
            sb.append("\"version\":\"").append(escapeJson(scopeVersion)).append('"');
        }
        sb.append("},\"spans\":[{");
        sb.append("\"traceId\":\"").append(escapeJson(spanData.getSpanContext().getTraceId())).append("\",");
        sb.append("\"spanId\":\"").append(escapeJson(spanData.getSpanContext().getSpanId())).append("\",");
        String parentSpanId = normalizeNullable(spanData.getParentSpanId());
        if (parentSpanId != null) {
            sb.append("\"parentSpanId\":\"").append(escapeJson(parentSpanId)).append("\",");
        }
        sb.append("\"name\":\"").append(escapeJson(spanData.getName())).append("\",");
        sb.append("\"startTimeUnixNano\":\"").append(startNs).append("\",");
        sb.append("\"endTimeUnixNano\":\"").append(endNs).append("\"");

        List<KeyValue> attributes = spanData.getAttributes();
        if (attributes != null && !attributes.isEmpty()) {
            sb.append(",\"attributes\":[");
            appendAttributes(sb, attributes);
            sb.append(']');
        }

        List<Event> events = spanData.getEvents();
        if (events != null && !events.isEmpty()) {
            sb.append(",\"events\":[");
            boolean firstEvent = true;
            for (Event event : events) {
                if (event == null) {
                    continue;
                }
                if (!firstEvent) {
                    sb.append(',');
                }
                sb.append("{\"timeUnixNano\":\"")
                        .append(toUnsignedNanosString(event.getTimestamp()))
                        .append("\",\"name\":\"")
                        .append(escapeJson(event.getName()))
                        .append('"');
                List<KeyValue> eventAttributes = event.getAttributes();
                if (eventAttributes != null && !eventAttributes.isEmpty()) {
                    sb.append(",\"attributes\":[");
                    appendAttributes(sb, eventAttributes);
                    sb.append(']');
                }
                sb.append('}');
                firstEvent = false;
            }
            sb.append(']');
        }

        StatusCode statusCode = spanData.getStatusCode();
        if (statusCode != null && statusCode != StatusCode.UNSET) {
            sb.append(",\"status\":{\"code\":\"").append(statusCode == StatusCode.OK ? "STATUS_CODE_OK" : "STATUS_CODE_ERROR").append("\"");
            String statusDescription = normalizeNullable(spanData.getStatusDescription());
            if (statusDescription != null) {
                sb.append(",\"message\":\"").append(escapeJson(statusDescription)).append("\"");
            }
            sb.append('}');
        }

        sb.append("}]}]}]}");
        return sb.toString();
    }

    private static void appendAttributes(final StringBuilder sb, final List<KeyValue> attributes) {
        boolean first = true;
        for (KeyValue attribute : attributes) {
            if (attribute == null || attribute.getKey() == null || attribute.getValue() == null) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            sb.append("{\"key\":\"").append(escapeJson(attribute.getKey())).append("\",\"value\":");
            appendAnyValue(sb, attribute.getValue());
            sb.append('}');
            first = false;
        }
    }

    private static void appendAnyValue(final StringBuilder sb, final AnyValue anyValue) {
        if (anyValue == null || anyValue.getType() == null) {
            sb.append("{\"stringValue\":\"\"}");
            return;
        }
        switch (anyValue.getType()) {
            case BOOL:
                sb.append("{\"boolValue\":").append(anyValue.asBoolean()).append('}');
                return;
            case INT:
                sb.append("{\"intValue\":\"").append(anyValue.asLong()).append("\"}");
                return;
            case DOUBLE:
                sb.append("{\"doubleValue\":").append(anyValue.asDouble()).append('}');
                return;
            case STRING:
            default:
                sb.append("{\"stringValue\":\"").append(escapeJson(anyValue.asString())).append("\"}");
        }
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
            if (!hasText(entry.getKey()) || entry.getValue() == null) {
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
