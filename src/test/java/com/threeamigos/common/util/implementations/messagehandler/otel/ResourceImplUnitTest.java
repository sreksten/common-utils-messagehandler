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

@DisplayName("ResourceImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class ResourceImplUnitTest {

    @Test
    @DisplayName("defaults should be null schemaUrl, empty attributes and zero dropped count")
    void defaultsShouldBeExpectedValues() {
        ResourceImpl resource = new ResourceImpl();
        assertNull(resource.getSchemaUrl());
        assertTrue(resource.getAttributes().isEmpty());
        assertEquals(0, resource.getDroppedAttributesCount());
    }

    @Test
    @DisplayName("setSchemaUrl() should store value")
    void setSchemaUrlShouldStoreValue() {
        ResourceImpl resource = new ResourceImpl();
        resource.setSchemaUrl("https://opentelemetry.io/schemas/1.26.0");
        assertEquals("https://opentelemetry.io/schemas/1.26.0", resource.getSchemaUrl());
    }

    @Test
    @DisplayName("setAttributes() should copy and expose unmodifiable attributes")
    void setAttributesShouldCopyAndExposeUnmodifiableAttributes() {
        ResourceImpl resource = new ResourceImpl();
        List<KeyValue> attrs = new ArrayList<>(Collections.singletonList(
                new KeyValueImpl("service.name", AnyValueImpl.ofString("svc"))
        ));

        resource.setAttributes(attrs);
        assertEquals(1, resource.getAttributes().size());
        assertThrows(UnsupportedOperationException.class,
                () -> resource.getAttributes().add(new KeyValueImpl("x", AnyValueImpl.ofString("y"))));

        attrs.add(new KeyValueImpl("service.version", AnyValueImpl.ofString("1.0")));
        assertEquals(1, resource.getAttributes().size());
    }

    @Test
    @DisplayName("setAttributes() should accept null as empty list")
    void setAttributesShouldAcceptNullAsEmptyList() {
        ResourceImpl resource = new ResourceImpl();
        resource.setAttributes(null);
        assertTrue(resource.getAttributes().isEmpty());
    }

    @Test
    @DisplayName("setAttributes() should reject duplicate keys")
    void setAttributesShouldRejectDuplicateKeys() {
        ResourceImpl resource = new ResourceImpl();
        List<KeyValue> attrs = Arrays.asList(
                new KeyValueImpl("k", AnyValueImpl.ofString("v1")),
                new KeyValueImpl("k", AnyValueImpl.ofString("v2"))
        );
        assertThrows(IllegalArgumentException.class, () -> resource.setAttributes(attrs));
    }

    @Test
    @DisplayName("setAttributes() should reject invalid key/value entries")
    void setAttributesShouldRejectInvalidEntries() {
        ResourceImpl resource = new ResourceImpl();
        KeyValue nullKey = new KeyValue() {
            @Override
            public String getKey() {
                return null;
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

        assertThrows(NullPointerException.class, () -> resource.setAttributes(Collections.singletonList(null)));
        assertThrows(NullPointerException.class, () -> resource.setAttributes(Collections.singletonList(nullKey)));
        assertThrows(NullPointerException.class, () -> resource.setAttributes(Collections.singletonList(nullValue)));
    }

    @Test
    @DisplayName("setDroppedAttributesCount() should reject negatives and accept non-negative values")
    void setDroppedAttributesCountShouldValidateInput() {
        ResourceImpl resource = new ResourceImpl();
        assertThrows(IllegalArgumentException.class, () -> resource.setDroppedAttributesCount(-1));

        resource.setDroppedAttributesCount(0);
        assertEquals(0, resource.getDroppedAttributesCount());
        resource.setDroppedAttributesCount(4);
        assertEquals(4, resource.getDroppedAttributesCount());
    }
}
