package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AnyValueImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class AnyValueImplUnitTest {

    @Test
    @DisplayName("empty() should return EMPTY type singleton")
    void emptyShouldReturnEmptyTypeSingleton() {
        AnyValue first = AnyValueImpl.empty();
        AnyValue second = AnyValueImpl.empty();
        assertSame(first, second);
        assertEquals(AnyValue.Type.EMPTY, first.getType());
    }

    @Test
    @DisplayName("all typed accessors should throw when AnyValue is EMPTY")
    void typedAccessorsShouldThrowWhenEmpty() {
        AnyValue empty = AnyValueImpl.empty();
        assertThrows(IllegalStateException.class, empty::asString);
        assertThrows(IllegalStateException.class, empty::asBoolean);
        assertThrows(IllegalStateException.class, empty::asLong);
        assertThrows(IllegalStateException.class, empty::asDouble);
        assertThrows(IllegalStateException.class, empty::asArray);
        assertThrows(IllegalStateException.class, empty::asKvList);
        assertThrows(IllegalStateException.class, empty::asBytes);
    }

    @Test
    @DisplayName("ofString() should store and return string values")
    void ofStringShouldStoreAndReturnStringValues() {
        AnyValue value = AnyValueImpl.ofString("abc");
        assertEquals(AnyValue.Type.STRING, value.getType());
        assertEquals("abc", value.asString());
    }

    @Test
    @DisplayName("ofString() should reject null")
    void ofStringShouldRejectNull() {
        assertThrows(NullPointerException.class, () -> AnyValueImpl.ofString(null));
    }

    @Test
    @DisplayName("ofNullableString() should map null to EMPTY and non-null to STRING")
    void ofNullableStringShouldMapNullAndNonNullValues() {
        AnyValue nullValue = AnyValueImpl.ofNullableString(null);
        AnyValue textValue = AnyValueImpl.ofNullableString("abc");

        assertSame(AnyValueImpl.empty(), nullValue);
        assertEquals(AnyValue.Type.STRING, textValue.getType());
        assertEquals("abc", textValue.asString());
    }

    @Test
    @DisplayName("ofBoolean() should store and return boolean values")
    void ofBooleanShouldStoreAndReturnBooleanValues() {
        AnyValue value = AnyValueImpl.ofBoolean(true);
        assertEquals(AnyValue.Type.BOOL, value.getType());
        assertTrue(value.asBoolean());
    }

    @Test
    @DisplayName("ofLong() should store and return long values")
    void ofLongShouldStoreAndReturnLongValues() {
        AnyValue value = AnyValueImpl.ofLong(42L);
        assertEquals(AnyValue.Type.INT, value.getType());
        assertEquals(42L, value.asLong());
    }

    @Test
    @DisplayName("ofDouble() should store and return double values")
    void ofDoubleShouldStoreAndReturnDoubleValues() {
        AnyValue value = AnyValueImpl.ofDouble(3.14);
        assertEquals(AnyValue.Type.DOUBLE, value.getType());
        assertEquals(3.14, value.asDouble(), 0.0);
    }

    @Test
    @DisplayName("ofArray() should defensively copy and expose unmodifiable list")
    void ofArrayShouldDefensivelyCopyAndExposeUnmodifiableList() {
        java.util.List<AnyValue> source = new java.util.ArrayList<>(
                Collections.singletonList(AnyValueImpl.ofString("x")));
        AnyValue value = AnyValueImpl.ofArray(source);

        assertEquals(AnyValue.Type.ARRAY, value.getType());
        assertEquals(1, value.asArray().size());
        assertEquals("x", value.asArray().get(0).asString());
        assertThrows(UnsupportedOperationException.class, () -> value.asArray().add(AnyValueImpl.ofString("y")));

        source.add(AnyValueImpl.ofString("z"));
        assertEquals(1, value.asArray().size());
    }

    @Test
    @DisplayName("ofArray() should reject null list and preserve null elements as EMPTY")
    void ofArrayShouldRejectNullListAndPreserveNullElementsAsEmpty() {
        assertThrows(NullPointerException.class, () -> AnyValueImpl.ofArray(null));
        AnyValue value = AnyValueImpl.ofArray(Arrays.asList(AnyValueImpl.ofString("x"), null));
        assertEquals(AnyValue.Type.ARRAY, value.getType());
        assertEquals(2, value.asArray().size());
        assertEquals("x", value.asArray().get(0).asString());
        assertEquals(AnyValue.Type.EMPTY, value.asArray().get(1).getType());
    }

    @Test
    @DisplayName("ofKvList() should defensively copy, validate and expose unmodifiable list")
    void ofKvListShouldDefensivelyCopyValidateAndExposeUnmodifiableList() {
        java.util.List<KeyValue> source = new java.util.ArrayList<>(Collections.singletonList(
                new KeyValueImpl("k1", AnyValueImpl.ofString("v1"))
        ));
        AnyValue value = AnyValueImpl.ofKvList(source);

        assertEquals(AnyValue.Type.KVLIST, value.getType());
        assertEquals(1, value.asKvList().size());
        assertEquals("k1", value.asKvList().get(0).getKey());
        assertThrows(UnsupportedOperationException.class, () -> value.asKvList().add(new KeyValueImpl("k2", AnyValueImpl.ofString("v2"))));

        source.add(new KeyValueImpl("k2", AnyValueImpl.ofString("v2")));
        assertEquals(1, value.asKvList().size());
    }

    @Test
    @DisplayName("ofKvList() should reject null input, duplicates and invalid elements")
    void ofKvListShouldRejectInvalidInputs() {
        assertThrows(NullPointerException.class, () -> AnyValueImpl.ofKvList(null));
        assertThrows(NullPointerException.class, () -> AnyValueImpl.ofKvList(Arrays.asList(new KeyValueImpl("k", AnyValueImpl.ofString("v")), null)));
        assertThrows(IllegalArgumentException.class, () -> AnyValueImpl.ofKvList(Arrays.asList(
                new KeyValueImpl("dup", AnyValueImpl.ofString("v1")),
                new KeyValueImpl("dup", AnyValueImpl.ofString("v2"))
        )));
    }

    @Test
    @DisplayName("ofKvList() should preserve null values as EMPTY and allow empty keys")
    void ofKvListShouldPreserveNullValuesAndAllowEmptyKeys() {
        KeyValue withNullValue = new KeyValue() {
            @Override
            public String getKey() {
                return "nullable";
            }

            @Override
            public AnyValue getValue() {
                return null;
            }
        };
        KeyValue withEmptyKey = new KeyValue() {
            @Override
            public String getKey() {
                return "";
            }

            @Override
            public AnyValue getValue() {
                return AnyValueImpl.ofString("v");
            }
        };

        AnyValue value = AnyValueImpl.ofKvList(Arrays.asList(withNullValue, withEmptyKey));
        assertEquals(AnyValue.Type.KVLIST, value.getType());
        assertEquals(2, value.asKvList().size());
        assertEquals("nullable", value.asKvList().get(0).getKey());
        assertEquals(AnyValue.Type.EMPTY, value.asKvList().get(0).getValue().getType());
        assertEquals("", value.asKvList().get(1).getKey());
        assertEquals("v", value.asKvList().get(1).getValue().asString());
    }

    @Test
    @DisplayName("ofBytes() should defensively copy input and output")
    void ofBytesShouldDefensivelyCopyInputAndOutput() {
        byte[] source = new byte[] {1, 2, 3};
        AnyValue value = AnyValueImpl.ofBytes(source);
        assertEquals(AnyValue.Type.BYTES, value.getType());

        source[0] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, value.asBytes());

        byte[] firstRead = value.asBytes();
        byte[] secondRead = value.asBytes();
        assertNotSame(firstRead, secondRead);
        firstRead[1] = 7;
        assertArrayEquals(new byte[] {1, 2, 3}, secondRead);
    }

    @Test
    @DisplayName("ofBytes() should reject null input")
    void ofBytesShouldRejectNull() {
        assertThrows(NullPointerException.class, () -> AnyValueImpl.ofBytes(null));
    }
}
