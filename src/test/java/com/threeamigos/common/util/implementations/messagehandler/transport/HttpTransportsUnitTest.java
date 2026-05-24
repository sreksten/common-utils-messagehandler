package com.threeamigos.common.util.implementations.messagehandler.transport;

import com.threeamigos.common.util.interfaces.messagehandler.transport.HttpTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("HttpTransports unit tests")
@Tag("unit")
@Tag("messageHandler")
class HttpTransportsUnitTest {

    private static final String REAL_CLASS = "org.apache.http.impl.client.CloseableHttpClient";
    private static final String MISSING_CLASS = "com.nonexistent.Missing";

    @AfterEach
    void restoreDetectionClass() {
        HttpTransports.detectionClass = REAL_CLASS;
    }

    @Test
    @DisplayName("createPreferred returns ApacheHttpClientTransport when Apache HttpClient is available")
    void createPreferred_returnsApache_whenAvailable() {
        HttpTransports.detectionClass = REAL_CLASS;
        HttpTransport transport = HttpTransports.createPreferred();
        assertInstanceOf(ApacheHttpClientTransport.class, transport);
    }

    @Test
    @DisplayName("createPreferred returns HttpUrlConnectionTransport when Apache HttpClient is absent")
    void createPreferred_returnsFallback_whenAbsent() {
        HttpTransports.detectionClass = MISSING_CLASS;
        HttpTransport transport = HttpTransports.createPreferred();
        assertInstanceOf(HttpUrlConnectionTransport.class, transport);
    }

    @Test
    @DisplayName("isApacheAvailable returns true when detection class is on classpath")
    void isApacheAvailable_returnsTrue_whenPresent() {
        HttpTransports.detectionClass = REAL_CLASS;
        assertTrue(HttpTransports.isApacheAvailable());
    }

    @Test
    @DisplayName("isApacheAvailable returns false when detection class is absent")
    void isApacheAvailable_returnsFalse_whenAbsent() {
        HttpTransports.detectionClass = MISSING_CLASS;
        assertFalse(HttpTransports.isApacheAvailable());
    }

    @Test
    @DisplayName("constructor is not instantiable")
    void constructor_throwsUnsupportedOperationException() throws Exception {
        Constructor<HttpTransports> ctor = HttpTransports.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class, ctor::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
    }
}
