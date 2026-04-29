package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.StatusCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TracerImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class TracerImplUnitTest {

    @Test
    @DisplayName("constructor should preserve provided instrumentation name in scope")
    void constructorShouldPreserveProvidedInstrumentationName() {
        TracerImpl sut = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList());
        assertNotNull(sut.getInstrumentationScope());
        assertEquals("orders", sut.getInstrumentationScope().getName());
    }

    @Test
    @DisplayName("constructor should normalize null instrumentation name to absent scope name")
    void constructorShouldNormalizeNullInstrumentationNameToEmpty() {
        TracerImpl sut = new TracerImpl(null, null, null, null);
        assertNotNull(sut.getInstrumentationScope());
        assertNull(sut.getInstrumentationScope().getName());
    }

    @Test
    @DisplayName("constructor should normalize blank instrumentation name to absent scope name")
    void constructorShouldNormalizeBlankInstrumentationNameToEmpty() {
        TracerImpl sut = new TracerImpl("   ", "1.0.0", "schema", Collections.emptyList());
        assertNotNull(sut.getInstrumentationScope());
        assertNull(sut.getInstrumentationScope().getName());
    }

    @Test
    @DisplayName("constructor should copy attributes defensively")
    @SuppressWarnings({"rawtypes"})
    void constructorShouldCopyAttributesDefensively() {
        ArrayList<com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue> sourceAttributes = new ArrayList<>();
        sourceAttributes.add(new KeyValueImpl("k1", AnyValueFactory.ofString("v1")));

        TracerImpl sut = new TracerImpl("orders", "1.0.0", "schema", sourceAttributes);
        assertNotNull(sut.getInstrumentationScope());
        assertNotSame(sourceAttributes, sut.getInstrumentationScope().getAttributes());
        assertEquals(1, sut.getInstrumentationScope().getAttributes().size());

        sourceAttributes.add(new KeyValueImpl("k2", AnyValueFactory.ofString("v2")));
        assertEquals(1, sut.getInstrumentationScope().getAttributes().size());

        assertThrows(UnsupportedOperationException.class,
                () -> ((java.util.List) sut.getInstrumentationScope().getAttributes())
                        .add(new KeyValueImpl("k3", AnyValueFactory.ofString("v3"))));
    }

    @Test
    @DisplayName("isEnabled should reflect tracer mode")
    void isEnabledShouldReflectTracerMode() {
        TracerImpl enabled = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList());
        TracerImpl disabled = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList(), false);

        assertTrue(enabled.isEnabled());
        assertFalse(disabled.isEnabled());
    }

    @Test
    @DisplayName("createSpan should reject null names and produce a recording span")
    void createSpanShouldRejectNullNamesAndProduceRecordingSpan() {
        TracerImpl sut = new TracerImpl("orders", null, null, null);

        assertThrows(IllegalArgumentException.class, () -> sut.createSpan(null));

        Span span = sut.createSpan("span-name");
        assertNotNull(span);
        assertNotNull(span.getContext());
        assertNotNull(span.getInstrumentationScope());
        assertEquals("orders", span.getInstrumentationScope().getName());
        assertTrue(span.isRecording());
        assertDoesNotThrow(() -> {
            span.addAttribute("k", AnyValueFactory.ofString("v"));
            span.addEvent("event-name");
            span.addEvent("event-name-2", Arrays.asList(
                    new KeyValueImpl("attr", AnyValueFactory.ofString("v2"))), Instant.now());
            span.addLink(new SpanContextImpl(
                    "5b8efff798038103d269b633813fc60c",
                    "eee19b7ec3c1b174",
                    (byte) 0x00,
                    false,
                    new TraceStateImpl()));
            span.setStatus(StatusCode.ERROR, "boom");
            span.recordException(new IllegalStateException("boom"));
            span.updateName("updated-name");
            span.end();
        });
        assertFalse(span.isRecording());
    }

    @Test
    @DisplayName("tracer should expose effective instrumentation scope")
    void tracerShouldExposeEffectiveInstrumentationScope() {
        TracerImpl tracer = new TracerImpl("orders", "1.0.0", "https://schema", Collections.emptyList());
        assertNotNull(tracer.getInstrumentationScope());
        assertEquals("orders", tracer.getInstrumentationScope().getName());
        assertEquals("1.0.0", tracer.getInstrumentationScope().getVersion());
        assertEquals("https://schema", tracer.getInstrumentationScope().getSchemaUrl());

        TracerImpl nullNameTracer = new TracerImpl(null, "1.0.0", null, null);
        assertNotNull(nullNameTracer.getInstrumentationScope());
        assertNull(nullNameTracer.getInstrumentationScope().getName());
        assertEquals("1.0.0", nullNameTracer.getInstrumentationScope().getVersion());
        assertNull(nullNameTracer.getInstrumentationScope().getSchemaUrl());
    }

    @Test
    @DisplayName("disabled tracer should return non-recording invalid span")
    void disabledTracerShouldReturnNonRecordingInvalidSpan() {
        TracerImpl disabled = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList(), false);

        assertThrows(IllegalArgumentException.class, () -> disabled.createSpan(null));

        Span span = disabled.createSpan("disabled-span");
        assertNotNull(span);
        assertFalse(span.isRecording());
        assertNotNull(span.getSpanContext());
        assertFalse(span.getSpanContext().isValid());
    }

    @Test
    @DisplayName("createSpan with valid parent should keep traceId and trace-flags from parent")
    void createSpanWithValidParentShouldKeepTraceIdAndFlagsFromParent() {
        TracerImpl tracer = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList(), true);
        SpanContextImpl parent = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x03,
                true,
                new TraceStateImpl().set("vendor", AnyValueFactory.ofString("value")));

        Span child = tracer.createSpan("child-span", parent);

        assertNotNull(child);
        assertTrue(child.isRecording());
        assertEquals(parent.getTraceId(), child.getSpanContext().getTraceId());
        assertNotEquals(parent.getSpanId(), child.getSpanContext().getSpanId());
        assertEquals(parent.getTraceFlags(), child.getSpanContext().getTraceFlags());
        assertTrue(child.getSpanContext().isSampled());
        assertTrue(child.getSpanContext().isRandom());
        assertFalse(child.getSpanContext().isRemote());
        assertEquals(1, child.getSpanContext().getTraceState().getValues().size());
        assertEquals("vendor", child.getSpanContext().getTraceState().getValues().iterator().next().getKey());
    }

    @Test
    @DisplayName("createSpan with invalid parent should start a new root span")
    void createSpanWithInvalidParentShouldStartNewRootSpan() {
        TracerImpl tracer = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList(), true);
        SpanContextImpl invalidParent = new SpanContextImpl();

        Span child = tracer.createSpan("rooted-span", invalidParent);

        assertNotNull(child);
        assertTrue(child.isRecording());
        assertTrue(child.getSpanContext().isValid());
        assertNotEquals(invalidParent.getTraceId(), child.getSpanContext().getTraceId());
        assertEquals((byte) 0x00, child.getSpanContext().getTraceFlags());
        assertFalse(child.getSpanContext().isSampled());
        assertFalse(child.getSpanContext().isRandom());
    }
}
