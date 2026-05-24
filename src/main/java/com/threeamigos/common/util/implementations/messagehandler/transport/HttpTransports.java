package com.threeamigos.common.util.implementations.messagehandler.transport;

import com.threeamigos.common.util.interfaces.messagehandler.transport.HttpTransport;

/**
 * Factory for the preferred {@link HttpTransport} implementation.
 * <p>
 * At construction time the factory checks whether Apache HttpClient 4.x is on the classpath.
 * If it is, {@link ApacheHttpClientTransport} is returned (connection pooling, persistent sockets).
 * Otherwise {@link HttpUrlConnectionTransport} is returned (zero extra dependencies).
 *
 * <h3>Opt-in to connection pooling</h3>
 * <p>
 * Simply add the optional dependency to your project and the preferred transport will be selected
 * automatically — no code change needed:
 * <pre>{@code
 * <dependency>
 *     <groupId>org.apache.httpcomponents</groupId>
 *     <artifactId>httpclient</artifactId>
 *     <version>4.5.14</version>
 * </dependency>
 * }</pre>
 *
 * <h3>Opt-out</h3>
 * <p>
 * To force {@link HttpUrlConnectionTransport} regardless of what is on the classpath, pass an
 * explicit {@code new HttpUrlConnectionTransport()} to the dispatcher constructor instead of
 * relying on the default.
 *
 * @author Stefano Reksten
 */
public final class HttpTransports {

    /**
     * Fully-qualified class name used to detect Apache HttpClient on the classpath.
     * Package-private and volatile to allow test code to force the fallback branch.
     */
    static volatile String detectionClass = "org.apache.http.impl.client.CloseableHttpClient";

    private HttpTransports() {
        throw new UnsupportedOperationException("utility class");
    }

    /**
     * Returns the preferred {@link HttpTransport} for the current classpath.
     * <p>
     * Returns {@link ApacheHttpClientTransport} when Apache HttpClient is available,
     * or {@link HttpUrlConnectionTransport} otherwise.
     * <p>
     * Each call returns a <em>new</em> instance. Callers are responsible for closing
     * {@link ApacheHttpClientTransport} instances (they implement {@link java.io.Closeable}).
     *
     * @return a new transport instance; never {@code null}
     */
    public static HttpTransport createPreferred() {
        return isApacheAvailable() ? new ApacheHttpClientTransport()
                                   : new HttpUrlConnectionTransport();
    }

    /**
     * Returns {@code true} if Apache HttpClient is available on the classpath.
     *
     * @return {@code true} when Apache HttpClient can be loaded; {@code false} otherwise
     */
    static boolean isApacheAvailable() {
        try {
            Class.forName(detectionClass);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
