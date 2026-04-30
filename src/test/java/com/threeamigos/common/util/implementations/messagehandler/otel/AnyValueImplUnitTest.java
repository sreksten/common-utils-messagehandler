package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AnyValueImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class AnyValueImplUnitTest extends AbstractOtelValidatorLogTrapUnitTest {

    private static final boolean ORIGINAL_LENIENT = OpenTelemetryAttributeValidator.isLenientMode();

    @BeforeEach
    void enforceLenientModeForDefaultGetterAssertions() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
    }

    @AfterEach
    void restoreLenientMode() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(ORIGINAL_LENIENT);
    }

    @Test
    @DisplayName("KVLIST equality should be map-based and ignore insertion order")
    void kvListEqualityShouldIgnoreInsertionOrder() {
        AnyValue left = AnyValueFactory.ofKvList(Arrays.asList(
                new KeyValueImpl("service.name", AnyValueFactory.ofString("checkout")),
                new KeyValueImpl("service.version", AnyValueFactory.ofString("1.2.3"))
        ));
        AnyValue right = AnyValueFactory.ofKvList(Arrays.asList(
                new KeyValueImpl("service.version", AnyValueFactory.ofString("1.2.3")),
                new KeyValueImpl("service.name", AnyValueFactory.ofString("checkout"))
        ));

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
    }

    @Test
    @DisplayName("constructor should normalize null internals to safe defaults")
    void constructorShouldNormalizeNullInternals() {
        AnyValueImpl value = new AnyValueImpl(
                null,
                null,
                false,
                0L,
                0.0d,
                null,
                null,
                null
        );

        assertEquals(AnyValue.Type.EMPTY, value.getType());
        assertEquals("", value.asString());
        assertTrue(value.asArray().isEmpty());
        assertTrue(value.asKvList().isEmpty());
        assertArrayEquals(new byte[0], value.asBytes());
    }

    @Test
    @DisplayName("constructor should throw on mismatched getter in strict mode")
    void constructorShouldThrowOnMismatchedGetterInStrictMode() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(false);
        AnyValueImpl value = new AnyValueImpl(
                null,
                null,
                false,
                0L,
                0.0d,
                null,
                null,
                null
        );

        assertThrows(IllegalArgumentException.class, value::asString);
    }

    @Test
    @DisplayName("typed accessors should return stored values for matching types")
    void typedAccessorsShouldReturnStoredValuesForMatchingTypes() {
        AnyValueImpl stringValue = new AnyValueImpl(
                AnyValue.Type.STRING, "abc", false, 0L, 0.0d, null, null, null);
        AnyValueImpl boolValue = new AnyValueImpl(
                AnyValue.Type.BOOL, null, true, 0L, 0.0d, null, null, null);
        AnyValueImpl intValue = new AnyValueImpl(
                AnyValue.Type.INT, null, false, 42L, 0.0d, null, null, null);
        AnyValueImpl doubleValue = new AnyValueImpl(
                AnyValue.Type.DOUBLE, null, false, 0L, 3.14d, null, null, null);
        AnyValueImpl arrayValue = new AnyValueImpl(
                AnyValue.Type.ARRAY,
                null,
                false,
                0L,
                0.0d,
                Collections.<AnyValue>singletonList(AnyValueFactory.ofString("x")),
                null,
                null);
        AnyValueImpl kvListValue = new AnyValueImpl(
                AnyValue.Type.KVLIST,
                null,
                false,
                0L,
                0.0d,
                null,
                Collections.<com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue>singletonList(
                        new KeyValueImpl("k", AnyValueFactory.ofString("v"))),
                null);
        AnyValueImpl bytesValue = new AnyValueImpl(
                AnyValue.Type.BYTES, null, false, 0L, 0.0d, null, null, new byte[]{1, 2, 3});

        assertEquals("abc", stringValue.asString());
        assertTrue(boolValue.asBoolean());
        assertEquals(42L, intValue.asLong());
        assertEquals(3.14d, doubleValue.asDouble(), 0.0001d);
        assertEquals(1, arrayValue.asArray().size());
        assertEquals(1, kvListValue.asKvList().size());
        assertArrayEquals(new byte[]{1, 2, 3}, bytesValue.asBytes());
    }

    @Test
    @DisplayName("mismatched accessors should return safe defaults in lenient mode")
    void mismatchedAccessorsShouldReturnSafeDefaultsInLenientMode() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        AnyValueImpl stringValue = new AnyValueImpl(
                AnyValue.Type.STRING, "abc", false, 0L, 0.0d, null, null, null);

        assertFalse(stringValue.asBoolean());
        assertEquals(0L, stringValue.asLong());
        assertEquals(0.0d, stringValue.asDouble(), 0.0d);
        assertTrue(stringValue.asArray().isEmpty());
        assertTrue(stringValue.asKvList().isEmpty());
        assertArrayEquals(new byte[0], stringValue.asBytes());
    }

    @Test
    @DisplayName("strict mode should throw on all mismatched accessor calls")
    void strictModeShouldThrowOnAllMismatchedAccessorCalls() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(false);
        AnyValueImpl boolValue = new AnyValueImpl(
                AnyValue.Type.BOOL, null, true, 0L, 0.0d, null, null, null);

        assertThrows(IllegalArgumentException.class, boolValue::asString);
        assertThrows(IllegalArgumentException.class, boolValue::asLong);
        assertThrows(IllegalArgumentException.class, boolValue::asDouble);
        assertThrows(IllegalArgumentException.class, boolValue::asArray);
        assertThrows(IllegalArgumentException.class, boolValue::asKvList);
        assertThrows(IllegalArgumentException.class, boolValue::asBytes);
    }

    @Test
    @DisplayName("equals and hashCode should cover all value kinds")
    void equalsAndHashCodeShouldCoverAllValueKinds() {
        AnyValue emptyLeft = AnyValueFactory.empty();
        AnyValue emptyRight = AnyValueFactory.empty();
        assertEquals(emptyLeft, emptyRight);
        assertEquals(emptyLeft.hashCode(), emptyRight.hashCode());

        AnyValue stringLeft = AnyValueFactory.ofString("text");
        AnyValue stringRight = AnyValueFactory.ofString("text");
        AnyValue stringDifferent = AnyValueFactory.ofString("different");
        assertEquals(stringLeft, stringRight);
        assertNotEquals(stringLeft, stringDifferent);

        AnyValue boolLeft = AnyValueFactory.ofBoolean(true);
        AnyValue boolRight = AnyValueFactory.ofBoolean(true);
        AnyValue boolDifferent = AnyValueFactory.ofBoolean(false);
        assertEquals(boolLeft, boolRight);
        assertNotEquals(boolLeft, boolDifferent);

        AnyValue intLeft = AnyValueFactory.ofLong(7L);
        AnyValue intRight = AnyValueFactory.ofLong(7L);
        AnyValue intDifferent = AnyValueFactory.ofLong(8L);
        assertEquals(intLeft, intRight);
        assertNotEquals(intLeft, intDifferent);

        AnyValue doubleLeft = AnyValueFactory.ofDouble(3.14d);
        AnyValue doubleRight = AnyValueFactory.ofDouble(3.14d);
        AnyValue doubleDifferent = AnyValueFactory.ofDouble(2.71d);
        assertEquals(doubleLeft, doubleRight);
        assertNotEquals(doubleLeft, doubleDifferent);

        AnyValue arrayLeft = AnyValueFactory.ofArray(Collections.singletonList(AnyValueFactory.ofString("a")));
        AnyValue arrayRight = AnyValueFactory.ofArray(Collections.singletonList(AnyValueFactory.ofString("a")));
        AnyValue arrayDifferent = AnyValueFactory.ofArray(Collections.singletonList(AnyValueFactory.ofString("b")));
        assertEquals(arrayLeft, arrayRight);
        assertNotEquals(arrayLeft, arrayDifferent);

        AnyValue kvLeft = AnyValueFactory.ofKvList(Arrays.<com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue>asList(
                new KeyValueImpl("a", AnyValueFactory.ofString("1")),
                new KeyValueImpl("b", AnyValueFactory.ofString("2"))));
        AnyValue kvRightDifferentOrder = AnyValueFactory.ofKvList(Arrays.<com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue>asList(
                new KeyValueImpl("b", AnyValueFactory.ofString("2")),
                new KeyValueImpl("a", AnyValueFactory.ofString("1"))));
        assertEquals(kvLeft, kvRightDifferentOrder);

        AnyValue bytesLeft = AnyValueFactory.ofBytes(new byte[]{1, 2, 3});
        AnyValue bytesRight = AnyValueFactory.ofBytes(new byte[]{1, 2, 3});
        AnyValue bytesDifferent = AnyValueFactory.ofBytes(new byte[]{9, 9, 9});
        assertEquals(bytesLeft, bytesRight);
        assertNotEquals(bytesLeft, bytesDifferent);
    }

    @Test
    @DisplayName("equals should reject null and other object types")
    void equalsShouldRejectNullAndOtherObjectTypes() {
        AnyValue value = AnyValueFactory.ofString("x");
        assertNotEquals(value, null);
        assertNotEquals(value, "x");
    }

    @Test
    @DisplayName("KVLIST equality should fall back to list equality when keys are not uniquely mappable")
    void kvListEqualityShouldFallBackToListEqualityWhenNotUniquelyMappable() {
        List<com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue> withNullKeyA =
                Collections.<com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue>singletonList(
                        new com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue() {
                            @Override
                            public String getKey() {
                                return null;
                            }

                            @Override
                            public AnyValue getValue() {
                                return AnyValueFactory.ofString("v");
                            }
                        });
        List<com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue> withNullKeyB =
                Collections.<com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue>singletonList(
                        new com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue() {
                            @Override
                            public String getKey() {
                                return null;
                            }

                            @Override
                            public AnyValue getValue() {
                                return AnyValueFactory.ofString("v");
                            }
                        });

        AnyValueImpl left = new AnyValueImpl(
                AnyValue.Type.KVLIST, null, false, 0L, 0.0d, null, withNullKeyA, null);
        AnyValueImpl right = new AnyValueImpl(
                AnyValue.Type.KVLIST, null, false, 0L, 0.0d, null, withNullKeyB, null);

        assertNotEquals(left, right);
        assertNotEquals(left.hashCode(), right.hashCode());
    }

    @Test
    @DisplayName("asBytes should return defensive copies")
    void asBytesShouldReturnDefensiveCopies() {
        AnyValue bytes = AnyValueFactory.ofBytes(new byte[]{1, 2, 3});
        byte[] first = bytes.asBytes();
        byte[] second = bytes.asBytes();

        assertNotSame(first, second);
        first[0] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, second);
    }
}
