package com.threeamigos.common.util.implementations.messagehandler.utils;

import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordFactoryImpl;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ExportLogsServiceRequestLogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
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
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GrafanaLogRecordDispatcher unit tests")
@Tag("unit")
@Tag("messageHandler")
class GrafanaLogRecordDispatcherUnitTest {

    @Test
    @DisplayName("dispatch(LogRecord) should post formatted OTLP JSON and return response")
    void dispatchShouldPostFormattedLogRecord() throws Exception {
        StubConnection connection = new StubConnection(200, "ok", null);
        GrafanaLogRecordDispatcher dispatcher = dispatcherWithConnection(connection);
        LogRecord record = new LogRecordFactoryImpl().create(SeverityNumber.INFO, "hello grafana");

        GrafanaLogRecordDispatcher.DispatchResult result = dispatcher.dispatch(record);

        assertEquals(200, result.getStatusCode());
        assertTrue(result.isSuccessful());
        assertEquals("ok", result.getResponseBody());
        assertEquals("POST", connection.requestMethod);
        assertEquals("application/json", connection.requestProperties.get("Content-Type"));
        assertEquals("application/json", connection.requestProperties.get("Accept"));
        assertNotNull(connection.requestProperties.get("User-Agent"));
        assertTrue(connection.writtenBody().contains("\"resourceLogs\""));
        assertTrue(connection.writtenBody().contains("\"body\":{\"stringValue\":\"hello grafana\"}"));
        assertFalse(connection.disconnected);
    }

    @Test
    @DisplayName("dispatch(LogRecord) should use Loki push payload for /loki/api/v1/push endpoint")
    void dispatchShouldUseLokiPushPayloadForLokiEndpoint() throws Exception {
        StubConnection connection = new StubConnection(204, "", null);
        GrafanaLogRecordDispatcher dispatcher = new GrafanaLogRecordDispatcher("http://localhost:3100/loki/api/v1/push");
        replaceEndpoint(dispatcher, urlFor(connection, "/loki/api/v1/push"));
        LogRecord record = new LogRecordFactoryImpl().create(SeverityNumber.INFO, "hello loki");

        GrafanaLogRecordDispatcher.DispatchResult result = dispatcher.dispatch(record);

        assertEquals(204, result.getStatusCode());
        assertTrue(result.isSuccessful());
        String payload = connection.writtenBody();
        assertTrue(payload.contains("\"streams\""));
        assertTrue(payload.contains("\"values\""));
        assertTrue(payload.contains("\"service_name\""));
        assertTrue(payload.contains("hello loki"));
        assertFalse(payload.contains("\"resourceLogs\""));
    }

    @Test
    @DisplayName("dispatcher should use Basic authentication when username/password are configured")
    void dispatchShouldUseBasicAuthentication() throws Exception {
        StubConnection connection = new StubConnection(200, "ok", null);
        GrafanaLogRecordDispatcher dispatcher = new GrafanaLogRecordDispatcher(
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
        headers.put("X-Scope-OrgID", "tenant-1");
        headers.put(" ", "ignored");
        headers.put("X-Null", null);

        GrafanaLogRecordDispatcher dispatcher = new GrafanaLogRecordDispatcher(
                "http://localhost:4318/v1/logs", "alice", "secret", "token-123", 2_000, 2_000, headers);
        replaceEndpoint(dispatcher, urlFor(connection));

        dispatcher.dispatchFormatted("{\"resourceLogs\":[]}");

        assertEquals("Bearer token-123", connection.requestProperties.get("Authorization"));
        assertEquals("tenant-1", connection.requestProperties.get("X-Scope-OrgID"));
        assertFalse(connection.requestProperties.containsKey("X-Null"));
    }

    @Test
    @DisplayName("dispatchOrThrow should fail for non-2xx status")
    void dispatchOrThrowShouldFailOnNon2xx() throws Exception {
        StubConnection connection = new StubConnection(500, null, "boom");
        GrafanaLogRecordDispatcher dispatcher = dispatcherWithConnection(connection);
        LogRecord record = new LogRecordFactoryImpl().create(SeverityNumber.ERROR, "fail");

        IOException thrown = assertThrows(IOException.class, () -> dispatcher.dispatchOrThrow(record));
        assertTrue(thrown.getMessage().contains("500"));
        assertTrue(thrown.getMessage().contains("boom"));
    }

    @Test
    @DisplayName("dispatchOrThrow should return result for successful status")
    void dispatchOrThrowShouldReturnResultForSuccessfulStatus() throws Exception {
        StubConnection connection = new StubConnection(204, "ok", null);
        GrafanaLogRecordDispatcher dispatcher = dispatcherWithConnection(connection);
        LogRecord record = new LogRecordFactoryImpl().create(SeverityNumber.INFO, "ok");

        GrafanaLogRecordDispatcher.DispatchResult result = dispatcher.dispatchOrThrow(record);

        assertEquals(204, result.getStatusCode());
        assertTrue(result.isSuccessful());
    }

    @Test
    @DisplayName("dispatchFormatted should treat status below 200 as failure")
    void dispatchFormattedShouldTreatStatusBelow200AsFailure() throws Exception {
        StubConnection connection = new StubConnection(199, null, "too-early");
        GrafanaLogRecordDispatcher dispatcher = dispatcherWithConnection(connection);
        LogRecord record = new LogRecordFactoryImpl().create(SeverityNumber.INFO, "retry");

        GrafanaLogRecordDispatcher.DispatchResult result = dispatcher.dispatchFormatted("{\"x\":1}");
        HttpDispatchStatusException thrown = assertThrows(
                HttpDispatchStatusException.class,
                () -> dispatcher.dispatchLogRecord(record, lr -> "{\"x\":1}")
        );

        assertEquals(199, result.getStatusCode());
        assertFalse(result.isSuccessful());
        assertEquals("too-early", result.getResponseBody());
        assertEquals(199, thrown.getStatusCode());
        assertEquals("too-early", thrown.getResponseBody());
    }

    @Test
    @DisplayName("dispatchLogRecords should send multiple records in one OTLP envelope when supported")
    void dispatchLogRecordsShouldSendMultipleRecordsInOneEnvelopeWhenSupported() throws Exception {
        StubConnection connection = new StubConnection(200, "ok", null);
        GrafanaLogRecordDispatcher dispatcher = dispatcherWithConnection(connection);
        LogRecordFactoryImpl factory = new LogRecordFactoryImpl();
        List<LogRecord> records = Arrays.asList(
                factory.create(SeverityNumber.INFO, "hello-1"),
                factory.create(SeverityNumber.WARN, "hello-2")
        );

        dispatcher.dispatchLogRecords(records, new ExportLogsServiceRequestLogRecordFormatter());

        String payload = connection.writtenBody();
        assertTrue(payload.contains("hello-1"));
        assertTrue(payload.contains("hello-2"));
        assertEquals(1, connection.outputStreamCalls);
    }

    @Test
    @DisplayName("dispatch methods should reject null arguments")
    void dispatchMethodsShouldRejectNulls() {
        GrafanaLogRecordDispatcher dispatcher = new GrafanaLogRecordDispatcher("http://localhost:4318/v1/logs");
        assertThrows(NullPointerException.class, () -> dispatcher.dispatch(null));
        assertThrows(NullPointerException.class, () -> dispatcher.dispatchFormatted(null));
    }

    @Test
    @DisplayName("constructor should validate endpoint, auth config and timeout values")
    void constructorShouldValidateInputs() {
        assertThrows(IllegalArgumentException.class, () -> new GrafanaLogRecordDispatcher(" "));
        assertThrows(IllegalArgumentException.class, () -> new GrafanaLogRecordDispatcher("not-a-url"));
        assertThrows(IllegalArgumentException.class, () -> new GrafanaLogRecordDispatcher(
                "http://localhost:4318/v1/logs", "user", null));
        assertThrows(IllegalArgumentException.class, () -> new GrafanaLogRecordDispatcher(
                "http://localhost:4318/v1/logs", null, "pass"));
        assertThrows(IllegalArgumentException.class, () -> new GrafanaLogRecordDispatcher(
                "http://localhost:4318/v1/logs", null, null, null, 0, 1000, null));
        assertThrows(IllegalArgumentException.class, () -> new GrafanaLogRecordDispatcher(
                "http://localhost:4318/v1/logs", null, null, null, 1000, 0, null));
    }

    @Test
    @DisplayName("private helpers should cover null branches")
    void privateHelpersShouldCoverNullBranches() throws Exception {
        Method readStream = GrafanaLogRecordDispatcher.class.getDeclaredMethod("readStream", InputStream.class);
        readStream.setAccessible(true);
        assertEquals("", readStream.invoke(null, new Object[]{null}));

        Method closeInput = GrafanaLogRecordDispatcher.class.getDeclaredMethod("closeQuietly", InputStream.class);
        closeInput.setAccessible(true);
        closeInput.invoke(null, new Object[]{null});

        Method closeOutput = GrafanaLogRecordDispatcher.class.getDeclaredMethod("closeQuietly", java.io.OutputStream.class);
        closeOutput.setAccessible(true);
        closeOutput.invoke(null, new Object[]{null});
    }

    @Test
    @DisplayName("DispatchResult should report success only for 2xx statuses")
    void dispatchResultShouldReportSuccessRange() throws Exception {
        Constructor<?> constructor = GrafanaLogRecordDispatcher.DispatchResult.class
                .getDeclaredConstructor(int.class, String.class);
        constructor.setAccessible(true);
        GrafanaLogRecordDispatcher.DispatchResult ok =
                (GrafanaLogRecordDispatcher.DispatchResult) constructor.newInstance(299, null);
        GrafanaLogRecordDispatcher.DispatchResult redirect =
                (GrafanaLogRecordDispatcher.DispatchResult) constructor.newInstance(302, "r");

        assertTrue(ok.isSuccessful());
        assertEquals("", ok.getResponseBody());
        assertFalse(redirect.isSuccessful());
    }

    @Test
    @DisplayName("private helper methods should cover remaining branches")
    void privateHelpersShouldCoverRemainingBranches() throws Exception {
        GrafanaLogRecordDispatcher dispatcher = new GrafanaLogRecordDispatcher("http://localhost:4318/v1/logs");

        Method shouldUseLokiPushFormat = GrafanaLogRecordDispatcher.class.getDeclaredMethod("shouldUseLokiPushFormat");
        shouldUseLokiPushFormat.setAccessible(true);
        setField(dispatcher, "endpoint", allocateUninitializedUrl());
        assertFalse((Boolean) shouldUseLokiPushFormat.invoke(dispatcher));

        Method toLokiPushPayload = GrafanaLogRecordDispatcher.class.getDeclaredMethod("toLokiPushPayload", LogRecord.class);
        toLokiPushPayload.setAccessible(true);
        String lokiPayload = (String) toLokiPushPayload.invoke(dispatcher,
                logRecord(null, null, null, null, null, null, Instant.ofEpochSecond(-1)));
        assertTrue(lokiPayload.contains("\"service_name\":\"common-utils-messagehandler\""));
        assertFalse(lokiPayload.contains("\"severity\":\""));
        assertFalse(lokiPayload.contains("\"trace_id\":\""));
        assertFalse(lokiPayload.contains("\"span_id\":\""));
        assertTrue(lokiPayload.contains("\"0\""));

        Method resolveServiceName = GrafanaLogRecordDispatcher.class
                .getDeclaredMethod("resolveServiceName", Resource.class, InstrumentationScope.class);
        resolveServiceName.setAccessible(true);
        Resource noisyResource = resource(Arrays.asList(
                null,
                keyValue(null, anyString("x")),
                keyValue("other.key", anyString("v")),
                keyValue("service.name", null),
                keyValue("service.name", anyString("  "))));
        assertEquals("scope-service", resolveServiceName.invoke(null, noisyResource, scope("scope-service")));
        assertEquals("common-utils-messagehandler", resolveServiceName.invoke(
                null, resource(null), scope("   ")));
        assertEquals("orders", resolveServiceName.invoke(
                null, resource(Collections.singletonList(keyValue("service.name", anyString("orders")))),
                scope("fallback")));

        Method extractBodyAsString = GrafanaLogRecordDispatcher.class.getDeclaredMethod("extractBodyAsString", LogRecord.class);
        extractBodyAsString.setAccessible(true);
        assertEquals("", extractBodyAsString.invoke(null, logRecord(null, null, null, null, null, null, Instant.now())));
        assertEquals("", extractBodyAsString.invoke(null,
                logRecord(anyString(null), null, null, null, null, null, Instant.now())));
        assertEquals("msg", extractBodyAsString.invoke(null,
                logRecord(anyString("msg"), null, null, null, null, null, Instant.now())));

        Method toUnsignedNanosString = GrafanaLogRecordDispatcher.class
                .getDeclaredMethod("toUnsignedNanosString", Instant.class);
        toUnsignedNanosString.setAccessible(true);
        String nowNanos = (String) toUnsignedNanosString.invoke(null, new Object[]{null});
        assertTrue(nowNanos.matches("\\d+"));
        assertEquals("0", toUnsignedNanosString.invoke(null, Instant.ofEpochSecond(-1)));

        Method applyAuthentication = GrafanaLogRecordDispatcher.class
                .getDeclaredMethod("applyAuthentication", HttpURLConnection.class);
        applyAuthentication.setAccessible(true);
        GrafanaLogRecordDispatcher basicDispatcher = new GrafanaLogRecordDispatcher(
                "http://localhost:4318/v1/logs", "alice", "secret");
        setField(basicDispatcher, "password", null);
        StubConnection authConnection = new StubConnection(200, "ok", null);
        applyAuthentication.invoke(basicDispatcher, authConnection);
        assertFalse(authConnection.requestProperties.containsKey("Authorization"));

        Method toImmutableHeaders = GrafanaLogRecordDispatcher.class
                .getDeclaredMethod("toImmutableHeaders", Map.class);
        toImmutableHeaders.setAccessible(true);
        Map<?, ?> immutable = (Map<?, ?>) toImmutableHeaders.invoke(null, Collections.emptyMap());
        assertTrue(immutable.isEmpty());

        Method escapeJson = GrafanaLogRecordDispatcher.class.getDeclaredMethod("escapeJson", String.class);
        escapeJson.setAccessible(true);
        assertEquals("", escapeJson.invoke(null, new Object[]{null}));
        String escaped = (String) escapeJson.invoke(null, "\"\\\b\f\n\r\t" + ((char) 1) + "a");
        assertTrue(escaped.contains("\\\""));
        assertTrue(escaped.contains("\\\\"));
        assertTrue(escaped.contains("\\b"));
        assertTrue(escaped.contains("\\f"));
        assertTrue(escaped.contains("\\n"));
        assertTrue(escaped.contains("\\r"));
        assertTrue(escaped.contains("\\t"));
        assertTrue(escaped.contains("\\u0001"));

        Method readStream = GrafanaLogRecordDispatcher.class.getDeclaredMethod("readStream", InputStream.class);
        readStream.setAccessible(true);
        assertEquals("a\nb", readStream.invoke(null,
                new ByteArrayInputStream("a\nb".getBytes(StandardCharsets.UTF_8))));

        Method closeInput = GrafanaLogRecordDispatcher.class.getDeclaredMethod("closeQuietly", InputStream.class);
        closeInput.setAccessible(true);
        closeInput.invoke(null, new InputStream() {
            @Override
            public int read() {
                return -1;
            }

            @Override
            public void close() throws IOException {
                throw new IOException("forced");
            }
        });

        Method closeOutput = GrafanaLogRecordDispatcher.class.getDeclaredMethod("closeQuietly", java.io.OutputStream.class);
        closeOutput.setAccessible(true);
        closeOutput.invoke(null, new java.io.OutputStream() {
            @Override
            public void write(final int b) {
                // no-op
            }

            @Override
            public void close() throws IOException {
                throw new IOException("forced");
            }
        });
    }

    private static GrafanaLogRecordDispatcher dispatcherWithConnection(final StubConnection connection) throws Exception {
        GrafanaLogRecordDispatcher dispatcher = new GrafanaLogRecordDispatcher("http://localhost:4318/v1/logs");
        replaceEndpoint(dispatcher, urlFor(connection));
        return dispatcher;
    }

    private static URL urlFor(final StubConnection connection) throws Exception {
        return urlFor(connection, "/v1/logs");
    }

    private static URL urlFor(final StubConnection connection, final String path) throws Exception {
        return new URL(null, "http://unit.test" + path, new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(final URL u) {
                return connection;
            }
        });
    }

    private static void replaceEndpoint(final GrafanaLogRecordDispatcher dispatcher, final URL url) throws Exception {
        Field endpointField = GrafanaLogRecordDispatcher.class.getDeclaredField("endpoint");
        endpointField.setAccessible(true);
        endpointField.set(dispatcher, url);
    }

    private static void setField(final Object target, final String fieldName, final Object value) throws Exception {
        Field field = GrafanaLogRecordDispatcher.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static LogRecord logRecord(final AnyValue body,
                                       final String severityText,
                                       final String traceId,
                                       final String spanId,
                                       final Resource resource,
                                       final InstrumentationScope scope,
                                       final Instant timestamp) {
        return new LogRecord() {
            @Override
            public Instant getTimestamp() {
                return timestamp;
            }

            @Override
            public Instant getObservedTimestamp() {
                return null;
            }

            @Override
            public String getTraceId() {
                return traceId;
            }

            @Override
            public String getSpanId() {
                return spanId;
            }

            @Override
            public int getTraceFlags() {
                return 0;
            }

            @Override
            public String getSeverityText() {
                return severityText;
            }

            @Override
            public SeverityNumber getSeverityNumber() {
                return SeverityNumber.UNSPECIFIED;
            }

            @Override
            public AnyValue getBody() {
                return body;
            }

            @Override
            public Resource getResource() {
                return resource;
            }

            @Override
            public InstrumentationScope getInstrumentationScope() {
                return scope;
            }

            @Override
            public List<KeyValue> getAttributes() {
                return Collections.emptyList();
            }

            @Override
            public String getEventName() {
                return null;
            }
        };
    }

    private static Resource resource(final List<KeyValue> attributes) {
        return new Resource() {
            @Override
            public List<com.threeamigos.common.util.interfaces.messagehandler.otel.Entity> getEntities() {
                return Collections.emptyList();
            }

            @Override
            public String getSchemaUrl() {
                return null;
            }

            @Override
            public List<KeyValue> getAttributes() {
                return attributes;
            }

            @Override
            public Resource merge(final Resource other) {
                return this;
            }
        };
    }

    private static InstrumentationScope scope(final String name) {
        return new InstrumentationScope() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getVersion() {
                return null;
            }

            @Override
            public String getSchemaUrl() {
                return null;
            }

            @Override
            public List<KeyValue> getAttributes() {
                return Collections.emptyList();
            }

            @Override
            public int getDroppedAttributesCount() {
                return 0;
            }
        };
    }

    private static KeyValue keyValue(final String key, final AnyValue value) {
        return new KeyValue() {
            @Override
            public String getKey() {
                return key;
            }

            @Override
            public AnyValue getValue() {
                return value;
            }
        };
    }

    private static AnyValue anyString(final String value) {
        return new AnyValue() {
            @Override
            public Type getType() {
                return Type.STRING;
            }

            @Override
            public String asString() {
                return value;
            }

            @Override
            public boolean asBoolean() {
                return false;
            }

            @Override
            public long asLong() {
                return 0L;
            }

            @Override
            public double asDouble() {
                return 0.0d;
            }

            @Override
            public List<AnyValue> asArray() {
                return Collections.emptyList();
            }

            @Override
            public List<KeyValue> asKvList() {
                return Collections.emptyList();
            }

            @Override
            public byte[] asBytes() {
                return new byte[0];
            }
        };
    }

    private static URL allocateUninitializedUrl() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        return (URL) allocateInstance.invoke(unsafe, URL.class);
    }

    private static final class StubConnection extends HttpURLConnection {
        private final int statusCode;
        private final String inputBody;
        private final String errorBody;
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private final Map<String, String> requestProperties = new LinkedHashMap<String, String>();
        private String requestMethod;
        private boolean disconnected;
        private int outputStreamCalls = 0;

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
            outputStreamCalls++;
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
