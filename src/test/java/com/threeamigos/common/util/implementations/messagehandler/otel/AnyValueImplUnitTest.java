package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
