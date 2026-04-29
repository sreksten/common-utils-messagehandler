package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("EventImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class EventImplUnitTest {

    private static final boolean ORIGINAL_LENIENT = OpenTelemetryAttributeValidator.isLenientMode();

    @BeforeEach
    void setUp() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(false);
    }

    @AfterEach
    void tearDown() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(ORIGINAL_LENIENT);
    }

    @Test
    @DisplayName("constructor should expose immutable event values")
    void constructorShouldExposeImmutableEventValues() {
        Instant timestamp = Instant.parse("2026-01-20T10:15:30Z");
        List<KeyValue> sourceAttributes = new ArrayList<>(Collections.singletonList(
                new KeyValueImpl("k1", AnyValueFactory.ofString("v1"))));

        EventImpl event = new EventImpl("order-created", timestamp, sourceAttributes);

        assertEquals("order-created", event.getName());
        assertEquals(timestamp, event.getTimestamp());
        assertEquals(1, event.getAttributes().size());
        assertEquals("k1", event.getAttributes().get(0).getKey());
        assertNotSame(sourceAttributes, event.getAttributes());

        sourceAttributes.add(new KeyValueImpl("k2", AnyValueFactory.ofString("v2")));
        assertEquals(1, event.getAttributes().size());
        assertThrows(UnsupportedOperationException.class,
                () -> event.getAttributes().add(new KeyValueImpl("k3", AnyValueFactory.ofString("v3"))));
    }

    @Test
    @DisplayName("null timestamp should default to current time")
    void nullTimestampShouldDefaultToCurrentTime() {
        Instant before = Instant.now();
        EventImpl event = new EventImpl("event", null, Collections.<KeyValue>emptyList());
        Instant after = Instant.now();

        assertFalse(event.getTimestamp().isBefore(before));
        assertFalse(event.getTimestamp().isAfter(after));
    }

    @Test
    @DisplayName("strict mode should throw on invalid event name")
    void strictModeShouldThrowOnInvalidEventName() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(false);
        assertThrows(IllegalArgumentException.class,
                () -> new EventImpl(null, Instant.now(), Collections.<KeyValue>emptyList()));
        assertThrows(IllegalArgumentException.class,
                () -> new EventImpl("   ", Instant.now(), Collections.<KeyValue>emptyList()));
    }

    @Test
    @DisplayName("lenient mode should normalize invalid event name")
    void lenientModeShouldNormalizeInvalidEventName() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        EventImpl event = new EventImpl("   ", Instant.now(), Collections.<KeyValue>emptyList());
        assertEquals("unknown", event.getName());
    }

    @Test
    @DisplayName("constructor should sanitize invalid attributes")
    void constructorShouldSanitizeInvalidAttributes() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        KeyValue nullValueAttribute = new KeyValue() {
            @Override
            public String getKey() {
                return "null.value";
            }

            @Override
            public AnyValue getValue() {
                return null;
            }
        };
        List<KeyValue> sourceAttributes = Arrays.asList(
                new KeyValueImpl("ok", AnyValueFactory.ofString("v")),
                null,
                new KeyValueImpl("dup", AnyValueFactory.ofString("a")),
                new KeyValueImpl("dup", AnyValueFactory.ofString("b")),
                nullValueAttribute);

        EventImpl event = new EventImpl("event", Instant.now(), sourceAttributes);
        List<KeyValue> attributes = event.getAttributes();

        assertEquals(3, attributes.size());
        assertEquals("ok", attributes.get(0).getKey());
        assertEquals("dup", attributes.get(1).getKey());
        assertEquals("a", attributes.get(1).getValue().asString());
        assertEquals("null.value", attributes.get(2).getKey());
        assertEquals(AnyValue.Type.EMPTY, attributes.get(2).getValue().getType());
    }
}
