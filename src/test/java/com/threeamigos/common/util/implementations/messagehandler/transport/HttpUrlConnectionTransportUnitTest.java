package com.threeamigos.common.util.implementations.messagehandler.transport;

import com.threeamigos.common.util.interfaces.messagehandler.transport.HttpTransportResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("HttpUrlConnectionTransport unit tests")
@Tag("unit")
@Tag("messageHandler")
class HttpUrlConnectionTransportUnitTest {

    private static final HttpUrlConnectionTransport TRANSPORT = new HttpUrlConnectionTransport();

    @Test
    @DisplayName("post should read inputStream and return body for 2xx response")
    void post_2xxResponse_readsInputStream() throws Exception {
        StubConnection connection = new StubConnection(200, "hello", null);
        URL url = urlFor(connection);

        HttpTransportResponse response = TRANSPORT.post(url, new byte[0], Collections.<String, String>emptyMap(), 5000, 5000);

        assertEquals(200, response.getStatusCode());
        assertEquals("hello", response.getBody());
    }

    @Test
    @DisplayName("post should read errorStream and return body for non-2xx response")
    void post_non2xxResponse_readsErrorStream() throws Exception {
        StubConnection connection = new StubConnection(500, null, "error");
        URL url = urlFor(connection);

        HttpTransportResponse response = TRANSPORT.post(url, new byte[0], Collections.<String, String>emptyMap(), 5000, 5000);

        assertEquals(500, response.getStatusCode());
        assertEquals("error", response.getBody());
    }

    @Test
    @DisplayName("post should return empty body when errorStream is null for non-2xx response")
    void post_nullErrorStream_returnsEmptyBody() throws Exception {
        StubConnection connection = new StubConnection(500, null, null);
        URL url = urlFor(connection);

        HttpTransportResponse response = TRANSPORT.post(url, new byte[0], Collections.<String, String>emptyMap(), 5000, 5000);

        assertEquals(500, response.getStatusCode());
        assertEquals("", response.getBody());
    }

    @Test
    @DisplayName("post should apply all provided headers and timeouts to the connection")
    void post_appliesHeadersAndTimeouts() throws Exception {
        StubConnection connection = new StubConnection(200, "", null);
        URL url = urlFor(connection);
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom", "value");

        TRANSPORT.post(url, new byte[0], headers, 3000, 7000);

        assertEquals("application/json", connection.requestProperties.get("Content-Type"));
        assertEquals("value", connection.requestProperties.get("X-Custom"));
        assertEquals(3000, connection.connectTimeout);
        assertEquals(7000, connection.readTimeout);
    }

    @Test
    @DisplayName("post should write provided body bytes to the connection output stream")
    void post_writesBodyBytesToOutputStream() throws Exception {
        StubConnection connection = new StubConnection(200, "", null);
        URL url = urlFor(connection);
        byte[] body = "test payload".getBytes(StandardCharsets.UTF_8);

        TRANSPORT.post(url, body, Collections.<String, String>emptyMap(), 1000, 1000);

        assertArrayEquals(body, connection.writtenBytes());
    }

    @Test
    @DisplayName("post should use POST request method")
    void post_setsRequestMethodToPost() throws Exception {
        StubConnection connection = new StubConnection(200, "", null);
        URL url = urlFor(connection);

        TRANSPORT.post(url, new byte[0], Collections.<String, String>emptyMap(), 1000, 1000);

        assertEquals("POST", connection.requestMethod);
    }

    @Test
    @DisplayName("readStream should return empty string for null stream")
    void readStream_nullStream_returnsEmpty() throws IOException {
        assertEquals("", HttpUrlConnectionTransport.readStream(null));
    }

    @Test
    @DisplayName("readStream should join multi-line content with newline characters")
    void readStream_multilineContent_joinsWithNewlines() throws IOException {
        InputStream stream = new ByteArrayInputStream("a\nb\nc".getBytes(StandardCharsets.UTF_8));
        assertEquals("a\nb\nc", HttpUrlConnectionTransport.readStream(stream));
    }

    @Test
    @DisplayName("closeQuietly should do nothing when InputStream is null")
    void closeQuietly_nullInputStream_noOp() {
        HttpUrlConnectionTransport.closeQuietly((InputStream) null);
    }

    @Test
    @DisplayName("closeQuietly should swallow IOException thrown by InputStream close")
    void closeQuietly_inputStreamCloseThrows_swallowsException() {
        InputStream throwing = new InputStream() {
            @Override
            public int read() {
                return -1;
            }

            @Override
            public void close() throws IOException {
                throw new IOException("forced");
            }
        };
        HttpUrlConnectionTransport.closeQuietly(throwing);
    }

    @Test
    @DisplayName("closeQuietly should do nothing when OutputStream is null")
    void closeQuietly_nullOutputStream_noOp() {
        HttpUrlConnectionTransport.closeQuietly((OutputStream) null);
    }

    @Test
    @DisplayName("closeQuietly should swallow IOException thrown by OutputStream close")
    void closeQuietly_outputStreamCloseThrows_swallowsException() {
        OutputStream throwing = new OutputStream() {
            @Override
            public void write(final int b) {
                // no-op
            }

            @Override
            public void close() throws IOException {
                throw new IOException("forced");
            }
        };
        HttpUrlConnectionTransport.closeQuietly(throwing);
    }

    @Test
    @DisplayName("HttpTransportResponse should normalize null body to empty string")
    void httpTransportResponse_nullBody_normalizesToEmpty() {
        HttpTransportResponse response = new HttpTransportResponse(200, null);
        assertEquals(200, response.getStatusCode());
        assertEquals("", response.getBody());
        assertNotNull(response.getBody());
    }

    private static URL urlFor(final StubConnection connection) throws Exception {
        return new URL(null, "http://unit.test/v1/logs", new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(final URL u) {
                return connection;
            }
        });
    }

    private static final class StubConnection extends HttpURLConnection {
        private final int statusCode;
        private final String inputBody;
        private final String errorBody;
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private final Map<String, String> requestProperties = new LinkedHashMap<String, String>();
        private String requestMethod;
        private int connectTimeout;
        private int readTimeout;

        StubConnection(final int statusCode, final String inputBody, final String errorBody) throws Exception {
            super(new URL("http://placeholder"));
            this.statusCode = statusCode;
            this.inputBody = inputBody;
            this.errorBody = errorBody;
        }

        @Override
        public void disconnect() {
            // no-op
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
        public void setConnectTimeout(final int timeout) {
            this.connectTimeout = timeout;
        }

        @Override
        public void setReadTimeout(final int timeout) {
            this.readTimeout = timeout;
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

        byte[] writtenBytes() {
            return outputStream.toByteArray();
        }
    }
}
