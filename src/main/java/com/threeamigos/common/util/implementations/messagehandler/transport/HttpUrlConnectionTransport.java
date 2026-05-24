package com.threeamigos.common.util.implementations.messagehandler.transport;

import com.threeamigos.common.util.interfaces.messagehandler.transport.HttpTransport;
import com.threeamigos.common.util.interfaces.messagehandler.transport.HttpTransportResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * {@link HttpTransport} implementation that uses {@link HttpURLConnection}.
 * <p>
 * This is the default transport for all HTTP dispatchers. It requires no additional
 * dependencies and relies on the JVM's built-in keep-alive socket cache for connection
 * reuse across sequential calls.
 * <p>
 * For sustained workloads that require explicit connection pooling, consider
 * {@link ApacheHttpClientTransport} instead.
 * <p>
 * Instances are stateless and fully thread-safe.
 *
 * @author Stefano Reksten
 */
public final class HttpUrlConnectionTransport implements HttpTransport {

    /**
     * Constructs a new {@code HttpUrlConnectionTransport}.
     */
    public HttpUrlConnectionTransport() {
        // no state
    }

    /**
     * {@inheritDoc}
     * <p>
     * Opens a new {@link HttpURLConnection} for each call, writes the payload to the
     * request body, and reads the response. The underlying TCP socket may be reused by
     * the JVM's keep-alive cache when the response streams are fully consumed.
     */
    @Override
    public HttpTransportResponse post(final URL endpoint,
                                      final byte[] body,
                                      final Map<String, String> headers,
                                      final int connectTimeoutMillis,
                                      final int readTimeoutMillis) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }

        OutputStream outputStream = null;
        InputStream inputStream = null;
        InputStream errorStream = null;
        try {
            outputStream = connection.getOutputStream();
            outputStream.write(body);
            outputStream.flush();

            int statusCode = connection.getResponseCode();
            if (statusCode >= 200 && statusCode < 300) {
                inputStream = connection.getInputStream();
                return new HttpTransportResponse(statusCode, readStream(inputStream));
            }
            errorStream = connection.getErrorStream();
            return new HttpTransportResponse(statusCode, readStream(errorStream));
        } finally {
            closeQuietly(outputStream);
            closeQuietly(inputStream);
            closeQuietly(errorStream);
        }
    }

    static String readStream(final InputStream stream) throws IOException {
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

    static void closeQuietly(final InputStream inputStream) {
        if (inputStream == null) {
            return;
        }
        try {
            inputStream.close();
        } catch (IOException ignored) {
            // best-effort
        }
    }

    static void closeQuietly(final OutputStream outputStream) {
        if (outputStream == null) {
            return;
        }
        try {
            outputStream.close();
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
