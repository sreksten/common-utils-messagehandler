package com.threeamigos.common.util.interfaces.messagehandler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("LogLevelEnum unit tests")
@Tag("unit")
@Tag("messageHandler")
class LogLevelEnumUnitTest {

    @Test
    @DisplayName("getSeverityText() should return enum name")
    void getSeverityTextShouldReturnName() {
        for (LogLevelEnum level : LogLevelEnum.values()) {
            assertEquals(level.name(), level.getSeverityText());
        }
    }

    @Test
    @DisplayName("getSeverityLevel() should return enum ordinal")
    void getSeverityLevelShouldReturnOrdinal() {
        for (LogLevelEnum level : LogLevelEnum.values()) {
            assertEquals(level.ordinal(), level.getSeverityLevel());
        }
    }
}
