package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AnyValueFactory unit tests")
@Tag("unit")
@Tag("messageHandler")
class AnyValueFactoryUnitTest extends AbstractOtelValidatorLogTrapUnitTest {

    private static final boolean ORIGINAL_LENIENT = OpenTelemetryAttributeValidator.isLenientMode();

    @BeforeEach
    void enforceLenientModeForDefaultValueAssertions() {
        setLenient(true);
    }

    @AfterEach
    void restoreLenientMode() {
        setLenient(ORIGINAL_LENIENT);
    }

    @Test
    @DisplayName("empty() should return EMPTY type singleton")
    void emptyShouldReturnEmptyTypeSingleton() {
        AnyValue first = AnyValueFactory.empty();
        AnyValue second = AnyValueFactory.empty();
        assertSame(first, second);
        assertEquals(AnyValue.Type.EMPTY, first.getType());
    }

    @Test
    @DisplayName("all typed accessors should return defaults when AnyValue is EMPTY")
    void typedAccessorsShouldReturnDefaultsWhenEmpty() {
        AnyValue empty = AnyValueFactory.empty();
        assertEquals("", empty.asString());
        assertFalse(empty.asBoolean());
        assertEquals(0L, empty.asLong());
        assertEquals(0.0d, empty.asDouble(), 0.0d);
        assertTrue(empty.asArray().isEmpty());
        assertTrue(empty.asKvList().isEmpty());
        assertArrayEquals(new byte[0], empty.asBytes());
    }

    @Test
    @DisplayName("typed accessors should return defaults on type mismatch")
    void typedAccessorsShouldReturnDefaultsOnTypeMismatch() {
        AnyValue stringValue = AnyValueFactory.ofString("abc");
        assertFalse(stringValue.asBoolean());
        assertEquals(0L, stringValue.asLong());
        assertEquals(0.0d, stringValue.asDouble(), 0.0d);
        assertTrue(stringValue.asArray().isEmpty());
        assertTrue(stringValue.asKvList().isEmpty());
        assertArrayEquals(new byte[0], stringValue.asBytes());
    }

    @Test
    @DisplayName("typed accessors should throw on type mismatch in strict mode")
    void typedAccessorsShouldThrowOnTypeMismatchInStrictMode() {
        setLenient(false);
        AnyValue stringValue = AnyValueFactory.ofString("abc");

        assertThrows(IllegalArgumentException.class, stringValue::asBoolean);
        assertThrows(IllegalArgumentException.class, stringValue::asLong);
        assertThrows(IllegalArgumentException.class, stringValue::asDouble);
        assertThrows(IllegalArgumentException.class, stringValue::asArray);
        assertThrows(IllegalArgumentException.class, stringValue::asKvList);
        assertThrows(IllegalArgumentException.class, stringValue::asBytes);
    }

    @Test
    @DisplayName("ofString() should store and return string values")
    void ofStringShouldStoreAndReturnStringValues() {
        AnyValue value = AnyValueFactory.ofString("abc");
        assertEquals(AnyValue.Type.STRING, value.getType());
        assertEquals("abc", value.asString());
    }

    @Test
    @DisplayName("ofString() should reject null")
    void ofStringShouldRejectNull() {
        AnyValue value = AnyValueFactory.ofString(null);
        assertSame(AnyValueFactory.empty(), value);
    }

    @Test
    @DisplayName("ofString() should throw on null in strict mode")
    void ofStringShouldThrowOnNullInStrictMode() {
        setLenient(false);
        assertThrows(IllegalArgumentException.class, () -> AnyValueFactory.ofString(null));
    }

    @Test
    @DisplayName("ofNullableString() should map null to EMPTY and non-null to STRING")
    void ofNullableStringShouldMapNullAndNonNullValues() {
        AnyValue nullValue = AnyValueFactory.ofNullableString(null);
        AnyValue textValue = AnyValueFactory.ofNullableString("abc");

        assertSame(AnyValueFactory.empty(), nullValue);
        assertEquals(AnyValue.Type.STRING, textValue.getType());
        assertEquals("abc", textValue.asString());
    }

    @Test
    @DisplayName("ofBoolean() should store and return boolean values")
    void ofBooleanShouldStoreAndReturnBooleanValues() {
        AnyValue value = AnyValueFactory.ofBoolean(true);
        assertEquals(AnyValue.Type.BOOL, value.getType());
        assertTrue(value.asBoolean());
    }

    @Test
    @DisplayName("ofLong() should store and return long values")
    void ofLongShouldStoreAndReturnLongValues() {
        AnyValue value = AnyValueFactory.ofLong(42L);
        assertEquals(AnyValue.Type.INT, value.getType());
        assertEquals(42L, value.asLong());
    }

    @Test
    @DisplayName("ofDouble() should store and return double values")
    void ofDoubleShouldStoreAndReturnDoubleValues() {
        AnyValue value = AnyValueFactory.ofDouble(3.14);
        assertEquals(AnyValue.Type.DOUBLE, value.getType());
        assertEquals(3.14, value.asDouble(), 0.0);
    }

    @Test
    @DisplayName("ofArray() should defensively copy and expose unmodifiable list")
    void ofArrayShouldDefensivelyCopyAndExposeUnmodifiableList() {
        java.util.List<AnyValue> source = new java.util.ArrayList<>(
                Collections.singletonList(AnyValueFactory.ofString("x")));
        AnyValue value = AnyValueFactory.ofArray(source);

        assertEquals(AnyValue.Type.ARRAY, value.getType());
        assertEquals(1, value.asArray().size());
        assertEquals("x", value.asArray().get(0).asString());
        assertThrows(UnsupportedOperationException.class, () -> value.asArray().add(AnyValueFactory.ofString("y")));

        source.add(AnyValueFactory.ofString("z"));
        assertEquals(1, value.asArray().size());
    }

    @Test
    @DisplayName("ofArray() should reject null list and preserve null elements as EMPTY")
    void ofArrayShouldRejectNullListAndPreserveNullElementsAsEmpty() {
        assertSame(AnyValueFactory.empty(), AnyValueFactory.ofArray(null));
        AnyValue value = AnyValueFactory.ofArray(Arrays.asList(AnyValueFactory.ofString("x"), null));
        assertEquals(AnyValue.Type.ARRAY, value.getType());
        assertEquals(2, value.asArray().size());
        assertEquals("x", value.asArray().get(0).asString());
        assertEquals(AnyValue.Type.EMPTY, value.asArray().get(1).getType());
    }

    @Test
    @DisplayName("ofArray() should throw on null list in strict mode")
    void ofArrayShouldThrowOnNullListInStrictMode() {
        setLenient(false);
        assertThrows(IllegalArgumentException.class, () -> AnyValueFactory.ofArray(null));
    }

    @Test
    @DisplayName("ofKvList() should defensively copy, validate and expose unmodifiable list")
    void ofKvListShouldDefensivelyCopyValidateAndExposeUnmodifiableList() {
        java.util.List<KeyValue> source = new java.util.ArrayList<>(Collections.singletonList(
                new KeyValueImpl("k1", AnyValueFactory.ofString("v1"))
        ));
        AnyValue value = AnyValueFactory.ofKvList(source);

        assertEquals(AnyValue.Type.KVLIST, value.getType());
        assertEquals(1, value.asKvList().size());
        assertEquals("k1", value.asKvList().get(0).getKey());
        assertThrows(UnsupportedOperationException.class, () -> value.asKvList().add(new KeyValueImpl("k2", AnyValueFactory.ofString("v2"))));

        source.add(new KeyValueImpl("k2", AnyValueFactory.ofString("v2")));
        assertEquals(1, value.asKvList().size());
    }

    @Test
    @DisplayName("ofKvList() should skip invalid entries and duplicate keys")
    void ofKvListShouldSkipInvalidEntriesAndDuplicateKeys() {
        assertSame(AnyValueFactory.empty(), AnyValueFactory.ofKvList(null));

        KeyValue nullKey = new KeyValue() {
            @Override
            public String getKey() {
                return null;
            }

            @Override
            public AnyValue getValue() {
                return AnyValueFactory.ofString("ignored");
            }
        };
        AnyValue value = AnyValueFactory.ofKvList(Arrays.asList(
                new KeyValueImpl("k", AnyValueFactory.ofString("v")),
                null,
                nullKey,
                new KeyValueImpl("dup", AnyValueFactory.ofString("v1")),
                new KeyValueImpl("dup", AnyValueFactory.ofString("v2"))
        ));

        assertEquals(AnyValue.Type.KVLIST, value.getType());
        assertEquals(2, value.asKvList().size());
        assertEquals("k", value.asKvList().get(0).getKey());
        assertEquals("dup", value.asKvList().get(1).getKey());
        assertEquals("v1", value.asKvList().get(1).getValue().asString());
    }

    @Test
    @DisplayName("ofKvList() should throw on null list in strict mode")
    void ofKvListShouldThrowOnNullListInStrictMode() {
        setLenient(false);
        assertThrows(IllegalArgumentException.class, () -> AnyValueFactory.ofKvList(null));
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
                return AnyValueFactory.ofString("v");
            }
        };

        AnyValue value = AnyValueFactory.ofKvList(Arrays.asList(withNullValue, withEmptyKey));
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
        AnyValue value = AnyValueFactory.ofBytes(source);
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
        assertSame(AnyValueFactory.empty(), AnyValueFactory.ofBytes(null));
    }

    @Test
    @DisplayName("ofBytes() should throw on null input in strict mode")
    void ofBytesShouldThrowOnNullInStrictMode() {
        setLenient(false);
        assertThrows(IllegalArgumentException.class, () -> AnyValueFactory.ofBytes(null));
    }

    private static void setLenient(final boolean value) {
        OpenTelemetryAttributeValidator.setLenientModeForTests(value);
    }
}
