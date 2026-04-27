package com.threeamigos.common.util.implementations.messagehandler.otel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("BuilderValidationUtils unit tests")
@Tag("unit")
@Tag("messageHandler")
class BuilderValidationUtilsUnitTest {

    @Test
    @DisplayName("requireNonBlank should return value when valid")
    void requireNonBlankShouldReturnValueWhenValid() {
        assertEquals("checkout", BuilderValidationUtils.requireNonBlank("checkout", "service.name"));
    }

    @Test
    @DisplayName("requireNonBlank should reject null and blank")
    void requireNonBlankShouldRejectNullAndBlank() {
        assertThrows(NullPointerException.class,
                () -> BuilderValidationUtils.requireNonBlank(null, "service.name"));
        assertThrows(IllegalArgumentException.class,
                () -> BuilderValidationUtils.requireNonBlank("   ", "service.name"));
    }

    @Test
    @DisplayName("constructor should be private utility guard")
    void constructorShouldBePrivateUtilityGuard() throws Exception {
        Constructor<BuilderValidationUtils> constructor = BuilderValidationUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException exception = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertNotNull(exception.getCause());
        assertEquals(UnsupportedOperationException.class, exception.getCause().getClass());
    }
}
