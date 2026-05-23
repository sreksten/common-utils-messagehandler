package com.threeamigos.common.util.implementations.messagehandler.utils;

import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordFactoryImpl;
import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordImpl;
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
import java.io.OutputStream;
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
        assertFalse(connection.disconnected);
    }

    @Test
    @DisplayName("dispatch(LogRecord) should transform logs into span events when endpoint is /v1/traces")
    void dispatchShouldTransformLogsIntoSpanEventsForTraceEndpoint() throws Exception {
        StubConnection connection = new StubConnection(200, "ok", null);
        JaegerLogRecordDispatcher dispatcher = new JaegerLogRecordDispatcher("http://localhost:4318/v1/traces");
        replaceEndpoint(dispatcher, urlFor(connection, "/v1/traces"));

        LogRecordImpl record = (LogRecordImpl) new LogRecordFactoryImpl().create(SeverityNumber.INFO, "hello jaeger");
        record.setTraceId("0123456789abcdef0123456789abcdef");
        record.setSpanId("89abcdef01234567");

        JaegerLogRecordDispatcher.DispatchResult result = dispatcher.dispatch(record);

        assertEquals(200, result.getStatusCode());
        assertTrue(result.isSuccessful());
        String body = connection.writtenBody();
        assertTrue(body.contains("\"resourceSpans\""));
        assertTrue(body.contains("\"events\""));
        assertTrue(body.contains("\"traceId\":\"0123456789abcdef0123456789abcdef\""));
        assertTrue(body.contains("\"parentSpanId\":\"89abcdef01234567\""));
        assertTrue(body.contains("hello jaeger"));
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
    @DisplayName("dispatchOrThrow should return result for successful statuses")
    void dispatchOrThrowShouldReturnResultOnSuccess() throws Exception {
        StubConnection connection = new StubConnection(204, "ok", null);
        JaegerLogRecordDispatcher dispatcher = dispatcherWithConnection(connection);
        LogRecord record = new LogRecordFactoryImpl().create(SeverityNumber.INFO, "ok");

        JaegerLogRecordDispatcher.DispatchResult result = dispatcher.dispatchOrThrow(record);

        assertEquals(204, result.getStatusCode());
        assertTrue(result.isSuccessful());
        assertEquals("ok", result.getResponseBody());
    }

    @Test
    @DisplayName("dispatchLogRecord should delegate formatter and throw on below-200 status")
    void dispatchLogRecordAndBelow200StatusShouldBeHandled() throws Exception {
        StubConnection delegatedConnection = new StubConnection(200, "ok", null);
        JaegerLogRecordDispatcher dispatcher = dispatcherWithConnection(delegatedConnection);
        LogRecord record = new LogRecordFactoryImpl().create(SeverityNumber.INFO, "delegated");

        dispatcher.dispatchLogRecord(record, lr -> "{\"resourceLogs\":[]}");
        assertEquals("{\"resourceLogs\":[]}", delegatedConnection.writtenBody());

        StubConnection tooEarlyConnection = new StubConnection(199, null, "too-early");
        JaegerLogRecordDispatcher tooEarlyDispatcher = dispatcherWithConnection(tooEarlyConnection);
        JaegerLogRecordDispatcher.DispatchResult result = tooEarlyDispatcher.dispatchFormatted("{\"resourceLogs\":[]}");
        HttpDispatchStatusException thrown = assertThrows(
                HttpDispatchStatusException.class,
                () -> tooEarlyDispatcher.dispatchLogRecord(record, lr -> "{\"resourceLogs\":[]}")
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
        JaegerLogRecordDispatcher dispatcher = dispatcherWithConnection(connection);
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
    @DisplayName("private helpers should cover remaining branches")
    void privateHelpersShouldCoverRemainingBranches() throws Exception {
        JaegerLogRecordDispatcher dispatcher = new JaegerLogRecordDispatcher("http://localhost:4318/v1/traces");

        Method shouldTransform = JaegerLogRecordDispatcher.class
                .getDeclaredMethod("shouldTransformLogsIntoTraceSpanEvents");
        shouldTransform.setAccessible(true);
        assertTrue((Boolean) shouldTransform.invoke(dispatcher));

        replaceEndpoint(dispatcher, urlFor(new StubConnection(200, "ok", null), "/v1/logs"));
        assertFalse((Boolean) shouldTransform.invoke(dispatcher));

        URL maybeNullPathUrl = urlWithNullPath();
        replaceEndpoint(dispatcher, maybeNullPathUrl);
        assertFalse((Boolean) shouldTransform.invoke(dispatcher));

        Method toExportTracesRequestJson = JaegerLogRecordDispatcher.class
                .getDeclaredMethod("toExportTracesRequestJson", LogRecord.class);
        toExportTracesRequestJson.setAccessible(true);

        String payloadDefault = (String) toExportTracesRequestJson.invoke(dispatcher,
                logRecord(null, null, null, null, null, null, null, null));
        assertTrue(payloadDefault.contains("common-utils-messagehandler"));
        assertTrue(payloadDefault.contains("\"name\":\"log-event\""));
        assertTrue(payloadDefault.contains("\"name\":\"log\""));
        assertFalse(payloadDefault.contains("\"parentSpanId\""));

        String payloadWithScopeAndResource = (String) toExportTracesRequestJson.invoke(dispatcher,
                logRecord(
                        "ABCDEF0123456789ABCDEF0123456789",
                        "ABCDEF0123456789",
                        Instant.ofEpochSecond(-1),
                        "WARN",
                        "evt",
                        anyString("body"),
                        resource(Arrays.asList(
                                null,
                                keyValue(null, anyString("x")),
                                keyValue("service.name", anyString("payments")))),
                        scope("orders", "1.0")));
        assertTrue(payloadWithScopeAndResource.contains("\"traceId\":\"abcdef0123456789abcdef0123456789\""));
        assertTrue(payloadWithScopeAndResource.contains("\"parentSpanId\":\"abcdef0123456789\""));
        assertTrue(payloadWithScopeAndResource.contains("\"name\":\"log-warn\""));
        assertTrue(payloadWithScopeAndResource.contains("\"name\":\"evt\""));
        assertTrue(payloadWithScopeAndResource.contains("\"version\":\"1.0\""));
        assertTrue(payloadWithScopeAndResource.contains("\"stringValue\":\"payments\""));
        assertTrue(payloadWithScopeAndResource.contains("\"startTimeUnixNano\":\"0\""));

        String payloadVersionOnlyScope = (String) toExportTracesRequestJson.invoke(dispatcher,
                logRecord(
                        "00000000000000000000000000000000",
                        "0000000000000000",
                        Instant.now(),
                        "  ",
                        " ",
                        anyString(null),
                        resource(Collections.<KeyValue>emptyList()),
                        scope(null, "2.0")));
        assertTrue(payloadVersionOnlyScope.contains("\"version\":\"2.0\""));
        assertFalse(payloadVersionOnlyScope.contains("\"name\":\"null\""));
        assertFalse(payloadVersionOnlyScope.contains("\"parentSpanId\""));
        assertTrue(payloadVersionOnlyScope.contains("\"name\":\"log-event\""));
        assertTrue(payloadVersionOnlyScope.contains("\"name\":\"log\""));

        Method extractBodyAsString = JaegerLogRecordDispatcher.class
                .getDeclaredMethod("extractBodyAsString", LogRecord.class);
        extractBodyAsString.setAccessible(true);
        assertEquals("", extractBodyAsString.invoke(null,
                logRecord(null, null, Instant.now(), "INFO", "evt", null, null, null)));
        assertEquals("", extractBodyAsString.invoke(null,
                logRecord(null, null, Instant.now(), "INFO", "evt", anyString(null), null, null)));
        assertEquals("x", extractBodyAsString.invoke(null,
                logRecord(null, null, Instant.now(), "INFO", "evt", anyString("x"), null, null)));

        Method resolveServiceName = JaegerLogRecordDispatcher.class
                .getDeclaredMethod("resolveServiceName", Resource.class, String.class);
        resolveServiceName.setAccessible(true);
        assertEquals("scope-A", resolveServiceName.invoke(null, null, "scope-A"));
        assertEquals("common-utils-messagehandler", resolveServiceName.invoke(null, null, null));
        assertEquals("scope-B", resolveServiceName.invoke(null, resource(null), "scope-B"));
        assertEquals("scope-C", resolveServiceName.invoke(null,
                resource(Arrays.asList(
                        null,
                        keyValue(null, anyString("x")),
                        keyValue("other", anyString("v")),
                        keyValue("service.name", null),
                        keyValue("service.name", anyString("  ")))),
                "scope-C"));
        assertEquals("orders", resolveServiceName.invoke(null,
                resource(Collections.singletonList(keyValue("service.name", anyString("orders")))),
                "scope-D"));

        Method toUnsignedNanosString = JaegerLogRecordDispatcher.class
                .getDeclaredMethod("toUnsignedNanosString", Instant.class);
        toUnsignedNanosString.setAccessible(true);
        String nanosNow = (String) toUnsignedNanosString.invoke(null, new Object[]{null});
        assertTrue(nanosNow.matches("\\d+"));
        assertEquals("0", toUnsignedNanosString.invoke(null, Instant.ofEpochSecond(-1)));

        Method normalizeHexId = JaegerLogRecordDispatcher.class
                .getDeclaredMethod("normalizeHexId", String.class, int.class);
        normalizeHexId.setAccessible(true);
        assertEquals(null, normalizeHexId.invoke(null, null, 32));
        assertEquals(null, normalizeHexId.invoke(null, "abc", 32));
        assertEquals("12345678901234567890123456789012",
                normalizeHexId.invoke(null, "12345678901234567890123456789012", 32));
        assertEquals(null, normalizeHexId.invoke(null, "/2345678901234567890123456789012", 32));
        assertEquals(null, normalizeHexId.invoke(null, "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz", 32));
        assertEquals(null, normalizeHexId.invoke(null, "00000000000000000000000000000000", 32));
        assertEquals("abcdefabcdefabcdefabcdefabcdefab",
                normalizeHexId.invoke(null, "ABCDEFABCDEFABCDEFABCDEFABCDEFAB", 32));

        Method appendStringKeyValueAttribute = JaegerLogRecordDispatcher.class
                .getDeclaredMethod("appendStringKeyValueAttribute", StringBuilder.class, String.class, String.class);
        appendStringKeyValueAttribute.setAccessible(true);
        StringBuilder attrBuilder = new StringBuilder();
        appendStringKeyValueAttribute.invoke(null, attrBuilder, "k", null);
        assertTrue(attrBuilder.toString().contains("\"stringValue\":\"\""));

        Method escapeJson = JaegerLogRecordDispatcher.class.getDeclaredMethod("escapeJson", String.class);
        escapeJson.setAccessible(true);
        assertEquals("", escapeJson.invoke(null, new Object[]{null}));
        String escaped = (String) escapeJson.invoke(null, "\"\\\b\f\n\r\t" + ((char) 1) + "x");
        assertTrue(escaped.contains("\\\""));
        assertTrue(escaped.contains("\\\\"));
        assertTrue(escaped.contains("\\b"));
        assertTrue(escaped.contains("\\f"));
        assertTrue(escaped.contains("\\n"));
        assertTrue(escaped.contains("\\r"));
        assertTrue(escaped.contains("\\t"));
        assertTrue(escaped.contains("\\u0001"));

        Method applyAuthentication = JaegerLogRecordDispatcher.class
                .getDeclaredMethod("applyAuthentication", HttpURLConnection.class);
        applyAuthentication.setAccessible(true);

        StubConnection bearerConnection = new StubConnection(200, "ok", null);
        JaegerLogRecordDispatcher bearerDispatcher = new JaegerLogRecordDispatcher(
                "http://localhost:4318/v1/logs", null, null, "token-9", 1000, 1000, null);
        applyAuthentication.invoke(bearerDispatcher, bearerConnection);
        assertEquals("Bearer token-9", bearerConnection.requestProperties.get("Authorization"));

        StubConnection basicConnection = new StubConnection(200, "ok", null);
        JaegerLogRecordDispatcher basicDispatcher = new JaegerLogRecordDispatcher(
                "http://localhost:4318/v1/logs", "alice", "secret");
        applyAuthentication.invoke(basicDispatcher, basicConnection);
        assertTrue(basicConnection.requestProperties.get("Authorization").startsWith("Basic "));

        StubConnection noAuthConnection = new StubConnection(200, "ok", null);
        setField(basicDispatcher, "password", null);
        applyAuthentication.invoke(basicDispatcher, noAuthConnection);
        assertFalse(noAuthConnection.requestProperties.containsKey("Authorization"));

        Method toImmutableHeaders = JaegerLogRecordDispatcher.class
                .getDeclaredMethod("toImmutableHeaders", Map.class);
        toImmutableHeaders.setAccessible(true);
        assertTrue(((Map<?, ?>) toImmutableHeaders.invoke(null, new Object[]{null})).isEmpty());
        assertTrue(((Map<?, ?>) toImmutableHeaders.invoke(null, Collections.<String, String>emptyMap())).isEmpty());
        @SuppressWarnings("unchecked")
        Map<String, String> immutableHeaders = (Map<String, String>) toImmutableHeaders.invoke(
                null, Collections.singletonMap("A", "B"));
        assertEquals("B", immutableHeaders.get("A"));
        assertThrows(UnsupportedOperationException.class, () -> immutableHeaders.put("C", "D"));

        Method readStream = JaegerLogRecordDispatcher.class.getDeclaredMethod("readStream", InputStream.class);
        readStream.setAccessible(true);
        assertEquals("a\nb", readStream.invoke(null,
                new ByteArrayInputStream("a\nb".getBytes(StandardCharsets.UTF_8))));

        Method closeInput = JaegerLogRecordDispatcher.class.getDeclaredMethod("closeQuietly", InputStream.class);
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

        Method closeOutput = JaegerLogRecordDispatcher.class.getDeclaredMethod("closeQuietly", OutputStream.class);
        closeOutput.setAccessible(true);
        closeOutput.invoke(null, new OutputStream() {
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

    @Test
    @DisplayName("DispatchResult should report success only for 2xx statuses")
    void dispatchResultShouldReportSuccessRange() throws Exception {
        Constructor<?> constructor = JaegerLogRecordDispatcher.DispatchResult.class
                .getDeclaredConstructor(int.class, String.class);
        constructor.setAccessible(true);
        JaegerLogRecordDispatcher.DispatchResult ok =
                (JaegerLogRecordDispatcher.DispatchResult) constructor.newInstance(299, null);
        JaegerLogRecordDispatcher.DispatchResult below =
                (JaegerLogRecordDispatcher.DispatchResult) constructor.newInstance(199, "too-early");
        JaegerLogRecordDispatcher.DispatchResult redirect =
                (JaegerLogRecordDispatcher.DispatchResult) constructor.newInstance(302, "r");

        assertTrue(ok.isSuccessful());
        assertEquals("", ok.getResponseBody());
        assertFalse(below.isSuccessful());
        assertFalse(redirect.isSuccessful());
    }

    private static LogRecord logRecord(final String traceId,
                                       final String spanId,
                                       final Instant timestamp,
                                       final String severityText,
                                       final String eventName,
                                       final AnyValue body,
                                       final Resource resource,
                                       final InstrumentationScope scope) {
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
                return eventName;
            }
        };
    }

    private static InstrumentationScope scope(final String name, final String version) {
        return new InstrumentationScope() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getVersion() {
                return version;
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

    private static void setField(final JaegerLogRecordDispatcher dispatcher,
                                 final String fieldName,
                                 final Object value) throws Exception {
        Field field = JaegerLogRecordDispatcher.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(dispatcher, value);
    }

    private static URL urlWithNullPath() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        return (URL) allocateInstance.invoke(unsafe, URL.class);
    }

    private static JaegerLogRecordDispatcher dispatcherWithConnection(final StubConnection connection) throws Exception {
        JaegerLogRecordDispatcher dispatcher = new JaegerLogRecordDispatcher("http://localhost:4318/v1/logs");
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
