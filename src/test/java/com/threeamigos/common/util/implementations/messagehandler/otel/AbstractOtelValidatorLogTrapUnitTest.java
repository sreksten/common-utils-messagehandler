package com.threeamigos.common.util.implementations.messagehandler.otel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class AbstractOtelValidatorLogTrapUnitTest {

    @BeforeEach
    void installNoOpValidatorLogTrap() {
        OpenTelemetryAttributeValidator.setLogTrapForTests((message, throwable) -> {
            // Swallow validator logs in tests unless a test explicitly installs its own trap.
        });
    }

    @AfterEach
    void clearValidatorLogTrap() {
        OpenTelemetryAttributeValidator.setLogTrapForTests(null);
    }
}
