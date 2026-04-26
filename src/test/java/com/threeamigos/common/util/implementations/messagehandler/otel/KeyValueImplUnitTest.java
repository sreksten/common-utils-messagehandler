package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("KeyValueImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class KeyValueImplUnitTest {

    @Test
    @DisplayName("KeyValueFactory constructor should be private and reject instantiation")
    void keyValueFactoryConstructorShouldBePrivateAndRejectInstantiation() throws Exception {
        Constructor<KeyValueFactory> constructor = KeyValueFactory.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                constructor::newInstance);

        assertTrue(exception.getCause() instanceof UnsupportedOperationException);
    }

    @Test
    @DisplayName("constructor should reject null key")
    void constructorShouldRejectNullKey() {
        assertThrows(NullPointerException.class, () -> new KeyValueImpl((String) null, AnyValueFactory.ofString("v")));
    }

    @Test
    @DisplayName("constructor should reject empty key")
    void constructorShouldRejectEmptyKey() {
        assertThrows(IllegalArgumentException.class, () -> new KeyValueImpl("", AnyValueFactory.ofString("v")));
    }

    @Test
    @DisplayName("constructor should reject null value")
    void constructorShouldRejectNullValue() {
        assertThrows(NullPointerException.class, () -> new KeyValueImpl("k", null));
    }

    @Test
    @DisplayName("constructor should reject null Names key")
    void constructorShouldRejectNullNamesKey() {
        assertThrows(NullPointerException.class, () -> new KeyValueImpl((Names) null, AnyValueFactory.ofString("v")));
    }

    @Test
    @DisplayName("constructor should accept Names key")
    void constructorShouldAcceptNamesKey() {
        AnyValue value = AnyValueFactory.ofString("service-a");
        KeyValue kv = new KeyValueImpl(Names.ATTR_SERVICE_NAME, value);
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
    @DisplayName("factory should accept Names key")
    void factoryShouldAcceptNamesKey() {
        AnyValue value = AnyValueFactory.ofString("200");
        KeyValue kv = KeyValueFactory.of(Names.ATTR_HTTP_RESPONSE_STATUS_CODE, value);
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
}
