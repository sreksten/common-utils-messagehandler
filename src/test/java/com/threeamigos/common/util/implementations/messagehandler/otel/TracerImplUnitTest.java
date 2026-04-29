package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.Event;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Link;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    @DisplayName("createSpan should reject null names and produce no-op span")
    void createSpanShouldRejectNullNamesAndProduceNoOpSpan() {
        TracerImpl sut = new TracerImpl("orders", null, null, null);

        assertThrows(NullPointerException.class, () -> sut.createSpan(null));

        Span span = sut.createSpan("span-name");
        assertNotNull(span);
        assertDoesNotThrow(() -> {
            span.addAttribute("k", AnyValueFactory.ofString("v"));
            span.addEvent(new Event() {
            });
            span.addLink(new Link() {
            });
            span.end();
        });
    }
}
