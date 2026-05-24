package com.threeamigos.common.util.interfaces.messagehandler.transport;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 * Pluggable HTTP transport abstraction used by log-record dispatchers.
 * <p>
 * Implementations handle the mechanics of opening connections, writing the request
 * body, and reading the response. The built-in implementations are:
 * <ul>
 *   <li>{@code HttpUrlConnectionTransport} — uses {@link java.net.HttpURLConnection}
 *       (zero extra dependencies, default for all dispatchers).</li>
 *   <li>{@code ApacheHttpClientTransport} — uses Apache HttpClient 4.x with a
 *       {@code PoolingHttpClientConnectionManager}, available when
 *       {@code org.apache.httpcomponents:httpclient} is on the classpath.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <p>
 * Dispatchers use {@code HttpUrlConnectionTransport} by default. To enable connection
 * pooling, pass an {@code ApacheHttpClientTransport} to the dispatcher constructor:
 * <pre>{@code
 * GrafanaLogRecordDispatcher dispatcher = new GrafanaLogRecordDispatcher(
 *     "https://logs-prod.example.com/loki/api/v1/push",
 *     null, null, "bearer-token", 5_000, 10_000, null,
 *     new ApacheHttpClientTransport()   // connection pooling
 * );
 * }</pre>
 *
 * <h3>Thread safety</h3>
 * <p>
 * Implementations must be fully thread-safe. A single transport instance may be shared
 * across multiple dispatcher instances.
 *
 * @author Stefano Reksten
 */
public interface HttpTransport {

    /**
     * Sends an HTTP POST request to the given endpoint and returns the response.
     *
     * @param endpoint             target URL; never {@code null}
     * @param body                 raw request body bytes; never {@code null}
     * @param headers              request headers to include; may be empty but never {@code null}
     * @param connectTimeoutMillis TCP connect timeout in milliseconds; must be positive
     * @param readTimeoutMillis    socket read timeout in milliseconds; must be positive
     * @return the response status code and body
     * @throws IOException if a network or transport error occurs
     */
    HttpTransportResponse post(URL endpoint,
                               byte[] body,
                               Map<String, String> headers,
                               int connectTimeoutMillis,
                               int readTimeoutMillis) throws IOException;
}
