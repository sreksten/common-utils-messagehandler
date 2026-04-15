package com.threeamigos.common.util.implementations.messagehandler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("MessageHandlerResourceBundles unit tests")
@Tag("unit")
@Tag("messageHandler")
class MessageHandlerResourceBundlesUnitTest {

    @Test
    @DisplayName("Should load existing bundle using explicit class loader")
    void shouldLoadExistingBundleUsingExplicitClassLoader() {
        ResourceBundle bundle = MessageHandlerResourceBundles.load(
                "com.threeamigos.common.util.implementations.messagehandler.AbstractMessageHandler.AbstractMessageHandler",
                AbstractMessageHandler.class);

        assertEquals("Null message provided.", bundle.getString("nullMessageProvided"));
    }

    @Test
    @DisplayName("Should throw when bundle is missing")
    void shouldThrowWhenBundleIsMissing() {
        assertThrows(MissingResourceException.class,
                () -> MessageHandlerResourceBundles.load("com.threeamigos.missing.bundle", AbstractMessageHandler.class));
    }
}
