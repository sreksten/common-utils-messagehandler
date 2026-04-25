package com.threeamigos.common.util.implementations.messagehandler.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("ClassNameReducer unit tests")
@Tag("unit")
@Tag("messageHandler")
class ClassNameReducerUnitTest {

    @Test
    @DisplayName("default constructor should be instantiable")
    void defaultConstructorShouldBeInstantiable() {
        assertNotNull(new ClassNameReducer());
    }

    @Test
    @DisplayName("reduce() should keep simple class names unchanged")
    void reduceShouldKeepSimpleClassNamesUnchanged() {
        assertEquals("Foo", ClassNameReducer.reduce("Foo"));
    }

    @Test
    @DisplayName("reduce() should abbreviate package segments and keep simple class name")
    void reduceShouldAbbreviatePackageSegments() {
        assertEquals("c.e.Foo", ClassNameReducer.reduce("com.example.Foo"));
        assertEquals("a.b.c.Service", ClassNameReducer.reduce("alpha.beta.charlie.Service"));
    }
}
