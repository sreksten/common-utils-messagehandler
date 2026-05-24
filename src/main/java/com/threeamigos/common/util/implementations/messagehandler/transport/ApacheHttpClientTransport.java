package com.threeamigos.common.util.implementations.messagehandler.transport;

import com.threeamigos.common.util.interfaces.messagehandler.transport.HttpTransport;
import com.threeamigos.common.util.interfaces.messagehandler.transport.HttpTransportResponse;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * {@link HttpTransport} implementation backed by Apache HttpClient 4.x.
 * <p>
 * A single {@link PoolingHttpClientConnectionManager} is shared across all calls, keeping
 * persistent connections open and reducing TCP handshake overhead for sustained workloads.
 * Default pool settings: 50 total connections, 10 connections per route. These can be
 * adjusted by constructing the transport with a pre-configured {@link CloseableHttpClient}.
 *
 * <h3>Lifecycle</h3>
 * <p>
 * Call {@link #close()} when the transport is no longer needed to release pooled sockets.
 * This is typically done at application shutdown:
 * <pre>{@code
 * ApacheHttpClientTransport transport = new ApacheHttpClientTransport();
 * // ... use with dispatcher ...
 * transport.close();
 * }</pre>
 *
 * <h3>Dependency</h3>
 * <p>
 * Requires {@code org.apache.httpcomponents:httpclient} on the runtime classpath.
 * Declare it as a non-optional dependency in your project:
 * <pre>{@code
 * <dependency>
 *     <groupId>org.apache.httpcomponents</groupId>
 *     <artifactId>httpclient</artifactId>
 *     <version>4.5.14</version>
 * </dependency>
 * }</pre>
 *
 * <h3>Thread safety</h3>
 * <p>
 * Instances are fully thread-safe. A single instance can be shared across multiple
 * dispatcher instances.
 *
 * @author Stefano Reksten
 */
public final class ApacheHttpClientTransport implements HttpTransport {

    private static final int DEFAULT_MAX_TOTAL = 50;
    private static final int DEFAULT_MAX_PER_ROUTE = 10;

    private final CloseableHttpClient httpClient;

    /**
     * Creates a transport with a default pooling configuration:
     * {@value #DEFAULT_MAX_TOTAL} total connections and
     * {@value #DEFAULT_MAX_PER_ROUTE} connections per route.
     */
    public ApacheHttpClientTransport() {
        this(createDefaultClient());
    }

    /**
     * Creates a transport using the supplied {@link CloseableHttpClient}.
     * <p>
     * Use this constructor when you need full control over connection pool settings,
     * TLS configuration, proxy support, or cookie management.
     * The caller is responsible for closing the provided client when it is no longer needed.
     *
     * @param httpClient pre-configured Apache HttpClient; must not be {@code null}
     */
    public ApacheHttpClientTransport(final CloseableHttpClient httpClient) {
        if (httpClient == null) {
            throw new IllegalArgumentException("httpClient must not be null");
        }
        this.httpClient = httpClient;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Uses the shared connection pool. Per-request connect and socket timeouts are applied
     * via {@link RequestConfig}.
     */
    @Override
    public HttpTransportResponse post(final URL endpoint,
                                      final byte[] body,
                                      final Map<String, String> headers,
                                      final int connectTimeoutMillis,
                                      final int readTimeoutMillis) throws IOException {
        HttpPost request = new HttpPost(endpoint.toString());
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(connectTimeoutMillis)
                .setSocketTimeout(readTimeoutMillis)
                .build();
        request.setConfig(config);

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            request.setHeader(entry.getKey(), entry.getValue());
        }
        request.setEntity(new ByteArrayEntity(body));

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            String responseBody = "";
            if (entity != null) {
                responseBody = HttpUrlConnectionTransport.readStream(entity.getContent());
            }
            return new HttpTransportResponse(statusCode, responseBody);
        }
    }

    /**
     * Closes the underlying {@link CloseableHttpClient}, releasing all pooled connections.
     *
     * @throws IOException if closing the client fails
     */
    public void close() throws IOException {
        httpClient.close();
    }

    private static CloseableHttpClient createDefaultClient() {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(DEFAULT_MAX_TOTAL);
        cm.setDefaultMaxPerRoute(DEFAULT_MAX_PER_ROUTE);
        return HttpClients.custom()
                .setConnectionManager(cm)
                .build();
    }
}
