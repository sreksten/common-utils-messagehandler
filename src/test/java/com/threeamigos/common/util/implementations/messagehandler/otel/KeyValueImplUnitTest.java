package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("KeyValueImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class KeyValueImplUnitTest {

    @org.junit.jupiter.api.AfterEach
    void restoreLenient() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(false);
    }

    @Test
    @DisplayName("constructor should reject null key")
    void constructorShouldRejectNullKey() {
        assertThrows(IllegalArgumentException.class, () -> new KeyValueImpl((String) null, AnyValueFactory.ofString("v")));
    }

    @Test
    @DisplayName("constructor should reject empty key")
    void constructorShouldRejectEmptyKey() {
        assertThrows(IllegalArgumentException.class, () -> new KeyValueImpl("", AnyValueFactory.ofString("v")));
    }

    @Test
    @DisplayName("constructor should reject null value")
    void constructorShouldRejectNullValue() {
        assertThrows(IllegalArgumentException.class, () -> new KeyValueImpl("k", null));
    }

    @Test
    @DisplayName("constructor should reject null OTelTags key")
    void constructorShouldRejectNullOtelTagsKey() {
        assertThrows(IllegalArgumentException.class, () -> new KeyValueImpl((OTelTags) null, AnyValueFactory.ofString("v")));
    }

    @Test
    @DisplayName("constructor should accept OTelTags key")
    void constructorShouldAcceptOtelTagsKey() {
        AnyValue value = AnyValueFactory.ofString("service-a");
        KeyValue kv = new KeyValueImpl(OTelTags.SERVICE_NAME, value);
        assertEquals("service.name", kv.getKey());
        assertSame(value, kv.getValue());
    }

    @Test
    @DisplayName("constructor should accept valid key/value")
    void constructorShouldAcceptValidKeyValue() {
        AnyValue value = AnyValueFactory.ofLong(42);
        KeyValue kv = new KeyValueImpl("k", value);
        assertEquals("k", kv.getKey());
        assertSame(value, kv.getValue());
    }

    @Test
    @DisplayName("constructor should accept heterogeneous AnyValue arrays")
    void constructorShouldAcceptHeterogeneousAnyValueArrays() {
        AnyValue array = AnyValueFactory.ofArray(Arrays.asList(
                AnyValueFactory.ofString("a"),
                AnyValueFactory.ofLong(2),
                AnyValueFactory.ofBoolean(true),
                AnyValueFactory.empty()
        ));
        assertDoesNotThrow(() -> new KeyValueImpl("array", array));
    }

    @Test
    @DisplayName("factory should accept OTelTags key")
    void factoryShouldAcceptOtelTagsKey() {
        AnyValue value = AnyValueFactory.ofString("200");
        KeyValue kv = KeyValueFactory.of(OTelTags.HTTP_RESPONSE_STATUS_CODE, value);
        assertEquals("http.response.status_code", kv.getKey());
        assertSame(value, kv.getValue());
    }

    @Test
    @DisplayName("factory should accept explicit key")
    void factoryShouldAcceptExplicitKey() {
        AnyValue value = AnyValueFactory.ofString("value");
        KeyValue kv = KeyValueFactory.of("custom.key", value);
        assertEquals("custom.key", kv.getKey());
        assertSame(value, kv.getValue());
    }

    @Test
    @DisplayName("equals and hashCode should compare key and value")
    void equalsAndHashCodeShouldCompareKeyAndValue() {
        KeyValueImpl left = new KeyValueImpl("service.name", AnyValueFactory.ofString("checkout"));
        KeyValueImpl same = new KeyValueImpl("service.name", AnyValueFactory.ofString("checkout"));
        KeyValueImpl differentKey = new KeyValueImpl("service.namespace", AnyValueFactory.ofString("checkout"));
        KeyValueImpl differentValue = new KeyValueImpl("service.name", AnyValueFactory.ofString("payments"));

        assertEquals(left, same);
        assertEquals(left.hashCode(), same.hashCode());
        assertNotEquals(left, differentKey);
        assertNotEquals(left, differentValue);
        assertNotEquals(left, null);
        assertNotEquals(left, "service.name=checkout");
        assertEquals(left, left);
    }

    @Test
    @DisplayName("lenient mode should normalize invalid constructor values")
    void lenientModeShouldNormalizeInvalidConstructorValues() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);

        KeyValueImpl fromNullTag = new KeyValueImpl((OTelTags) null, null);
        KeyValueImpl fromNullKey = new KeyValueImpl((String) null, AnyValueFactory.ofString("x"));
        KeyValueImpl fromBlankKey = new KeyValueImpl(" ", null);

        assertEquals("unknown", fromNullTag.getKey());
        assertEquals(AnyValue.Type.EMPTY, fromNullTag.getValue().getType());
        assertEquals("unknown", fromNullKey.getKey());
        assertEquals("unknown", fromBlankKey.getKey());
        assertEquals(AnyValue.Type.EMPTY, fromBlankKey.getValue().getType());
    }
}
