package com.threeamigos.common.util.implementations.messagehandler;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Log4JMessageHandler unit tests")
@Tag("unit")
@Tag("messageHandler")
class Log4JMessageHandlerUnitTest {

    @Test
    @DisplayName("Should throw on null logger name")
    void shouldThrowOnNullLoggerName() {
        assertThrows(NullPointerException.class, () -> new Log4JMessageHandler((String) null));
        assertThrows(IllegalArgumentException.class, () -> new Log4JMessageHandler(" "));
    }

    @Test
    @DisplayName("Should throw on null Logger instance")
    void shouldThrowOnNullLoggerInstance() {
        assertThrows(NullPointerException.class, () -> new Log4JMessageHandler((Logger) null));
    }

    @Test
    @DisplayName("Should create logger by name")
    void shouldCreateLoggerByName() {
        assertNotNull(new Log4JMessageHandler("log4j-handler-name"));
    }

    @Test
    @DisplayName("Should bridge to Log4j 2 levels")
    void shouldBridgeToLog4JLevels() {
        Logger logger = mock(Logger.class);
        Log4JMessageHandler handler = new Log4JMessageHandler(logger);

        handler.handleInfoMessage("info");
        verify(logger).info("info");

        handler.handleWarnMessage("warn");
        verify(logger).warn("warn");

        handler.handleErrorMessage("error");
        verify(logger).error("error");

        handler.handleDebugMessage("debug");
        verify(logger).debug("debug");

        handler.handleTraceMessage("trace");
        verify(logger).trace("trace");

        RuntimeException exception = new RuntimeException("boom");
        handler.handleException(exception);
        verify(logger).error("boom", (Throwable) exception);
    }

    @Test
    @DisplayName("Should honor enabled flags")
    void shouldHonorEnabledFlags() {
        Logger logger = mock(Logger.class);
        Log4JMessageHandler handler = new Log4JMessageHandler(logger);

        handler.setInfoEnabled(false);
        handler.setWarnEnabled(false);
        handler.setErrorEnabled(false);
        handler.setDebugEnabled(false);
        handler.setTraceEnabled(false);
        handler.setExceptionEnabled(false);

        handler.handleInfoMessage("info");
        handler.handleWarnMessage("warn");
        handler.handleErrorMessage("error");
        handler.handleDebugMessage("debug");
        handler.handleTraceMessage("trace");
        handler.handleException(new RuntimeException("boom"));

        verifyNoInteractions(logger);
    }

    @Test
    @DisplayName("Should log exception with custom message")
    void shouldLogExceptionWithCustomMessage() {
        Logger logger = mock(Logger.class);
        Log4JMessageHandler handler = new Log4JMessageHandler(logger);
        RuntimeException exception = new RuntimeException("boom");

        handler.handleException("prefix", exception);

        verify(logger).error("prefix", (Throwable) exception);
    }
}
