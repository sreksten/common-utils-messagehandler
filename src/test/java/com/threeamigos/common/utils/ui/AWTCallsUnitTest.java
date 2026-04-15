package com.threeamigos.common.utils.ui;

import com.threeamigos.common.util.ui.AWTCalls;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.swing.*;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("AWTCalls unit tests")
class AWTCallsUnitTest {

    @Test
    @DisplayName("Should no-op when headless")
    void shouldNoOpWhenHeadless() {
        String original = System.getProperty("java.awt.headless");
        System.setProperty("java.awt.headless", "true");
        try {
            assertDoesNotThrow(() -> AWTCalls.showOptionPane(null, "msg", "title", JOptionPane.INFORMATION_MESSAGE));
        } finally {
            if (original == null) {
                System.clearProperty("java.awt.headless");
            } else {
                System.setProperty("java.awt.headless", original);
            }
        }
    }

}
