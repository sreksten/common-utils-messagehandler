package com.threeamigos.common.util.interfaces.messagehandler.otel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Span interface unit tests")
@Tag("unit")
@Tag("messageHandler")
class SpanInterfaceUnitTest {

    @Test
    @DisplayName("default aliases should delegate to core span APIs")
    void defaultAliasesShouldDelegateToCoreSpanApis() {
        ProbeSpan span = new ProbeSpan();

        assertSame(span.spanContext, span.getContext());
        span.addAttribute("k", null);

        assertEquals(1, span.setAttributeCalls);
        assertEquals("k", span.lastAttributeKey);
    }

    @Test
    @DisplayName("default create should return a wrapped non-recording span")
    void defaultCreateShouldReturnWrappedNonRecordingSpan() {
        ProbeSpan span = new ProbeSpan();

        Span child = span.create("child-span");

        assertFalse(child.isRecording());
        assertSame(span.getSpanContext(), child.getSpanContext());
    }

    @Test
    @DisplayName("wrap should expose the provided context and remain non-recording")
    void wrapShouldExposeProvidedContextAndRemainNonRecording() {
        SpanContext providedContext = new StubSpanContext();

        Span wrapped = Span.wrap(providedContext);

        assertSame(providedContext, wrapped.getSpanContext());
        assertFalse(wrapped.isRecording());
        assertNull(wrapped.getInstrumentationScope());
    }

    @Test
    @DisplayName("wrap null should expose invalid context and no-op operations")
    void wrapNullShouldExposeInvalidContextAndNoOpOperations() {
        Span wrapped = Span.wrap(null);
        SpanContext context = wrapped.getSpanContext();

        assertEquals("00000000000000000000000000000000", context.getTraceId());
        assertArrayEquals(new byte[16], context.getTraceIdBytes());
        assertEquals("0000000000000000", context.getSpanId());
        assertArrayEquals(new byte[8], context.getSpanIdBytes());
        assertEquals((byte) 0x00, context.getTraceFlags());
        assertFalse(context.isSampled());
        assertFalse(context.isRandom());
        assertFalse(context.isValid());
        assertFalse(context.isRemote());

        TraceState traceState = context.getTraceState();
        assertNull(traceState.get("missing"));
        assertSame(traceState, traceState.set("k", null));
        assertSame(traceState, traceState.delete("k"));
        assertTrue(traceState.getValues().isEmpty());

        wrapped.setAttribute("key", null);
        wrapped.addAttribute("alias-key", null);
        wrapped.addEvent("event");
        wrapped.addEvent("event", Collections.<KeyValue>emptyList());
        wrapped.addEvent("event", Collections.<KeyValue>emptyList(), Instant.now());
        wrapped.addEvent((Event) null);
        wrapped.addLink((SpanContext) null);
        wrapped.addLink((SpanContext) null, Collections.<KeyValue>emptyList());
        wrapped.addLink((Link) null);
        wrapped.setStatus(StatusCode.OK);
        wrapped.setStatus(StatusCode.ERROR, "desc");
        wrapped.updateName("renamed");
        wrapped.end();
        wrapped.end(Instant.now());
        wrapped.recordException(new RuntimeException("boom"));
        wrapped.recordException(new RuntimeException("boom"), Collections.<KeyValue>emptyList());
    }

    private static final class ProbeSpan implements Span {
        private final SpanContext spanContext = new StubSpanContext();
        private int setAttributeCalls;
        private String lastAttributeKey;

        @Override
        public SpanContext getSpanContext() {
            return spanContext;
        }

        @Override
        public boolean isRecording() {
            return true;
        }

        @Override
        public void setAttribute(final String key, final AnyValue value) {
            setAttributeCalls++;
            lastAttributeKey = key;
        }

        @Override
        public void addEvent(final String name) {
            // No-op.
        }

        @Override
        public void addEvent(final String name, final List<KeyValue> attributes) {
            // No-op.
        }

        @Override
        public void addEvent(final String name,
                             final List<KeyValue> attributes,
                             final Instant timestamp) {
            // No-op.
        }

        @Override
        public void addEvent(final Event event) {
            // No-op.
        }

        @Override
        public void addLink(final SpanContext spanContext) {
            // No-op.
        }

        @Override
        public void addLink(final SpanContext spanContext, final List<KeyValue> attributes) {
            // No-op.
        }

        @Override
        public void addLink(final Link link) {
            // No-op.
        }

        @Override
        public void setStatus(final StatusCode statusCode) {
            // No-op.
        }

        @Override
        public void setStatus(final StatusCode statusCode, final String description) {
            // No-op.
        }

        @Override
        public void updateName(final String name) {
            // No-op.
        }

        @Override
        public void end() {
            // No-op.
        }

        @Override
        public void end(final Instant endTimestamp) {
            // No-op.
        }

        @Override
        public void recordException(final Throwable exception) {
            // No-op.
        }

        @Override
        public void recordException(final Throwable exception, final List<KeyValue> additionalAttributes) {
            // No-op.
        }
    }

    private static final class StubSpanContext implements SpanContext {
        @Override
        public String getTraceId() {
            return "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        }

        @Override
        public byte[] getTraceIdBytes() {
            return new byte[16];
        }

        @Override
        public String getSpanId() {
            return "bbbbbbbbbbbbbbbb";
        }

        @Override
        public byte[] getSpanIdBytes() {
            return new byte[8];
        }

        @Override
        public byte getTraceFlags() {
            return (byte) 0x01;
        }

        @Override
        public boolean isSampled() {
            return true;
        }

        @Override
        public boolean isRandom() {
            return true;
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public boolean isRemote() {
            return false;
        }

        @Override
        public TraceState getTraceState() {
            return new TraceState() {
                @Override
                public AnyValue get(final String key) {
                    return null;
                }

                @Override
                public TraceState set(final String key, final AnyValue value) {
                    return this;
                }

                @Override
                public TraceState delete(final String key) {
                    return this;
                }

                @Override
                public List<KeyValue> getValues() {
                    return Collections.emptyList();
                }
            };
        }
    }
}
