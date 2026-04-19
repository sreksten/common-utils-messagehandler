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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OtelAttributeValidator unit tests")
@Tag("unit")
@Tag("messageHandler")
class OpenTelemetryAttributeValidatorUnitTest {

    @Test
    @DisplayName("copyAndValidateKeyValues() should return empty list for null input")
    void copyAndValidateKeyValuesShouldReturnEmptyListForNullInput() {
        List<KeyValue> copy = OpenTelemetryAttributeValidator.copyAndValidateKeyValues(null, "attrs");
        assertTrue(copy.isEmpty());
    }

    @Test
    @DisplayName("copyAndValidateKeyValues() should return an independent copy")
    void copyAndValidateKeyValuesShouldReturnIndependentCopy() {
        List<KeyValue> source = new ArrayList<>(Collections.singletonList(
                new KeyValueImpl("k1", AnyValueImpl.ofString("v1"))
        ));

        List<KeyValue> copy = OpenTelemetryAttributeValidator.copyAndValidateKeyValues(source, "attrs");
        assertNotSame(source, copy);
        assertEquals(1, copy.size());

        source.add(new KeyValueImpl("k2", AnyValueImpl.ofString("v2")));
        assertEquals(1, copy.size());
    }

    @Test
    @DisplayName("copyAndValidateKeyValues() should reject null elements")
    void copyAndValidateKeyValuesShouldRejectNullElements() {
        List<KeyValue> source = Arrays.asList(new KeyValueImpl("k1", AnyValueImpl.ofString("v1")), null);
        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> OpenTelemetryAttributeValidator.copyAndValidateKeyValues(source, "attrs"));
        assertTrue(ex.getMessage().contains("contains null element"));
    }

    @Test
    @DisplayName("copyAndValidateKeyValues() should reject null keys")
    void copyAndValidateKeyValuesShouldRejectNullKeys() {
        KeyValue invalid = new KeyValue() {
            @Override
            public String getKey() {
                return null;
            }

            @Override
            public AnyValue getValue() {
                return AnyValueImpl.ofString("v");
            }
        };

        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> OpenTelemetryAttributeValidator.copyAndValidateKeyValues(Collections.singletonList(invalid), "attrs"));
        assertTrue(ex.getMessage().contains("key must not be null"));
    }

    @Test
    @DisplayName("copyAndValidateKeyValues() should reject empty keys")
    void copyAndValidateKeyValuesShouldRejectEmptyKeys() {
        KeyValue invalid = new KeyValue() {
            @Override
            public String getKey() {
                return "";
            }

            @Override
            public AnyValue getValue() {
                return AnyValueImpl.ofString("v");
            }
        };

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> OpenTelemetryAttributeValidator.copyAndValidateKeyValues(Collections.singletonList(invalid), "attrs"));
        assertTrue(ex.getMessage().contains("key must not be empty"));
    }

    @Test
    @DisplayName("copyAndValidateKeyValues() should reject null values")
    void copyAndValidateKeyValuesShouldRejectNullValues() {
        KeyValue invalid = new KeyValue() {
            @Override
            public String getKey() {
                return "k";
            }

            @Override
            public AnyValue getValue() {
                return null;
            }
        };

        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> OpenTelemetryAttributeValidator.copyAndValidateKeyValues(Collections.singletonList(invalid), "attrs"));
        assertTrue(ex.getMessage().contains("value must not be null"));
    }

    @Test
    @DisplayName("copyAndValidateKeyValues() should reject duplicate keys")
    void copyAndValidateKeyValuesShouldRejectDuplicateKeys() {
        List<KeyValue> source = Arrays.asList(
                new KeyValueImpl("dup", AnyValueImpl.ofString("v1")),
                new KeyValueImpl("dup", AnyValueImpl.ofString("v2"))
        );
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> OpenTelemetryAttributeValidator.copyAndValidateKeyValues(source, "attrs"));
        assertTrue(ex.getMessage().contains("duplicate key"));
    }

    @Test
    @DisplayName("copyAndValidateKeyValues() should accept valid unique key-values")
    void copyAndValidateKeyValuesShouldAcceptValidUniqueValues() {
        assertDoesNotThrow(() -> OpenTelemetryAttributeValidator.copyAndValidateKeyValues(Arrays.asList(
                new KeyValueImpl("a", AnyValueImpl.ofString("1")),
                new KeyValueImpl("b", AnyValueImpl.ofLong(2))
        ), "attrs"));
    }
}
