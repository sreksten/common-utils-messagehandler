package com.threeamigos.common.util.implementations.messagehandler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("MessageHandlerResourceBundles unit tests")
@Tag("unit")
@Tag("messageHandler")
class MessageHandlerResourceBundleUnitTest {

    @Test
    @DisplayName("Should load existing bundle using explicit class loader")
    void shouldLoadExistingBundleUsingExplicitClassLoader() {
        ResourceBundle bundle = MessageHandlerResourceBundle.load(
                "com.threeamigos.common.util.implementations.messagehandler.MessageHandler.MessageHandler",
                MessageHandlerResourceBundle.class);

        assertEquals("Null message provided.", bundle.getString("nullMessageProvided"));
    }

    @Test
    @DisplayName("Should throw when bundle is missing")
    void shouldThrowWhenBundleIsMissing() {
        assertThrows(MissingResourceException.class,
                () -> MessageHandlerResourceBundle.load("com.threeamigos.missing.bundle", AbstractMessageHandler.class));
    }

    @Test
    @DisplayName("Should expose span-start error message in default and italian bundles")
    void shouldExposeSpanStartErrorMessageInDefaultAndItalianBundles() {
        ResourceBundle defaultBundle = ResourceBundle.getBundle(
                "com.threeamigos.common.util.implementations.messagehandler.MessageHandler.MessageHandler",
                Locale.ENGLISH,
                MessageHandlerResourceBundle.class.getClassLoader());
        ResourceBundle italianBundle = ResourceBundle.getBundle(
                "com.threeamigos.common.util.implementations.messagehandler.MessageHandler.MessageHandler",
                Locale.ITALY,
                MessageHandlerResourceBundle.class.getClassLoader());

        assertEquals(
                "Cannot start span because no tracer is bound to this handler.",
                defaultBundle.getString("cannotStartSpanNoTracerBound"));
        assertEquals(
                "Impossibile avviare lo span perch\u00e9 nessun tracer \u00e8 associato a questo handler.",
                italianBundle.getString("cannotStartSpanNoTracerBound"));
    }
}
