package com.threeamigos.common.util.implementations.messagehandler.utils;

import com.threeamigos.common.util.implementations.messagehandler.otel.SpanContextImpl;
import com.threeamigos.common.util.implementations.messagehandler.otel.TraceStateImpl;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Event;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Link;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanData;
import com.threeamigos.common.util.interfaces.messagehandler.otel.StatusCode;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OtlpSpanDispatcher unit tests")
@Tag("unit")
@Tag("messageHandler")
class OtlpSpanDispatcherUnitTest {

    @Test
    @DisplayName("should serialize span data into OTLP traces payload and post it")
    void shouldSerializeSpanDataIntoOtlpTracesPayloadAndPostIt() throws Exception {
        StubConnection connection = new StubConnection(200, "ok", null);
        OtlpSpanDispatcher dispatcher = new OtlpSpanDispatcher("http://localhost:4318/v1/traces");
        replaceEndpoint(dispatcher, urlFor(connection));

        dispatcher.dispatchSpan(fakeSpanData());

        String payload = connection.writtenBody();
        assertTrue(payload.contains("\"resourceSpans\""));
        assertTrue(payload.contains("\"key\":\"service.name\""));
        assertTrue(payload.contains("\"stringValue\":\"orders\""));
        assertTrue(payload.contains("\"key\":\"service.version\""));
        assertTrue(payload.contains("\"stringValue\":\"1.0.0\""));
        assertTrue(payload.contains("\"scopeSpans\""));
        assertTrue(payload.contains("\"traceId\":\"5b8efff798038103d269b633813fc60c\""));
        assertTrue(payload.contains("\"spanId\":\"eee19b7ec3c1b174\""));
        assertTrue(payload.contains("\"name\":\"checkout\""));
        assertEquals("POST", connection.requestMethod);
        assertEquals("application/json", connection.requestProperties.get("Content-Type"));
        assertTrue(connection.disconnected);
    }

    @Test
    @DisplayName("dispatchSpan should fail on non-success status and include response body")
    void dispatchSpanShouldFailOnNonSuccessStatus() throws Exception {
        StubConnection connection = new StubConnection(500, null, "boom");
        OtlpSpanDispatcher dispatcher = new OtlpSpanDispatcher("http://localhost:4318/v1/traces");
        replaceEndpoint(dispatcher, urlFor(connection));

        IOException thrown = assertThrows(IOException.class, () -> dispatcher.dispatchSpan(fakeSpanData()));
        assertTrue(thrown.getMessage().contains("500"));
        assertTrue(thrown.getMessage().contains("boom"));
    }

    @Test
    @DisplayName("dispatch methods should validate null inputs and below-200 statuses")
    void dispatchMethodsShouldValidateNullInputsAndBelow200Statuses() throws Exception {
        StubConnection connection = new StubConnection(199, null, "too-early");
        OtlpSpanDispatcher dispatcher = new OtlpSpanDispatcher("http://localhost:4318/v1/traces");
        replaceEndpoint(dispatcher, urlFor(connection));

        OtlpSpanDispatcher.DispatchResult result = dispatcher.dispatchFormatted("{\"resourceSpans\":[]}");
        assertEquals(199, result.getStatusCode());
        assertFalse(result.isSuccessful());
        assertEquals("too-early", result.getResponseBody());

        assertThrows(NullPointerException.class, () -> dispatcher.dispatchFormatted(null));
        assertThrows(NullPointerException.class, () -> dispatcher.dispatchSpan(null));
    }

    @Test
    @DisplayName("constructor should validate endpoint, auth config and timeout values")
    void constructorShouldValidateInputs() {
        assertThrows(IllegalArgumentException.class, () -> new OtlpSpanDispatcher(" "));
        assertThrows(IllegalArgumentException.class, () -> new OtlpSpanDispatcher("not-a-url"));
        assertThrows(IllegalArgumentException.class, () -> new OtlpSpanDispatcher(
                "http://localhost:4318/v1/traces", "user", null));
        assertThrows(IllegalArgumentException.class, () -> new OtlpSpanDispatcher(
                "http://localhost:4318/v1/traces", null, "pass"));
        assertThrows(IllegalArgumentException.class, () -> new OtlpSpanDispatcher(
                "http://localhost:4318/v1/traces", null, null, null, 0, 1000, null));
        assertThrows(IllegalArgumentException.class, () -> new OtlpSpanDispatcher(
                "http://localhost:4318/v1/traces", null, null, null, 1000, 0, null));
    }

    @Test
    @DisplayName("private helpers should cover remaining branches")
    void privateHelpersShouldCoverRemainingBranches() throws Exception {
        OtlpSpanDispatcher dispatcher = new OtlpSpanDispatcher("http://localhost:4318/v1/traces");

        Method toExportTracesPayload = OtlpSpanDispatcher.class.getDeclaredMethod("toExportTracesPayload", SpanData.class);
        toExportTracesPayload.setAccessible(true);

        String payloadWithDefaults = (String) toExportTracesPayload.invoke(dispatcher,
                spanData("minimal", spanContext(), "parent1234",
                        null, Instant.ofEpochSecond(-1), Instant.ofEpochSecond(-1),
                        null, null, null, null));
        assertTrue(payloadWithDefaults.contains("common-utils-messagehandler"));
        assertTrue(payloadWithDefaults.contains("\"parentSpanId\":\"parent1234\""));
        assertFalse(payloadWithDefaults.contains("\"status\":"));

        String payloadWithVersionOnlyScope = (String) toExportTracesPayload.invoke(dispatcher,
                spanData("version-only", spanContext(), null,
                        scope(null, "2.0"), Instant.now(), Instant.now().plusMillis(1),
                        StatusCode.OK, "   ", Collections.<KeyValue>emptyList(), Collections.<Event>emptyList()));
        assertTrue(payloadWithVersionOnlyScope.contains("\"version\":\"2.0\""));
        assertFalse(payloadWithVersionOnlyScope.contains("\"name\":\"null\""));
        assertTrue(payloadWithVersionOnlyScope.contains("STATUS_CODE_OK"));

        List<KeyValue> attributes = Arrays.asList(
                null,
                keyValue(null, anyString("x")),
                keyValue("flag", anyBool(true)),
                keyValue("count", anyInt(7L)),
                keyValue("ratio", anyDouble(2.5d)),
                keyValue("text", anyString("hello")));
        List<Event> events = Arrays.asList(
                null,
                event("evt-null-attrs", Instant.now(), null),
                event("evt-empty-attrs", Instant.now(), Collections.<KeyValue>emptyList()),
                event("evt", Instant.now(), Arrays.asList(
                        null,
                        keyValue("evtKey", anyString("evtValue")))));
        String payloadWithEverything = (String) toExportTracesPayload.invoke(dispatcher,
                spanData("checkout", spanContext(), null,
                        scope("orders", "1.0.0"), Instant.now(), Instant.now().plusMillis(1),
                        StatusCode.ERROR, "failed", attributes, events));
        assertTrue(payloadWithEverything.contains("\"key\":\"flag\""));
        assertTrue(payloadWithEverything.contains("\"boolValue\":true"));
        assertTrue(payloadWithEverything.contains("\"intValue\":\"7\""));
        assertTrue(payloadWithEverything.contains("\"doubleValue\":2.5"));
        assertTrue(payloadWithEverything.contains("\"stringValue\":\"hello\""));
        assertTrue(payloadWithEverything.contains("\"events\""));
        assertTrue(payloadWithEverything.contains("\"name\":\"evt\""));
        assertTrue(payloadWithEverything.contains("STATUS_CODE_ERROR"));
        assertTrue(payloadWithEverything.contains("\"message\":\"failed\""));

        Method appendStringKeyValueAttribute = OtlpSpanDispatcher.class.getDeclaredMethod(
                "appendStringKeyValueAttribute", StringBuilder.class, String.class, String.class);
        appendStringKeyValueAttribute.setAccessible(true);
        StringBuilder stringAttr = new StringBuilder();
        appendStringKeyValueAttribute.invoke(null, stringAttr, "k", null);
        assertTrue(stringAttr.toString().contains("\"stringValue\":\"\""));

        Method appendAttributes = OtlpSpanDispatcher.class.getDeclaredMethod(
                "appendAttributes", StringBuilder.class, List.class);
        appendAttributes.setAccessible(true);
        StringBuilder attrsBuilder = new StringBuilder();
        appendAttributes.invoke(null, attrsBuilder, Arrays.asList(
                null,
                keyValue(null, anyString("x")),
                keyValue("a", null),
                keyValue("first", anyString("one")),
                keyValue("second", anyInt(2L))));
        String attrsText = attrsBuilder.toString();
        assertTrue(attrsText.contains("\"key\":\"first\""));
        assertTrue(attrsText.contains("\"key\":\"second\""));
        assertTrue(attrsText.contains(","));

        Method appendAnyValue = OtlpSpanDispatcher.class.getDeclaredMethod(
                "appendAnyValue", StringBuilder.class, AnyValue.class);
        appendAnyValue.setAccessible(true);
        assertAnyValueRendering(appendAnyValue, null, "\"stringValue\":\"\"");
        assertAnyValueRendering(appendAnyValue, anyWithType(null), "\"stringValue\":\"\"");
        assertAnyValueRendering(appendAnyValue, anyBool(true), "\"boolValue\":true");
        assertAnyValueRendering(appendAnyValue, anyInt(11L), "\"intValue\":\"11\"");
        assertAnyValueRendering(appendAnyValue, anyDouble(4.2d), "\"doubleValue\":4.2");
        assertAnyValueRendering(appendAnyValue, anyString("s"), "\"stringValue\":\"s\"");
        assertAnyValueRendering(appendAnyValue, anyBytes("raw"), "\"stringValue\":\"raw\"");

        Method applyAuthentication = OtlpSpanDispatcher.class.getDeclaredMethod(
                "applyAuthentication", HttpURLConnection.class);
        applyAuthentication.setAccessible(true);
        StubConnection bearerConnection = new StubConnection(200, "ok", null);
        OtlpSpanDispatcher bearerDispatcher = new OtlpSpanDispatcher(
                "http://localhost:4318/v1/traces", null, null, "token-1", 1000, 1000, null);
        applyAuthentication.invoke(bearerDispatcher, bearerConnection);
        assertEquals("Bearer token-1", bearerConnection.requestProperties.get("Authorization"));

        StubConnection basicConnection = new StubConnection(200, "ok", null);
        OtlpSpanDispatcher basicDispatcher = new OtlpSpanDispatcher(
                "http://localhost:4318/v1/traces", "alice", "secret");
        applyAuthentication.invoke(basicDispatcher, basicConnection);
        assertTrue(basicConnection.requestProperties.get("Authorization").startsWith("Basic "));

        StubConnection noAuthConnection = new StubConnection(200, "ok", null);
        setField(basicDispatcher, "password", null);
        applyAuthentication.invoke(basicDispatcher, noAuthConnection);
        assertFalse(noAuthConnection.requestProperties.containsKey("Authorization"));

        Method applyAdditionalHeaders = OtlpSpanDispatcher.class.getDeclaredMethod(
                "applyAdditionalHeaders", HttpURLConnection.class);
        applyAdditionalHeaders.setAccessible(true);
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("X-Valid", "ok");
        headers.put(" ", "skip");
        headers.put("X-Null", null);
        OtlpSpanDispatcher headerDispatcher = new OtlpSpanDispatcher(
                "http://localhost:4318/v1/traces", null, null, null, 1000, 1000, headers);
        StubConnection headerConnection = new StubConnection(200, "ok", null);
        applyAdditionalHeaders.invoke(headerDispatcher, headerConnection);
        assertEquals("ok", headerConnection.requestProperties.get("X-Valid"));
        assertFalse(headerConnection.requestProperties.containsKey("X-Null"));

        Method toUnsignedNanosString = OtlpSpanDispatcher.class.getDeclaredMethod(
                "toUnsignedNanosString", Instant.class);
        toUnsignedNanosString.setAccessible(true);
        String nowNanos = (String) toUnsignedNanosString.invoke(null, new Object[]{null});
        assertTrue(nowNanos.matches("\\d+"));
        assertEquals("0", toUnsignedNanosString.invoke(null, Instant.ofEpochSecond(-1)));

        Method toImmutableHeaders = OtlpSpanDispatcher.class.getDeclaredMethod(
                "toImmutableHeaders", Map.class);
        toImmutableHeaders.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> immutable = (Map<String, String>) toImmutableHeaders.invoke(
                null, Collections.singletonMap("A", "B"));
        assertEquals("B", immutable.get("A"));
        assertThrows(UnsupportedOperationException.class, () -> immutable.put("C", "D"));
        assertTrue(((Map<?, ?>) toImmutableHeaders.invoke(null, new Object[]{null})).isEmpty());
        assertTrue(((Map<?, ?>) toImmutableHeaders.invoke(null, Collections.<String, String>emptyMap())).isEmpty());

        Method hasText = OtlpSpanDispatcher.class.getDeclaredMethod("hasText", String.class);
        hasText.setAccessible(true);
        assertFalse((Boolean) hasText.invoke(null, new Object[]{null}));
        assertFalse((Boolean) hasText.invoke(null, "   "));
        assertTrue((Boolean) hasText.invoke(null, "x"));

        Method readStream = OtlpSpanDispatcher.class.getDeclaredMethod("readStream", InputStream.class);
        readStream.setAccessible(true);
        assertEquals("", readStream.invoke(null, new Object[]{null}));
        assertEquals("a\nb", readStream.invoke(null,
                new ByteArrayInputStream("a\nb".getBytes(StandardCharsets.UTF_8))));

        Method escapeJson = OtlpSpanDispatcher.class.getDeclaredMethod("escapeJson", String.class);
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

        Method closeInput = OtlpSpanDispatcher.class.getDeclaredMethod("closeQuietly", InputStream.class);
        closeInput.setAccessible(true);
        closeInput.invoke(null, new Object[]{null});
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

        Method closeOutput = OtlpSpanDispatcher.class.getDeclaredMethod("closeQuietly", OutputStream.class);
        closeOutput.setAccessible(true);
        closeOutput.invoke(null, new Object[]{null});
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
    void dispatchResultShouldReportSuccessOnlyFor2xxStatuses() throws Exception {
        Constructor<?> constructor = OtlpSpanDispatcher.DispatchResult.class
                .getDeclaredConstructor(int.class, String.class);
        constructor.setAccessible(true);
        OtlpSpanDispatcher.DispatchResult ok =
                (OtlpSpanDispatcher.DispatchResult) constructor.newInstance(299, null);
        OtlpSpanDispatcher.DispatchResult redirect =
                (OtlpSpanDispatcher.DispatchResult) constructor.newInstance(302, "redirect");

        assertTrue(ok.isSuccessful());
        assertEquals("", ok.getResponseBody());
        assertFalse(redirect.isSuccessful());
    }

    private static void assertAnyValueRendering(final Method appendAnyValue,
                                                final AnyValue anyValue,
                                                final String expectedFragment) throws Exception {
        StringBuilder sb = new StringBuilder();
        appendAnyValue.invoke(null, sb, anyValue);
        assertTrue(sb.toString().contains(expectedFragment), "Unexpected AnyValue rendering: " + sb);
    }

    private static SpanData fakeSpanData() {
        return spanData(
                "checkout",
                spanContext(),
                "aaaabbbbccccdddd",
                scope("orders", "1.0.0"),
                Instant.now(),
                Instant.now().plusMillis(1),
                StatusCode.OK,
                "",
                Collections.<KeyValue>emptyList(),
                Collections.<Event>emptyList());
    }

    private static SpanData spanData(final String name,
                                     final SpanContext spanContext,
                                     final String parentSpanId,
                                     final InstrumentationScope instrumentationScope,
                                     final Instant start,
                                     final Instant end,
                                     final StatusCode statusCode,
                                     final String statusDescription,
                                     final List<KeyValue> attributes,
                                     final List<Event> events) {
        return new SpanData() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public SpanContext getSpanContext() {
                return spanContext;
            }

            @Override
            public String getParentSpanId() {
                return parentSpanId;
            }

            @Override
            public InstrumentationScope getInstrumentationScope() {
                return instrumentationScope;
            }

            @Override
            public Instant getStartTimestamp() {
                return start;
            }

            @Override
            public Instant getEndTimestamp() {
                return end;
            }

            @Override
            public StatusCode getStatusCode() {
                return statusCode;
            }

            @Override
            public String getStatusDescription() {
                return statusDescription;
            }

            @Override
            public List<KeyValue> getAttributes() {
                return attributes;
            }

            @Override
            public List<Event> getEvents() {
                return events;
            }

            @Override
            public List<Link> getLinks() {
                return Collections.emptyList();
            }
        };
    }

    private static SpanContext spanContext() {
        return new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 1,
                false,
                new TraceStateImpl());
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

    private static Event event(final String name, final Instant timestamp, final List<KeyValue> attributes) {
        return new Event() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Instant getTimestamp() {
                return timestamp;
            }

            @Override
            public List<KeyValue> getAttributes() {
                return attributes;
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

    private static AnyValue anyWithType(final AnyValue.Type type) {
        return new AnyValue() {
            @Override
            public Type getType() {
                return type;
            }

            @Override
            public String asString() {
                return "";
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

    private static AnyValue anyBool(final boolean value) {
        return new AnyValue() {
            @Override
            public Type getType() {
                return Type.BOOL;
            }

            @Override
            public String asString() {
                return "";
            }

            @Override
            public boolean asBoolean() {
                return value;
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

    private static AnyValue anyInt(final long value) {
        return new AnyValue() {
            @Override
            public Type getType() {
                return Type.INT;
            }

            @Override
            public String asString() {
                return "";
            }

            @Override
            public boolean asBoolean() {
                return false;
            }

            @Override
            public long asLong() {
                return value;
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

    private static AnyValue anyDouble(final double value) {
        return new AnyValue() {
            @Override
            public Type getType() {
                return Type.DOUBLE;
            }

            @Override
            public String asString() {
                return "";
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
                return value;
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

    private static AnyValue anyBytes(final String asString) {
        return new AnyValue() {
            @Override
            public Type getType() {
                return Type.BYTES;
            }

            @Override
            public String asString() {
                return asString;
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
                return new byte[]{1, 2, 3};
            }
        };
    }

    private static URL urlFor(final StubConnection connection) throws Exception {
        return new URL(null, "http://unit.test/v1/traces", new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(final URL u) {
                return connection;
            }
        });
    }

    private static void replaceEndpoint(final OtlpSpanDispatcher dispatcher, final URL url) throws Exception {
        Field endpointField = OtlpSpanDispatcher.class.getDeclaredField("endpoint");
        endpointField.setAccessible(true);
        endpointField.set(dispatcher, url);
    }

    private static void setField(final OtlpSpanDispatcher dispatcher,
                                 final String fieldName,
                                 final Object value) throws Exception {
        Field field = OtlpSpanDispatcher.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(dispatcher, value);
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
