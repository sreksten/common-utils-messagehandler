package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LinkImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class LinkImplUnitTest {

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
    @DisplayName("constructor should keep context and expose immutable copied attributes")
    void constructorShouldKeepContextAndExposeImmutableCopiedAttributes() {
        SpanContext spanContext = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x00,
                true,
                new TraceStateImpl());
        List<KeyValue> sourceAttributes = new ArrayList<>(Collections.singletonList(
                new KeyValueImpl("k1", AnyValueFactory.ofString("v1"))));

        LinkImpl link = new LinkImpl(spanContext, sourceAttributes);

        assertSame(spanContext, link.getSpanContext());
        assertNotSame(sourceAttributes, link.getAttributes());
        assertEquals(1, link.getAttributes().size());
        assertEquals("k1", link.getAttributes().get(0).getKey());

        sourceAttributes.add(new KeyValueImpl("k2", AnyValueFactory.ofString("v2")));
        assertEquals(1, link.getAttributes().size());
        assertThrows(UnsupportedOperationException.class,
                () -> link.getAttributes().add(new KeyValueImpl("k3", AnyValueFactory.ofString("v3"))));
    }

    @Test
    @DisplayName("strict mode should throw when spanContext is null")
    void strictModeShouldThrowWhenSpanContextIsNull() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(false);
        assertThrows(IllegalArgumentException.class,
                () -> new LinkImpl(null, Collections.<KeyValue>emptyList()));
    }

    @Test
    @DisplayName("lenient mode should default null spanContext to invalid context and continue")
    void lenientModeShouldDefaultNullSpanContextToInvalidContextAndContinue() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);

        LinkImpl link = new LinkImpl(null, Collections.<KeyValue>emptyList());

        assertNotNull(link.getSpanContext());
        assertFalse(link.getSpanContext().isValid());
        assertTrue(link.getAttributes().isEmpty());
    }

    @Test
    @DisplayName("null attributes should produce empty immutable list")
    void nullAttributesShouldProduceEmptyImmutableList() {
        SpanContext spanContext = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x00,
                false,
                new TraceStateImpl());

        LinkImpl link = new LinkImpl(spanContext, null);

        assertTrue(link.getAttributes().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> link.getAttributes().add(new KeyValueImpl("k", AnyValueFactory.ofString("v"))));
    }

    @Test
    @DisplayName("invalid span context with attributes should be preserved")
    void invalidSpanContextWithAttributesShouldBePreserved() {
        SpanContext invalidContext = new SpanContextImpl(
                "00000000000000000000000000000000",
                "0000000000000000",
                (byte) 0x00,
                false,
                new TraceStateImpl());
        LinkImpl link = new LinkImpl(invalidContext, Collections.singletonList(
                new KeyValueImpl("hint", AnyValueFactory.ofString("kept"))));

        assertSame(invalidContext, link.getSpanContext());
        assertFalse(link.getSpanContext().isValid());
        assertEquals(1, link.getAttributes().size());
        assertEquals("hint", link.getAttributes().get(0).getKey());
    }

    @Test
    @DisplayName("attributes should be sanitized in lenient mode")
    void attributesShouldBeSanitizedInLenientMode() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        SpanContext spanContext = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x00,
                false,
                new TraceStateImpl());
        KeyValue nullValue = new KeyValue() {
            @Override
            public String getKey() {
                return "null.value";
            }

            @Override
            public AnyValue getValue() {
                return null;
            }
        };

        LinkImpl link = new LinkImpl(spanContext, Arrays.asList(
                new KeyValueImpl("ok", AnyValueFactory.ofString("v")),
                null,
                new KeyValueImpl("dup", AnyValueFactory.ofString("a")),
                new KeyValueImpl("dup", AnyValueFactory.ofString("b")),
                nullValue));

        assertEquals(3, link.getAttributes().size());
        assertEquals("ok", link.getAttributes().get(0).getKey());
        assertEquals("dup", link.getAttributes().get(1).getKey());
        assertEquals("a", link.getAttributes().get(1).getValue().asString());
        assertEquals("null.value", link.getAttributes().get(2).getKey());
        assertEquals(AnyValue.Type.EMPTY, link.getAttributes().get(2).getValue().getType());
    }

}
