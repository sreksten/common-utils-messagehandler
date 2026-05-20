package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("JULMessageHandler unit tests")
@Tag("unit")
@Tag("messageHandler")
class JULMessageHandlerUnitTest {

    private static class CapturingHandler extends Handler {
        LogRecord last;
        @Override
        public void publish(LogRecord record) {
            last = record;
        }
        @Override
        public void flush() {}
        @Override
        public void close() throws SecurityException {}
    }

    @Test
    @DisplayName("Should throw on null logger name")
    void shouldThrowOnNullLoggerName() {
        assertThrows(NullPointerException.class, () -> new JULMessageHandler((String) null));
        assertThrows(IllegalArgumentException.class, () -> new JULMessageHandler(" "));
    }

    @Test
    @DisplayName("Should throw on null Logger instance")
    void shouldThrowOnNullLoggerInstance() {
        assertThrows(NullPointerException.class, () -> new JULMessageHandler((Logger) null));
    }

    @Test
    @DisplayName("Should create logger by name")
    void shouldCreateLoggerByName() {
        JULMessageHandler handler = new JULMessageHandler("jul-handler-name");
        assertNotNull(handler);
    }

    @Test
    @DisplayName("Should bridge to JUL levels")
    void shouldBridgeToJulLevels() {
        Logger logger = Logger.getLogger("test-jul-handler");
        logger.setUseParentHandlers(false);
        CapturingHandler capturingHandler = new CapturingHandler();
        logger.addHandler(capturingHandler);
        logger.setLevel(Level.ALL);

        JULMessageHandler handler = new JULMessageHandler(logger);
        handler.setDebugEnabled(true);
        handler.setTraceEnabled(true);
        handler.info("info");
        assertEquals(Level.INFO, capturingHandler.last.getLevel());
        assertEquals("info", capturingHandler.last.getMessage());

        handler.warn("warn");
        assertEquals(Level.WARNING, capturingHandler.last.getLevel());

        handler.error("error");
        assertEquals(Level.SEVERE, capturingHandler.last.getLevel());

        handler.fatal("fatal");
        assertEquals(Level.SEVERE, capturingHandler.last.getLevel());

        handler.debug("debug");
        assertEquals(Level.FINER, capturingHandler.last.getLevel());

        handler.trace("trace");
        assertEquals(Level.FINEST, capturingHandler.last.getLevel());

        handler.exception(new RuntimeException("boom"));
        assertEquals(Level.SEVERE, capturingHandler.last.getLevel());
        assertNotNull(capturingHandler.last.getThrown());
    }

    @Test
    @DisplayName("Should honor enabled flags")
    void shouldHonorEnabledFlags() {
        Logger logger = Logger.getLogger("test-jul-handler-flags");
        logger.setUseParentHandlers(false);
        CapturingHandler capturingHandler = new CapturingHandler();
        logger.addHandler(capturingHandler);
        logger.setLevel(Level.ALL);

        JULMessageHandler handler = new JULMessageHandler(logger);
        handler.setInfoEnabled(false);
        handler.setWarnEnabled(false);
        handler.setErrorEnabled(false);
        handler.setFatalEnabled(false);
        handler.setDebugEnabled(false);
        handler.setTraceEnabled(false);

        handler.info("info");
        handler.warn("warn");
        handler.error("error");
        handler.fatal("fatal");
        handler.debug("debug");
        handler.trace("trace");
        handler.exception(new RuntimeException("boom"));

        assertNull(capturingHandler.last, "No messages should be published when disabled");
    }

    @Test
    @DisplayName("Should log exception with custom message")
    void shouldLogExceptionWithCustomMessage() {
        Logger logger = Logger.getLogger("test-jul-handler-custom-message");
        logger.setUseParentHandlers(false);
        CapturingHandler capturingHandler = new CapturingHandler();
        logger.addHandler(capturingHandler);
        logger.setLevel(Level.ALL);

        JULMessageHandler handler = new JULMessageHandler(logger);
        RuntimeException exception = new RuntimeException("boom");

        handler.exception("prefix", exception);

        assertEquals(Level.SEVERE, capturingHandler.last.getLevel());
        assertEquals("prefix: boom", capturingHandler.last.getMessage());
        assertEquals(exception, capturingHandler.last.getThrown());
    }

    @Test
    @DisplayName("Unknown severity should fallback to INFO level")
    void unknownSeverityShouldFallbackToInfoLevel() {
        Logger logger = Logger.getLogger("test-jul-handler-unknown-severity");
        logger.setUseParentHandlers(false);
        CapturingHandler capturingHandler = new CapturingHandler();
        logger.addHandler(capturingHandler);
        logger.setLevel(Level.ALL);

        JULMessageHandler handler = new JULMessageHandler(logger);
        handler.handleMessage(com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber.UNSPECIFIED, "payload");

        assertEquals(Level.INFO, capturingHandler.last.getLevel());
        assertTrue(capturingHandler.last.getMessage().contains("Unknown severity level"));
        assertTrue(capturingHandler.last.getMessage().contains("payload"));
    }

    @Test
    @DisplayName("Should use provided factory and formatter when both are configured")
    void shouldUseProvidedFactoryAndFormatterWhenBothAreConfigured() {
        Logger logger = Logger.getLogger("test-jul-handler-formatted");
        logger.setUseParentHandlers(false);
        CapturingHandler capturingHandler = new CapturingHandler();
        logger.addHandler(capturingHandler);
        logger.setLevel(Level.ALL);

        LogRecordFactory factory = mock(LogRecordFactory.class);
        LogRecordFormatter formatter = mock(LogRecordFormatter.class);
        RuntimeException boom = new RuntimeException("boom");

        when(formatter.format(org.mockito.ArgumentMatchers.any(
                com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord.class)))
                .thenReturn("formatted-info", "formatted-prefix");

        JULMessageHandler handler = new JULMessageHandler(logger, factory, formatter);

        handler.info("plain-info");
        assertEquals("formatted-info", capturingHandler.last.getMessage());

        handler.exception("prefix", boom);
        assertEquals("formatted-prefix: boom", capturingHandler.last.getMessage());
        verifyNoInteractions(factory);
    }

    @Test
    @DisplayName("Factory-only configuration should fall back to raw messages")
    void factoryOnlyConfigurationShouldFallBackToRawMessages() {
        Logger logger = Logger.getLogger("test-jul-handler-factory-only");
        logger.setUseParentHandlers(false);
        CapturingHandler capturingHandler = new CapturingHandler();
        logger.addHandler(capturingHandler);
        logger.setLevel(Level.ALL);

        LogRecordFactory factory = mock(LogRecordFactory.class);
        JULMessageHandler handler = new JULMessageHandler(logger, factory, null);

        handler.info("raw-info");
        assertEquals("raw-info", capturingHandler.last.getMessage());

        RuntimeException boom = new RuntimeException("boom");
        handler.exception("raw-prefix", boom);
        assertEquals("raw-prefix: boom", capturingHandler.last.getMessage());
    }
}
