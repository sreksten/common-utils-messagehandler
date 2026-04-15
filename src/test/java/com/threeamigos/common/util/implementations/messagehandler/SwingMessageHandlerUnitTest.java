package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.SwingMessageHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SwingMessageHandler unit test")
@Tag("unit")
@Tag("messageHandler")
class SwingMessageHandlerUnitTest {

    private static class CapturingSwingMessageHandler extends SwingMessageHandler {
        private int calls;
        private String lastMessage;
        private String lastTitle;
        private int lastIcon;

        @Override
        protected void showOptionPane(final String message, final String title, final int icon) {
            calls++;
            lastMessage = message;
            lastTitle = title;
            lastIcon = icon;
        }
    }

    @Test
    @DisplayName("No-args constructor should not set a parent component")
    void noArgsConstructor() {
        SwingMessageHandler swingMessageHandler = new SwingMessageHandler();
        assertNull(swingMessageHandler.getParentComponent(), "No-args constructor should not set a component");
    }

    @Test
    @DisplayName("Parameterized constructor should set a parent component")
    void componentConstructor() {
        Component component = new JLabel();
        SwingMessageHandler swingMessageHandler = new SwingMessageHandler(component);
        assertEquals(component, swingMessageHandler.getParentComponent(), "Wrong parent component");
    }

    @Test
    @DisplayName("Component setter works")
    void componentSetterWorks() {
        Component component = new JLabel();
        SwingMessageHandler swingMessageHandler = new SwingMessageHandler();
        swingMessageHandler.setParentComponent(component);
        assertEquals(component, swingMessageHandler.getParentComponent(), "Wrong parent component");
    }

    @Test
    @DisplayName("Should throw an exception if a null info message is provided")
    void shouldThrowAnExceptionIfANullInfoMessageIsProvided() {
        SwingMessageHandler sut = new SwingMessageHandler();
        assertThrows(NullPointerException.class, () -> sut.handleInfoMessage((String) null));
    }

    @Test
    @DisplayName("Should throw an exception if a null warn message is provided")
    void shouldThrowAnExceptionIfANullWarnMessageIsProvided() {
        SwingMessageHandler sut = new SwingMessageHandler();
        assertThrows(NullPointerException.class, () -> sut.handleWarnMessage((String) null));
    }

    @Test
    @DisplayName("Should throw an exception if a null error message is provided")
    void shouldThrowAnExceptionIfANullErrorMessageIsProvided() {
        SwingMessageHandler sut = new SwingMessageHandler();
        assertThrows(NullPointerException.class, () -> sut.handleErrorMessage((String) null));
    }

    @Test
    @DisplayName("Should throw an exception if a null debug message is provided")
    void shouldThrowAnExceptionIfANullDebugMessageIsProvided() {
        SwingMessageHandler sut = new SwingMessageHandler();
        assertThrows(NullPointerException.class, () -> sut.handleDebugMessage((String) null));
    }

    @Test
    @DisplayName("Should throw an exception if a null trace message is provided")
    void shouldThrowAnExceptionIfANullTraceMessageIsProvided() {
        SwingMessageHandler sut = new SwingMessageHandler();
        assertThrows(NullPointerException.class, () -> sut.handleTraceMessage((String) null));
    }

    @Test
    @DisplayName("Should throw an exception if a null exception is provided")
    void shouldThrowAnExceptionIfANullExceptionIsProvided() {
        SwingMessageHandler sut = new SwingMessageHandler();
        assertThrows(NullPointerException.class, () -> sut.handleException((Exception) null));
    }

    @Test
    @DisplayName("Should throw an exception if a null exception message is provided")
    void shouldThrowAnExceptionIfANullExceptionMessageIsProvided() {
        SwingMessageHandler sut = new SwingMessageHandler();
        assertThrows(NullPointerException.class, () -> sut.handleException(null, new RuntimeException("boom")));
    }

    @Test
    @DisplayName("Should throw an exception if a null exception is provided with message")
    void shouldThrowAnExceptionIfANullExceptionIsProvidedWithMessage() {
        SwingMessageHandler sut = new SwingMessageHandler();
        assertThrows(NullPointerException.class, () -> sut.handleException("message", null));
    }

    @Test
    @DisplayName("Should display info, warn, error, debug and trace messages")
    void shouldDisplayStandardMessages() {
        CapturingSwingMessageHandler sut = new CapturingSwingMessageHandler();

        sut.handleInfoMessage("info-message");
        assertEquals(1, sut.calls);
        assertEquals("info-message", sut.lastMessage);
        assertEquals("Info", sut.lastTitle);
        assertEquals(JOptionPane.INFORMATION_MESSAGE, sut.lastIcon);

        sut.handleWarnMessage("warn-message");
        assertEquals(2, sut.calls);
        assertEquals("warn-message", sut.lastMessage);
        assertEquals("Warning", sut.lastTitle);
        assertEquals(JOptionPane.WARNING_MESSAGE, sut.lastIcon);

        sut.handleErrorMessage("error-message");
        assertEquals(3, sut.calls);
        assertEquals("error-message", sut.lastMessage);
        assertEquals("Error", sut.lastTitle);
        assertEquals(JOptionPane.ERROR_MESSAGE, sut.lastIcon);

        sut.handleDebugMessage("debug-message");
        assertEquals(4, sut.calls);
        assertEquals("debug-message", sut.lastMessage);
        assertEquals("Debug", sut.lastTitle);
        assertEquals(JOptionPane.INFORMATION_MESSAGE, sut.lastIcon);

        sut.handleTraceMessage("trace-message");
        assertEquals(5, sut.calls);
        assertEquals("trace-message", sut.lastMessage);
        assertEquals("Trace", sut.lastTitle);
        assertEquals(JOptionPane.INFORMATION_MESSAGE, sut.lastIcon);
    }

    @Test
    @DisplayName("Should display exceptions")
    void shouldDisplayExceptions() {
        CapturingSwingMessageHandler sut = new CapturingSwingMessageHandler();

        sut.handleException(new RuntimeException("boom"));
        assertEquals(1, sut.calls);
        assertEquals("boom", sut.lastMessage);
        assertEquals("Exception", sut.lastTitle);
        assertEquals(JOptionPane.ERROR_MESSAGE, sut.lastIcon);

        sut.handleException("prefix", new RuntimeException("kaboom"));
        assertEquals(2, sut.calls);
        assertEquals("prefix: kaboom", sut.lastMessage);
        assertEquals("Exception", sut.lastTitle);
        assertEquals(JOptionPane.ERROR_MESSAGE, sut.lastIcon);
    }

    @Test
    @DisplayName("Should evaluate suppliers only when levels are enabled")
    void shouldEvaluateSuppliersOnlyWhenLevelsAreEnabled() {
        CapturingSwingMessageHandler sut = new CapturingSwingMessageHandler();
        AtomicBoolean supplierCalled = new AtomicBoolean(false);

        sut.setInfoEnabled(false);
        sut.handleInfoMessage(() -> {
            supplierCalled.set(true);
            return "ignored";
        });
        assertEquals(0, sut.calls);
        assertTrue(!supplierCalled.get());

        sut.setInfoEnabled(true);
        sut.handleInfoMessage(() -> {
            supplierCalled.set(true);
            return "evaluated";
        });
        assertEquals(1, sut.calls);
        assertEquals("evaluated", sut.lastMessage);
        assertTrue(supplierCalled.get());
    }

    @Test
    @DisplayName("Should not display anything when all levels are disabled")
    void shouldNotDisplayAnythingWhenAllLevelsAreDisabled() {
        CapturingSwingMessageHandler sut = new CapturingSwingMessageHandler();
        sut.setInfoEnabled(false);
        sut.setWarnEnabled(false);
        sut.setErrorEnabled(false);
        sut.setDebugEnabled(false);
        sut.setTraceEnabled(false);
        sut.setExceptionEnabled(false);

        sut.handleInfoMessage("info");
        sut.handleWarnMessage("warn");
        sut.handleErrorMessage("error");
        sut.handleDebugMessage("debug");
        sut.handleTraceMessage("trace");
        sut.handleException(new RuntimeException("boom"));
        sut.handleException("prefix", new RuntimeException("kaboom"));

        assertEquals(0, sut.calls);
    }

    @Test
    @DisplayName("Real option pane call should be safe in headless mode")
    void realOptionPaneCallShouldBeSafeInHeadlessMode() {
        String original = System.getProperty("java.awt.headless");
        System.setProperty("java.awt.headless", "true");
        try {
            SwingMessageHandler sut = new SwingMessageHandler();
            assertDoesNotThrow(() -> sut.handleInfoMessage("headless-safe"));
        } finally {
            if (original == null) {
                System.clearProperty("java.awt.headless");
            } else {
                System.setProperty("java.awt.headless", original);
            }
        }
    }
}
