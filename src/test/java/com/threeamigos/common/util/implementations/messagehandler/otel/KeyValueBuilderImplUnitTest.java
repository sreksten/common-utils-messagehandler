package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("KeyValueBuilderImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class KeyValueBuilderImplUnitTest extends AbstractOtelValidatorLogTrapUnitTest {

    private static final boolean ORIGINAL_LENIENT = OpenTelemetryAttributeValidator.isLenientMode();

    @Test
    @DisplayName("all builder methods should return same instance")
    void allBuilderMethodsShouldReturnSameInstance() {
        KeyValueBuilderImpl builder = new KeyValueBuilderImpl();

        AnyValue anyValue = AnyValueFactory.ofString("v");
        KeyValue keyValue = new KeyValueImpl("k", anyValue);

        assertSame(builder, builder.withEmpty("empty"));
        assertSame(builder, builder.withString("s", "v"));
        assertSame(builder, builder.withBoolean("b", true));
        assertSame(builder, builder.withLong("l", 1L));
        assertSame(builder, builder.withDouble("d", 1.5));
        assertSame(builder, builder.withArray("a", Arrays.asList(anyValue)));
        assertSame(builder, builder.withKeyValueList("kvl", Collections.singletonList(keyValue)));
        assertSame(builder, builder.withBytes("bytes", new byte[] {1, 2}));
    }

    @Test
    @DisplayName("withString should map null values to AnyValue.EMPTY in lenient mode")
    void withStringShouldMapNullValuesToEmptyInLenientMode() {
        setLenient(true);
        KeyValueBuilderImpl builder = new KeyValueBuilderImpl();
        assertDoesNotThrow(() -> builder.withString("s", null));
        assertDoesNotThrow(() -> builder.withString(OTelTags.SERVICE_NAME, null));
        assertEquals(2, builder.attributes.size());
        assertEquals(AnyValue.Type.EMPTY, builder.attributes.get(0).getValue().getType());
        assertEquals(AnyValue.Type.EMPTY, builder.attributes.get(1).getValue().getType());
        setLenient(ORIGINAL_LENIENT);
    }

    @Test
    @DisplayName("withString should throw on null values in strict mode")
    void withStringShouldThrowOnNullValuesInStrictMode() {
        setLenient(false);
        KeyValueBuilderImpl builder = new KeyValueBuilderImpl();
        assertThrows(IllegalArgumentException.class, () -> builder.withString("s", null));
        assertThrows(IllegalArgumentException.class, () -> builder.withString(OTelTags.SERVICE_NAME, null));
        setLenient(ORIGINAL_LENIENT);
    }

    private static void setLenient(final boolean value) {
        OpenTelemetryAttributeValidator.setLenientModeForTests(value);
    }
}
