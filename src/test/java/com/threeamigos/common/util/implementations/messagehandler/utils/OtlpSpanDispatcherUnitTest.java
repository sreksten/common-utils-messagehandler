package com.threeamigos.common.util.implementations.messagehandler.utils;

import com.threeamigos.common.util.implementations.messagehandler.otel.InstrumentationScopeFactory;
import com.threeamigos.common.util.implementations.messagehandler.otel.SpanContextImpl;
import com.threeamigos.common.util.implementations.messagehandler.otel.TraceStateImpl;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Event;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanData;
import com.threeamigos.common.util.interfaces.messagehandler.otel.StatusCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

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
    }

    private static SpanData fakeSpanData() {
        InstrumentationScope scope = InstrumentationScopeFactory.create(
                "orders", "1.0.0", null, Collections.<KeyValue>emptyList());
        SpanContext context = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 1,
                false,
                new TraceStateImpl());
        Instant now = Instant.now();
        return new SpanData() {
            @Override
            public String getName() {
                return "checkout";
            }

            @Override
            public SpanContext getSpanContext() {
                return context;
            }

            @Override
            public String getParentSpanId() {
                return "aaaabbbbccccdddd";
            }

            @Override
            public InstrumentationScope getInstrumentationScope() {
                return scope;
            }

            @Override
            public Instant getStartTimestamp() {
                return now;
            }

            @Override
            public Instant getEndTimestamp() {
                return now.plusMillis(1);
            }

            @Override
            public StatusCode getStatusCode() {
                return StatusCode.OK;
            }

            @Override
            public String getStatusDescription() {
                return "";
            }

            @Override
            public List<KeyValue> getAttributes() {
                return Collections.emptyList();
            }

            @Override
            public List<Event> getEvents() {
                return Collections.emptyList();
            }

            @Override
            public List<com.threeamigos.common.util.interfaces.messagehandler.otel.Link> getLinks() {
                return Collections.emptyList();
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

    private static final class StubConnection extends HttpURLConnection {
        private final int statusCode;
        private final String inputBody;
        private final String errorBody;
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

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
            return new ByteArrayInputStream(inputBody.getBytes());
        }

        @Override
        public InputStream getErrorStream() {
            if (errorBody == null) {
                return null;
            }
            return new ByteArrayInputStream(errorBody.getBytes());
        }

        String writtenBody() {
            return new String(outputStream.toByteArray());
        }
    }
}
