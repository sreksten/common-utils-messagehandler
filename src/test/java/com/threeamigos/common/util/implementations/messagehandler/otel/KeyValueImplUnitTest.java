package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("KeyValueImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class KeyValueImplUnitTest {

    @Test
    @DisplayName("constructor should reject null key")
    void constructorShouldRejectNullKey() {
        assertThrows(NullPointerException.class, () -> new KeyValueImpl(null, AnyValueImpl.ofString("v")));
    }

    @Test
    @DisplayName("constructor should reject empty key")
    void constructorShouldRejectEmptyKey() {
        assertThrows(IllegalArgumentException.class, () -> new KeyValueImpl("", AnyValueImpl.ofString("v")));
    }

    @Test
    @DisplayName("constructor should reject null value")
    void constructorShouldRejectNullValue() {
        assertThrows(NullPointerException.class, () -> new KeyValueImpl("k", null));
    }

    @Test
    @DisplayName("constructor should accept valid key/value")
    void constructorShouldAcceptValidKeyValue() {
        AnyValue value = AnyValueImpl.ofLong(42);
        KeyValue kv = new KeyValueImpl("k", value);
        assertEquals("k", kv.getKey());
        assertSame(value, kv.getValue());
    }

    @Test
    @DisplayName("constructor should accept heterogeneous AnyValue arrays")
    void constructorShouldAcceptHeterogeneousAnyValueArrays() {
        AnyValue array = AnyValueImpl.ofArray(Arrays.asList(
                AnyValueImpl.ofString("a"),
                AnyValueImpl.ofLong(2),
                AnyValueImpl.ofBoolean(true),
                AnyValueImpl.empty()
        ));
        assertDoesNotThrow(() -> new KeyValueImpl("array", array));
    }
}
