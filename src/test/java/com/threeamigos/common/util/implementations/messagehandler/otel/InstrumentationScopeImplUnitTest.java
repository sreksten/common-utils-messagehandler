package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("InstrumentationScopeImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class InstrumentationScopeImplUnitTest {

    @Test
    @DisplayName("defaults should be null name/version/schemaUrl, empty attributes and zero dropped count")
    void defaultsShouldBeExpectedValues() {
        InstrumentationScopeImpl scope = new InstrumentationScopeImpl(null, null, null, null);
        assertNull(scope.getName());
        assertNull(scope.getVersion());
        assertNull(scope.getSchemaUrl());
        assertTrue(scope.getAttributes().isEmpty());
        assertEquals(0, scope.getDroppedAttributesCount());
    }

    @Test
    @DisplayName("constructor should store scalar fields")
    void constructorShouldStoreScalarFields() {
        InstrumentationScopeImpl scope = new InstrumentationScopeImpl(
                "com.example.lib",
                "1.2.3",
                "https://opentelemetry.io/schemas/1.26.0",
                null);

        assertEquals("com.example.lib", scope.getName());
        assertEquals("1.2.3", scope.getVersion());
        assertEquals("https://opentelemetry.io/schemas/1.26.0", scope.getSchemaUrl());
    }

    @Test
    @DisplayName("constructor should copy and expose unmodifiable attributes")
    void constructorShouldCopyAndExposeUnmodifiableAttributes() {
        List<KeyValue> attrs = new ArrayList<>(Collections.singletonList(
                new KeyValueImpl("scope.attr", AnyValueImpl.ofString("x"))
        ));

        InstrumentationScopeImpl scope = new InstrumentationScopeImpl(
                null, null, null, attrs);
        assertEquals(1, scope.getAttributes().size());
        assertThrows(UnsupportedOperationException.class,
                () -> scope.getAttributes().add(new KeyValueImpl("k", AnyValueImpl.ofString("v"))));

        attrs.add(new KeyValueImpl("other", AnyValueImpl.ofLong(2)));
        assertEquals(1, scope.getAttributes().size());
    }

    @Test
    @DisplayName("constructor should accept null attributes as empty list")
    void constructorShouldAcceptNullAsEmptyList() {
        InstrumentationScopeImpl scope = new InstrumentationScopeImpl(
                null, null, null, null);
        assertTrue(scope.getAttributes().isEmpty());
    }

    @Test
    @DisplayName("constructor should reject duplicate keys")
    void constructorShouldRejectDuplicateKeys() {
        List<KeyValue> attrs = Arrays.asList(
                new KeyValueImpl("k", AnyValueImpl.ofString("v1")),
                new KeyValueImpl("k", AnyValueImpl.ofString("v2"))
        );
        assertThrows(IllegalArgumentException.class, () ->
                new InstrumentationScopeImpl(null, null, null, attrs));
    }

    @Test
    @DisplayName("constructor should reject invalid key/value entries")
    void constructorShouldRejectInvalidEntries() {
        KeyValue emptyKey = new KeyValue() {
            @Override
            public String getKey() {
                return "";
            }

            @Override
            public AnyValue getValue() {
                return AnyValueImpl.ofString("v");
            }
        };
        KeyValue nullValue = new KeyValue() {
            @Override
            public String getKey() {
                return "k";
            }

            @Override
            public AnyValue getValue() {
                return null;
            }
        };

        assertThrows(IllegalArgumentException.class, () ->
                new InstrumentationScopeImpl(null, null, null, Collections.singletonList(emptyKey)));
        assertThrows(NullPointerException.class, () ->
                new InstrumentationScopeImpl(null, null, null, Collections.singletonList(nullValue)));
    }

    @Test
    @DisplayName("constructor should drop attributes above the default attribute count limit")
    void constructorShouldDropAttributesAboveDefaultLimit() {
        List<KeyValue> attributes = new ArrayList<>();
        for (int i = 0; i < 129; i++) {
            attributes.add(new KeyValueImpl("k" + i, AnyValueImpl.ofString("v" + i)));
        }

        InstrumentationScopeImpl scope = new InstrumentationScopeImpl(
                null, null, null, attributes);

        assertEquals(128, scope.getAttributes().size());
        assertEquals(1, scope.getDroppedAttributesCount());
        assertEquals("k127", scope.getAttributes().get(127).getKey());
    }
}
