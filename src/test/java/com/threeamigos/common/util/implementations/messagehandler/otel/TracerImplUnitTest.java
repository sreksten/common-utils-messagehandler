package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.StatusCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TracerImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class TracerImplUnitTest {

    @Test
    @DisplayName("constructor should preserve provided instrumentation name")
    void constructorShouldPreserveProvidedInstrumentationName() throws Exception {
        TracerImpl sut = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList());

        Field field = TracerImpl.class.getDeclaredField("instrumentationName");
        field.setAccessible(true);
        assertEquals("orders", field.get(sut));
    }

    @Test
    @DisplayName("constructor should normalize null instrumentation name to empty")
    void constructorShouldNormalizeNullInstrumentationNameToEmpty() throws Exception {
        TracerImpl sut = new TracerImpl(null, null, null, null);

        Field field = TracerImpl.class.getDeclaredField("instrumentationName");
        field.setAccessible(true);
        assertEquals("", field.get(sut));
    }

    @Test
    @DisplayName("createSpan should reject null names and produce a recording span")
    void createSpanShouldRejectNullNamesAndProduceRecordingSpan() {
        TracerImpl sut = new TracerImpl("orders", null, null, null);

        assertThrows(IllegalArgumentException.class, () -> sut.createSpan(null));

        Span span = sut.createSpan("span-name");
        assertNotNull(span);
        assertNotNull(span.getContext());
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
                    Collections.emptyList()));
            span.setStatus(StatusCode.ERROR, "boom");
            span.recordException(new IllegalStateException("boom"));
            span.updateName("updated-name");
            span.end();
        });
        assertFalse(span.isRecording());
    }
}
