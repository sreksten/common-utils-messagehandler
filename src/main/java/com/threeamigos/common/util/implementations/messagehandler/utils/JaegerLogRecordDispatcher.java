package com.threeamigos.common.util.implementations.messagehandler.utils;

import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ExportLogsServiceRequestLogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;

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
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
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
 * <h2>Authentication</h2>
 * <p>
 * Jaeger itself is often deployed without built-in collector auth and protected via reverse proxy/gateway.
 * For this reason, the dispatcher supports:
 * <ul>
 *   <li>HTTP Basic authentication (username + password)</li>
 *   <li>Bearer token authentication</li>
 *   <li>Additional custom headers</li>
 * </ul>
 * If both Basic and Bearer are configured, Bearer is used.
 *
 * <h2>Thread Safety</h2>
 * <p>
 * Instances are immutable after construction and safe for concurrent use.
 * Each dispatch call opens a dedicated {@link HttpURLConnection} and closes it immediately after use.
 *
 * <h2>References</h2>
 * <ul>
 *   <li>OpenTelemetry OTLP/HTTP specification: default logs path {@code /v1/logs} and JSON encoding.</li>
 *   <li>Jaeger documentation (2.x): collector OTLP endpoints focused on traces, typically {@code /v1/traces}.</li>
 * </ul>
 *
 * @author Stefano Reksten
 */
public class JaegerLogRecordDispatcher {

    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 10_000;
    private static final String APPLICATION_JSON = "application/json";

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
        Objects.requireNonNull(logRecord, "logRecord must not be null");
        return dispatchFormatted(formatter.format(logRecord));
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

    /**
     * Dispatches a record and fails fast when the remote endpoint does not return a 2xx status code.
     *
     * @param logRecord record to send
     * @return successful dispatch result
     * @throws IOException network/transport errors or non-success HTTP status
     */
    public DispatchResult dispatchOrThrow(final @Nonnull LogRecord logRecord) throws IOException {
        DispatchResult result = dispatch(logRecord);
        if (!result.isSuccessful()) {
            throw new IOException("Dispatcher received non-success HTTP status "
                    + result.getStatusCode() + " from endpoint " + endpoint
                    + ", responseBody=" + result.getResponseBody());
        }
        return result;
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
