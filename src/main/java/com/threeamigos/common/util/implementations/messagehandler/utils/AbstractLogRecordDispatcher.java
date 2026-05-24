package com.threeamigos.common.util.implementations.messagehandler.utils;

import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ExportLogsServiceRequestLogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordDispatcher;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.implementations.messagehandler.transport.HttpTransports;
import com.threeamigos.common.util.interfaces.messagehandler.transport.HttpTransport;
import com.threeamigos.common.util.interfaces.messagehandler.transport.HttpTransportResponse;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Abstract base class for HTTP-based OTLP log record dispatchers.
 * <p>
 * Provides common infrastructure: connection and auth configuration, HTTP transport lifecycle,
 * payload dispatch, error propagation, and utility helpers. Subclasses supply the
 * backend-specific payload format and endpoint-detection logic via three abstract methods:
 * <ul>
 *   <li>{@link #usesSpecialEndpointFormat()} — whether the endpoint requires per-record
 *       special formatting instead of the standard OTLP formatter output</li>
 *   <li>{@link #buildSpecialPayload(LogRecord)} — builds the backend-specific payload when
 *       {@link #usesSpecialEndpointFormat()} returns {@code true}</li>
 *   <li>{@link #getUserAgent()} — returns the {@code User-Agent} header value</li>
 * </ul>
 *
 * <h2>Transport lifecycle</h2>
 * <p>
 * When no explicit {@link HttpTransport} is provided, the dispatcher auto-detects the preferred
 * implementation at construction time via
 * {@link HttpTransports#createPreferred()}. The dispatcher owns the auto-created transport and
 * closes it in {@link #close()}. When a transport is supplied by the caller, the caller owns
 * its lifecycle.
 *
 * <h2>Thread safety</h2>
 * <p>
 * Instances are immutable after construction and safe for concurrent use.
 *
 * @author Stefano Reksten
 */
abstract class AbstractLogRecordDispatcher implements LogRecordDispatcher, Closeable {

    static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000;
    static final int DEFAULT_READ_TIMEOUT_MILLIS = 10_000;
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
    private final boolean ownsTransport;

    AbstractLogRecordDispatcher(final @Nonnull String endpointUrl,
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
        if (httpTransport == null) {
            this.httpTransport = HttpTransports.createPreferred();
            this.ownsTransport = true;
        } else {
            this.httpTransport = httpTransport;
            this.ownsTransport = false;
        }
        validateAuthConfiguration();
    }

    /**
     * Closes the underlying HTTP transport if this dispatcher created it automatically.
     * Has no effect when the transport was supplied externally.
     *
     * @throws IOException if closing the transport fails
     */
    @Override
    public void close() throws IOException {
        if (ownsTransport && httpTransport instanceof Closeable) {
            ((Closeable) httpTransport).close();
        }
    }

    /**
     * Formats and dispatches a {@link LogRecord} using the default formatter.
     *
     * @param logRecord record to send
     * @return HTTP dispatch result
     * @throws IOException network/transport errors
     */
    public DispatchResult dispatch(final @Nonnull LogRecord logRecord) throws IOException {
        return dispatch(logRecord, formatter);
    }

    /**
     * Formats and dispatches a {@link LogRecord} using the supplied formatter.
     * <p>
     * When {@link #usesSpecialEndpointFormat()} returns {@code true}, the payload is built via
     * {@link #buildSpecialPayload(LogRecord)} regardless of the formatter argument.
     *
     * @param logRecord          record to send
     * @param logRecordFormatter formatter used for standard OTLP endpoints
     * @return HTTP dispatch result
     * @throws IOException network/transport errors
     */
    public DispatchResult dispatch(final @Nonnull LogRecord logRecord,
                                   final @Nonnull LogRecordFormatter logRecordFormatter) throws IOException {
        Objects.requireNonNull(logRecord, "logRecord must not be null");
        Objects.requireNonNull(logRecordFormatter, "logRecordFormatter must not be null");
        String payload = usesSpecialEndpointFormat()
                ? buildSpecialPayload(logRecord)
                : logRecordFormatter.format(logRecord);
        return dispatchFormatted(payload);
    }

    /**
     * Dispatches one log record and fails on non-2xx responses.
     *
     * @param logRecord          record to dispatch
     * @param logRecordFormatter formatter used to build payloads
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
     * The batch fast-path is used when {@link #usesSpecialEndpointFormat()} returns
     * {@code false} and the formatter is {@link ExportLogsServiceRequestLogRecordFormatter}.
     * Otherwise records are dispatched sequentially.
     *
     * @param logRecords         records to dispatch
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
        if (!usesSpecialEndpointFormat()
                && logRecordFormatter instanceof ExportLogsServiceRequestLogRecordFormatter) {
            String payload = ((ExportLogsServiceRequestLogRecordFormatter) logRecordFormatter)
                    .formatBatch(sanitized);
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
    public DispatchResult dispatchFormatted(
            final @Nonnull String formattedExportLogsServiceRequestJson) throws IOException {
        Objects.requireNonNull(formattedExportLogsServiceRequestJson,
                "formattedExportLogsServiceRequestJson must not be null");
        byte[] payloadBytes = formattedExportLogsServiceRequestJson.getBytes(StandardCharsets.UTF_8);
        HttpTransportResponse response = httpTransport.post(endpoint, payloadBytes, buildHeaders(),
                connectTimeoutMillis, readTimeoutMillis);
        return new DispatchResult(response.getStatusCode(), response.getBody());
    }

    /**
     * Dispatches a record and throws on non-2xx responses.
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
     * Returns the configured endpoint URL.
     * Exposed for subclasses that need to inspect the path for endpoint-detection logic.
     *
     * @return endpoint URL
     */
    protected URL getEndpoint() {
        return endpoint;
    }

    // -------------------------------------------------------------------------
    // Abstract hook methods — implemented by each concrete backend dispatcher
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when the configured endpoint requires per-record special formatting
     * rather than the standard OTLP formatter output.
     * <p>
     * When {@code true}, {@link #buildSpecialPayload(LogRecord)} is called for each record
     * instead of the supplied {@link LogRecordFormatter}, and the batch fast-path is disabled.
     *
     * @return whether the endpoint uses backend-specific per-record formatting
     */
    protected abstract boolean usesSpecialEndpointFormat();

    /**
     * Builds the backend-specific payload for a single log record.
     * Called only when {@link #usesSpecialEndpointFormat()} returns {@code true}.
     *
     * @param logRecord the record to serialize
     * @return backend-specific JSON payload
     */
    protected abstract String buildSpecialPayload(LogRecord logRecord);

    /**
     * Returns the {@code User-Agent} header value for HTTP requests made by this dispatcher.
     *
     * @return User-Agent string
     */
    protected abstract String getUserAgent();

    // -------------------------------------------------------------------------
    // Private infrastructure helpers
    // -------------------------------------------------------------------------

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", APPLICATION_JSON);
        headers.put("Accept", APPLICATION_JSON);
        headers.put("Connection", "keep-alive");
        headers.put("User-Agent", getUserAgent());
        applyAuthentication(headers);
        applyAdditionalHeaders(headers);
        return headers;
    }

    private void throwIfNonSuccess(final DispatchResult result) throws IOException {
        if (result == null) {
            throw new IOException("Dispatcher returned null dispatch result for endpoint " + endpoint);
        }
        if (!result.isSuccessful()) {
            throw new HttpDispatchStatusException(endpoint.toString(), result.getStatusCode(),
                    result.getResponseBody());
        }
    }

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

    private static boolean hasText(final String value) {
        return value != null && !value.trim().isEmpty();
    }

    // -------------------------------------------------------------------------
    // Package-private static utilities — shared with concrete subclasses
    // -------------------------------------------------------------------------

    static Map<String, String> toImmutableHeaders(final Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<String, String>(headers));
    }

    static String normalizeNullable(final String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String escapeJson(final String value) {
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

    static String extractBodyAsString(final LogRecord logRecord) {
        if (logRecord.getBody() == null) {
            return "";
        }
        String value = logRecord.getBody().asString();
        return value == null ? "" : value;
    }

    static String toUnsignedNanosString(final Instant timestamp) {
        Instant safeTimestamp = timestamp == null ? Instant.now() : timestamp;
        BigInteger nanos = BigInteger.valueOf(safeTimestamp.getEpochSecond())
                .multiply(NANOS_PER_SECOND)
                .add(BigInteger.valueOf(safeTimestamp.getNano()));
        if (nanos.signum() < 0) {
            return "0";
        }
        return nanos.toString();
    }

    // -------------------------------------------------------------------------
    // Nested result type
    // -------------------------------------------------------------------------

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
