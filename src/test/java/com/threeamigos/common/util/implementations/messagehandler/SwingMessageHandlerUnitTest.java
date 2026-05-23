package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("SwingMessageHandler unit test")
@Tag("unit")
@Tag("messageHandler")
class SwingMessageHandlerUnitTest {

    private static class CapturingSwingMessageHandler extends SwingMessageHandler {
        private int calls;
        private String lastMessage;
        private String lastTitle;
        private int lastIcon;

        CapturingSwingMessageHandler() {
            super();
        }

        CapturingSwingMessageHandler(final LogRecordFactory logRecordFactory,
                                     final LogRecordFormatter logRecordFormatter) {
            super(logRecordFactory, logRecordFormatter);
        }

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
    @DisplayName("Parent component field should be volatile for cross-thread visibility")
    void parentComponentFieldShouldBeVolatileForCrossThreadVisibility() throws Exception {
        Field parentComponentField = SwingMessageHandler.class.getDeclaredField("parentComponent");
        assertTrue(Modifier.isVolatile(parentComponentField.getModifiers()));
    }

    @Test
    @DisplayName("Should ignore null info message")
    void shouldIgnoreNullInfoMessage() {
        CapturingSwingMessageHandler sut = new CapturingSwingMessageHandler();
        assertDoesNotThrow(() -> sut.info((String) null));
        assertEquals(0, sut.calls);
    }

    @Test
    @DisplayName("Should ignore null warn message")
    void shouldIgnoreNullWarnMessage() {
        CapturingSwingMessageHandler sut = new CapturingSwingMessageHandler();
        assertDoesNotThrow(() -> sut.warn((String) null));
        assertEquals(0, sut.calls);
    }

    @Test
    @DisplayName("Should ignore null fatal message")
    void shouldIgnoreNullFatalMessage() {
        CapturingSwingMessageHandler sut = new CapturingSwingMessageHandler();
        assertDoesNotThrow(() -> sut.fatal((String) null));
        assertEquals(0, sut.calls);
    }

    @Test
    @DisplayName("Should ignore null error message")
    void shouldIgnoreNullErrorMessage() {
        CapturingSwingMessageHandler sut = new CapturingSwingMessageHandler();
        assertDoesNotThrow(() -> sut.error((String) null));
        assertEquals(0, sut.calls);
    }

    @Test
    @DisplayName("Should ignore null debug message")
    void shouldIgnoreNullDebugMessage() {
        CapturingSwingMessageHandler sut = new CapturingSwingMessageHandler();
        sut.setDebugEnabled(true);
        assertDoesNotThrow(() -> sut.debug((String) null));
        assertEquals(0, sut.calls);
    }

    @Test
    @DisplayName("Should ignore null trace message")
    void shouldIgnoreNullTraceMessage() {
        CapturingSwingMessageHandler sut = new CapturingSwingMessageHandler();
        sut.setTraceEnabled(true);
        assertDoesNotThrow(() -> sut.trace((String) null));
        assertEquals(0, sut.calls);
    }

    @Test
    @DisplayName("Should ignore a null exception")
    void shouldIgnoreANullException() {
        CapturingSwingMessageHandler sut = new CapturingSwingMessageHandler();
        assertDoesNotThrow(() -> sut.exception((Exception) null));
        assertEquals(0, sut.calls);
    }

    @Test
    @DisplayName("Should ignore null exception message")
    void shouldIgnoreNullExceptionMessage() {
        CapturingSwingMessageHandler sut = new CapturingSwingMessageHandler();
        assertDoesNotThrow(() -> sut.exception(null, new RuntimeException("boom")));
        assertEquals(0, sut.calls);
    }

    @Test
    @DisplayName("Should ignore a null exception when a message is provided")
    void shouldIgnoreANullExceptionWhenAMessageIsProvided() {
        CapturingSwingMessageHandler sut = new CapturingSwingMessageHandler();
        assertDoesNotThrow(() -> sut.exception("message", null));
        assertEquals(0, sut.calls);
    }

    @Test
    @DisplayName("Should display info, warn, error, debug and trace messages")
    void shouldDisplayStandardMessages() {
        CapturingSwingMessageHandler sut = new CapturingSwingMessageHandler();
        sut.setDebugEnabled(true);
        sut.setTraceEnabled(true);

        sut.info("info-message");
        assertEquals(1, sut.calls);
        assertEquals("info-message", sut.lastMessage);
        assertEquals("Info", sut.lastTitle);
        assertEquals(JOptionPane.INFORMATION_MESSAGE, sut.lastIcon);

        sut.warn("warn-message");
        assertEquals(2, sut.calls);
        assertEquals("warn-message", sut.lastMessage);
        assertEquals("Warning", sut.lastTitle);
        assertEquals(JOptionPane.WARNING_MESSAGE, sut.lastIcon);

        sut.error("error-message");
        assertEquals(3, sut.calls);
        assertEquals("error-message", sut.lastMessage);
        assertEquals("Error", sut.lastTitle);
        assertEquals(JOptionPane.ERROR_MESSAGE, sut.lastIcon);

        sut.fatal("fatal-message");
        assertEquals(4, sut.calls);
        assertEquals("fatal-message", sut.lastMessage);
        assertEquals("Fatal", sut.lastTitle);
        assertEquals(JOptionPane.ERROR_MESSAGE, sut.lastIcon);

        sut.debug("debug-message");
        assertEquals(5, sut.calls);
        assertEquals("debug-message", sut.lastMessage);
        assertEquals("Debug", sut.lastTitle);
        assertEquals(JOptionPane.INFORMATION_MESSAGE, sut.lastIcon);

        sut.trace("trace-message");
        assertEquals(6, sut.calls);
        assertEquals("trace-message", sut.lastMessage);
        assertEquals("Trace", sut.lastTitle);
        assertEquals(JOptionPane.INFORMATION_MESSAGE, sut.lastIcon);
    }

    @Test
    @DisplayName("Should display exceptions")
    void shouldDisplayExceptions() {
        CapturingSwingMessageHandler sut = new CapturingSwingMessageHandler();

        sut.exception(new RuntimeException("boom"));
        assertEquals(1, sut.calls);
        assertEquals("boom", sut.lastMessage);
        assertEquals("Exception", sut.lastTitle);
        assertEquals(JOptionPane.ERROR_MESSAGE, sut.lastIcon);

        sut.exception("prefix", new RuntimeException("kaboom"));
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
        sut.info(() -> {
            supplierCalled.set(true);
            return "ignored";
        });
        assertEquals(0, sut.calls);
        assertTrue(!supplierCalled.get());

        sut.setInfoEnabled(true);
        sut.info(() -> {
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
        sut.setFatalEnabled(false);
        sut.setDebugEnabled(false);
        sut.setTraceEnabled(false);

        sut.info("info");
        sut.warn("warn");
        sut.error("error");
        sut.fatal("fatal");
        sut.debug("debug");
        sut.trace("trace");
        sut.exception(new RuntimeException("boom"));
        sut.exception("prefix", new RuntimeException("kaboom"));

        assertEquals(0, sut.calls);
    }

    @Test
    @DisplayName("Real option pane call should be safe in headless mode")
    void realOptionPaneCallShouldBeSafeInHeadlessMode() {
        String original = System.getProperty("java.awt.headless");
        System.setProperty("java.awt.headless", "true");
        try {
            SwingMessageHandler sut = new SwingMessageHandler();
            assertDoesNotThrow(() -> sut.info("headless-safe"));
        } finally {
            if (original == null) {
                System.clearProperty("java.awt.headless");
            } else {
                System.setProperty("java.awt.headless", original);
            }
        }
    }

    @Test
    @DisplayName("Should use provided formatter when factory and formatter are configured")
    void shouldUseProvidedFormatterWhenFactoryAndFormatterAreConfigured() {
        LogRecordFactory factory = mock(LogRecordFactory.class);
        LogRecordFormatter formatter = mock(LogRecordFormatter.class);
        RuntimeException boom = new RuntimeException("boom");

        when(formatter.format(org.mockito.ArgumentMatchers.any(
                com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord.class)))
                .thenReturn("formatted-info", "formatted-prefix");

        CapturingSwingMessageHandler sut = new CapturingSwingMessageHandler(factory, formatter);
        sut.info("plain-info");
        assertEquals("formatted-info", sut.lastMessage);

        sut.exception("prefix", boom);
        assertEquals("formatted-prefix: boom", sut.lastMessage);

        verifyNoInteractions(factory);
        verify(formatter, times(2)).format(org.mockito.ArgumentMatchers.any(
                com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord.class));
    }

    @Test
    @DisplayName("Factory-only configuration should keep fallback formatting behavior")
    void factoryOnlyConfigurationShouldKeepFallbackFormattingBehavior() {
        LogRecordFactory factory = mock(LogRecordFactory.class);
        CapturingSwingMessageHandler sut = new CapturingSwingMessageHandler(factory, null);
        RuntimeException boom = new RuntimeException("boom");

        sut.info("plain-info");
        assertEquals("plain-info", sut.lastMessage);

        sut.exception("prefix", boom);
        assertEquals("prefix: boom", sut.lastMessage);

        verifyNoInteractions(factory);
    }
}
