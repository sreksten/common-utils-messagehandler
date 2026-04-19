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
        InstrumentationScopeImpl scope = new InstrumentationScopeImpl();
        assertNull(scope.getName());
        assertNull(scope.getVersion());
        assertNull(scope.getSchemaUrl());
        assertTrue(scope.getAttributes().isEmpty());
        assertEquals(0, scope.getDroppedAttributesCount());
    }

    @Test
    @DisplayName("setters should store scalar fields")
    void settersShouldStoreScalarFields() {
        InstrumentationScopeImpl scope = new InstrumentationScopeImpl();
        scope.setName("com.example.lib");
        scope.setVersion("1.2.3");
        scope.setSchemaUrl("https://opentelemetry.io/schemas/1.26.0");

        assertEquals("com.example.lib", scope.getName());
        assertEquals("1.2.3", scope.getVersion());
        assertEquals("https://opentelemetry.io/schemas/1.26.0", scope.getSchemaUrl());
    }

    @Test
    @DisplayName("setAttributes() should copy and expose unmodifiable attributes")
    void setAttributesShouldCopyAndExposeUnmodifiableAttributes() {
        InstrumentationScopeImpl scope = new InstrumentationScopeImpl();
        List<KeyValue> attrs = new ArrayList<>(Collections.singletonList(
                new KeyValueImpl("scope.attr", AnyValueImpl.ofString("x"))
        ));

        scope.setAttributes(attrs);
        assertEquals(1, scope.getAttributes().size());
        assertThrows(UnsupportedOperationException.class,
                () -> scope.getAttributes().add(new KeyValueImpl("k", AnyValueImpl.ofString("v"))));

        attrs.add(new KeyValueImpl("other", AnyValueImpl.ofLong(2)));
        assertEquals(1, scope.getAttributes().size());
    }

    @Test
    @DisplayName("setAttributes() should accept null as empty list")
    void setAttributesShouldAcceptNullAsEmptyList() {
        InstrumentationScopeImpl scope = new InstrumentationScopeImpl();
        scope.setAttributes(null);
        assertTrue(scope.getAttributes().isEmpty());
    }

    @Test
    @DisplayName("setAttributes() should reject duplicate keys")
    void setAttributesShouldRejectDuplicateKeys() {
        InstrumentationScopeImpl scope = new InstrumentationScopeImpl();
        List<KeyValue> attrs = Arrays.asList(
                new KeyValueImpl("k", AnyValueImpl.ofString("v1")),
                new KeyValueImpl("k", AnyValueImpl.ofString("v2"))
        );
        assertThrows(IllegalArgumentException.class, () -> scope.setAttributes(attrs));
    }

    @Test
    @DisplayName("setAttributes() should reject invalid key/value entries")
    void setAttributesShouldRejectInvalidEntries() {
        InstrumentationScopeImpl scope = new InstrumentationScopeImpl();
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

        assertThrows(IllegalArgumentException.class, () -> scope.setAttributes(Collections.singletonList(emptyKey)));
        assertThrows(NullPointerException.class, () -> scope.setAttributes(Collections.singletonList(nullValue)));
    }

    @Test
    @DisplayName("setDroppedAttributesCount() should reject negatives and accept non-negative values")
    void setDroppedAttributesCountShouldValidateInput() {
        InstrumentationScopeImpl scope = new InstrumentationScopeImpl();
        assertThrows(IllegalArgumentException.class, () -> scope.setDroppedAttributesCount(-1));

        scope.setDroppedAttributesCount(0);
        assertEquals(0, scope.getDroppedAttributesCount());
        scope.setDroppedAttributesCount(3);
        assertEquals(3, scope.getDroppedAttributesCount());
    }
}
