package com.threeamigos.common.util.implementations.messagehandler.utils;

import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordFactoryImpl;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("JaegerLogRecordDispatcher unit tests")
@Tag("unit")
@Tag("messageHandler")
class JaegerLogRecordDispatcherUnitTest {

    @Test
    @DisplayName("dispatch(LogRecord) should post formatted OTLP JSON and return response")
    void dispatchShouldPostFormattedLogRecord() throws Exception {
        StubConnection connection = new StubConnection(200, "ok", null);
        JaegerLogRecordDispatcher dispatcher = dispatcherWithConnection(connection);
        LogRecord record = new LogRecordFactoryImpl().create(SeverityNumber.INFO, "hello jaeger");

        JaegerLogRecordDispatcher.DispatchResult result = dispatcher.dispatch(record);

        assertEquals(200, result.getStatusCode());
        assertTrue(result.isSuccessful());
        assertEquals("ok", result.getResponseBody());
        assertEquals("POST", connection.requestMethod);
        assertEquals("application/json", connection.requestProperties.get("Content-Type"));
        assertEquals("application/json", connection.requestProperties.get("Accept"));
        assertNotNull(connection.requestProperties.get("User-Agent"));
        assertTrue(connection.writtenBody().contains("\"body\":{\"stringValue\":\"hello jaeger\"}"));
        assertTrue(connection.disconnected);
    }

    @Test
    @DisplayName("dispatcher should use Basic authentication when username/password are configured")
    void dispatchShouldUseBasicAuthentication() throws Exception {
        StubConnection connection = new StubConnection(200, "ok", null);
        JaegerLogRecordDispatcher dispatcher = new JaegerLogRecordDispatcher(
                "http://localhost:4318/v1/logs", "alice", "secret");
        replaceEndpoint(dispatcher, urlFor(connection));

        dispatcher.dispatchFormatted("{\"resourceLogs\":[]}");

        String expected = "Basic " + Base64.getEncoder()
                .encodeToString("alice:secret".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, connection.requestProperties.get("Authorization"));
    }

    @Test
    @DisplayName("dispatcher should prefer Bearer token over Basic and apply custom headers")
    void dispatchShouldPreferBearerAndApplyHeaders() throws Exception {
        StubConnection connection = new StubConnection(200, "ok", null);
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("X-Tenant", "payments");
        headers.put(" ", "ignored");
        headers.put("X-Null", null);

        JaegerLogRecordDispatcher dispatcher = new JaegerLogRecordDispatcher(
                "http://localhost:4318/v1/logs", "alice", "secret", "token-123", 2_000, 2_000, headers);
        replaceEndpoint(dispatcher, urlFor(connection));

        dispatcher.dispatchFormatted("{\"resourceLogs\":[]}");

        assertEquals("Bearer token-123", connection.requestProperties.get("Authorization"));
        assertEquals("payments", connection.requestProperties.get("X-Tenant"));
        assertFalse(connection.requestProperties.containsKey("X-Null"));
    }

    @Test
    @DisplayName("dispatchOrThrow should fail for non-2xx status")
    void dispatchOrThrowShouldFailOnNon2xx() throws Exception {
        StubConnection connection = new StubConnection(500, null, "boom");
        JaegerLogRecordDispatcher dispatcher = dispatcherWithConnection(connection);
        LogRecord record = new LogRecordFactoryImpl().create(SeverityNumber.ERROR, "fail");

        IOException thrown = assertThrows(IOException.class, () -> dispatcher.dispatchOrThrow(record));
        assertTrue(thrown.getMessage().contains("500"));
        assertTrue(thrown.getMessage().contains("boom"));
    }

    @Test
    @DisplayName("dispatch methods should reject null arguments")
    void dispatchMethodsShouldRejectNulls() {
        JaegerLogRecordDispatcher dispatcher = new JaegerLogRecordDispatcher("http://localhost:4318/v1/logs");
        assertThrows(NullPointerException.class, () -> dispatcher.dispatch(null));
        assertThrows(NullPointerException.class, () -> dispatcher.dispatchFormatted(null));
    }

    @Test
    @DisplayName("constructor should validate endpoint, auth config and timeout values")
    void constructorShouldValidateInputs() {
        assertThrows(IllegalArgumentException.class, () -> new JaegerLogRecordDispatcher(" "));
        assertThrows(IllegalArgumentException.class, () -> new JaegerLogRecordDispatcher("not-a-url"));
        assertThrows(IllegalArgumentException.class, () -> new JaegerLogRecordDispatcher(
                "http://localhost:4318/v1/logs", "user", null));
        assertThrows(IllegalArgumentException.class, () -> new JaegerLogRecordDispatcher(
                "http://localhost:4318/v1/logs", null, "pass"));
        assertThrows(IllegalArgumentException.class, () -> new JaegerLogRecordDispatcher(
                "http://localhost:4318/v1/logs", null, null, null, 0, 1000, null));
        assertThrows(IllegalArgumentException.class, () -> new JaegerLogRecordDispatcher(
                "http://localhost:4318/v1/logs", null, null, null, 1000, 0, null));
    }

    @Test
    @DisplayName("private helpers should cover null branches")
    void privateHelpersShouldCoverNullBranches() throws Exception {
        Method readStream = JaegerLogRecordDispatcher.class.getDeclaredMethod("readStream", InputStream.class);
        readStream.setAccessible(true);
        assertEquals("", readStream.invoke(null, new Object[]{null}));

        Method closeInput = JaegerLogRecordDispatcher.class.getDeclaredMethod("closeQuietly", InputStream.class);
        closeInput.setAccessible(true);
        closeInput.invoke(null, new Object[]{null});

        Method closeOutput = JaegerLogRecordDispatcher.class.getDeclaredMethod("closeQuietly", java.io.OutputStream.class);
        closeOutput.setAccessible(true);
        closeOutput.invoke(null, new Object[]{null});
    }

    @Test
    @DisplayName("DispatchResult should report success only for 2xx statuses")
    void dispatchResultShouldReportSuccessRange() throws Exception {
        Constructor<?> constructor = JaegerLogRecordDispatcher.DispatchResult.class
                .getDeclaredConstructor(int.class, String.class);
        constructor.setAccessible(true);
        JaegerLogRecordDispatcher.DispatchResult ok =
                (JaegerLogRecordDispatcher.DispatchResult) constructor.newInstance(299, null);
        JaegerLogRecordDispatcher.DispatchResult redirect =
                (JaegerLogRecordDispatcher.DispatchResult) constructor.newInstance(302, "r");

        assertTrue(ok.isSuccessful());
        assertEquals("", ok.getResponseBody());
        assertFalse(redirect.isSuccessful());
    }

    private static JaegerLogRecordDispatcher dispatcherWithConnection(final StubConnection connection) throws Exception {
        JaegerLogRecordDispatcher dispatcher = new JaegerLogRecordDispatcher("http://localhost:4318/v1/logs");
        replaceEndpoint(dispatcher, urlFor(connection));
        return dispatcher;
    }

    private static URL urlFor(final StubConnection connection) throws Exception {
        return new URL(null, "http://unit.test/v1/logs", new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(final URL u) {
                return connection;
            }
        });
    }

    private static void replaceEndpoint(final JaegerLogRecordDispatcher dispatcher, final URL url) throws Exception {
        Field endpointField = JaegerLogRecordDispatcher.class.getDeclaredField("endpoint");
        endpointField.setAccessible(true);
        endpointField.set(dispatcher, url);
    }

    private static final class StubConnection extends HttpURLConnection {
        private final int statusCode;
        private final String inputBody;
        private final String errorBody;
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private final Map<String, String> requestProperties = new LinkedHashMap<String, String>();
        private String requestMethod;
        private boolean disconnected;

        StubConnection(final int statusCode, final String inputBody, final String errorBody) throws Exception {
            super(new URL("http://placeholder"));
            this.statusCode = statusCode;
            this.inputBody = inputBody;
            this.errorBody = errorBody;
        }

        @Override
        public void disconnect() {
            disconnected = true;
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
            // no-op
        }

        @Override
        public void setRequestMethod(final String method) {
            this.requestMethod = method;
        }

        @Override
        public void setRequestProperty(final String key, final String value) {
            requestProperties.put(key, value);
        }

        @Override
        public ByteArrayOutputStream getOutputStream() {
            return outputStream;
        }

        @Override
        public int getResponseCode() {
            return statusCode;
        }

        @Override
        public InputStream getInputStream() {
            if (inputBody == null) {
                return null;
            }
            return new ByteArrayInputStream(inputBody.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getErrorStream() {
            if (errorBody == null) {
                return null;
            }
            return new ByteArrayInputStream(errorBody.getBytes(StandardCharsets.UTF_8));
        }

        String writtenBody() {
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
