package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("InstrumentationScopeFactory unit tests")
@Tag("unit")
@Tag("messageHandler")
class InstrumentationScopeFactoryUnitTest {

    @Test
    @DisplayName("create() should return an InstrumentationScope with the provided scalar fields")
    void createShouldReturnScopeWithProvidedScalarFields() {
        InstrumentationScope scope = InstrumentationScopeFactory.create(
                "com.example.lib",
                "1.0.0",
                "https://opentelemetry.io/schemas/1.27.0",
                Collections.singletonList(new KeyValueImpl("scope.attr", AnyValueImpl.ofString("x"))));

        assertEquals("com.example.lib", scope.getName());
        assertEquals("1.0.0", scope.getVersion());
        assertEquals("https://opentelemetry.io/schemas/1.27.0", scope.getSchemaUrl());
        assertEquals(1, scope.getAttributes().size());
        assertEquals(0, scope.getDroppedAttributesCount());
        assertThrows(UnsupportedOperationException.class,
                () -> scope.getAttributes().add(new KeyValueImpl("another", AnyValueImpl.ofString("y"))));
    }

    @Test
    @DisplayName("create() should enforce attribute limits and drop overflow attributes")
    void createShouldDropOverflowAttributes() {
        List<KeyValue> attributes = new ArrayList<>();
        for (int i = 0; i < 129; i++) {
            attributes.add(new KeyValueImpl("k" + i, AnyValueImpl.ofString("v" + i)));
        }

        InstrumentationScope scope = InstrumentationScopeFactory.create(null, null, null, attributes);

        assertEquals(128, scope.getAttributes().size());
        assertEquals(1, scope.getDroppedAttributesCount());
        assertTrue(scope.getAttributes().stream().anyMatch(kv -> "k0".equals(kv.getKey())));
        assertTrue(scope.getAttributes().stream().noneMatch(kv -> "k128".equals(kv.getKey())));
    }
}
