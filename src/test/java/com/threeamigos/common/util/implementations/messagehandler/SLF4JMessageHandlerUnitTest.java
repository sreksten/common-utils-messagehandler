package com.threeamigos.common.util.implementations.messagehandler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("SLF4JMessageHandler unit tests")
@Tag("unit")
@Tag("messageHandler")
class SLF4JMessageHandlerUnitTest {

    @Test
    @DisplayName("Should throw on null logger name")
    void shouldThrowOnNullLoggerName() {
        assertThrows(NullPointerException.class, () -> new SLF4JMessageHandler((String) null));
        assertThrows(IllegalArgumentException.class, () -> new SLF4JMessageHandler(" "));
    }

    @Test
    @DisplayName("Should throw on null Logger instance")
    void shouldThrowOnNullLoggerInstance() {
        assertThrows(NullPointerException.class, () -> new SLF4JMessageHandler((Logger) null));
    }

    @Test
    @DisplayName("Should create logger by name")
    void shouldCreateLoggerByName() {
        assertNotNull(new SLF4JMessageHandler("slf4j-handler-name"));
    }

    @Test
    @DisplayName("Should bridge to SLF4J levels")
    void shouldBridgeToSLF4JLevels() {
        Logger logger = mock(Logger.class);
        SLF4JMessageHandler handler = new SLF4JMessageHandler(logger);

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
        SLF4JMessageHandler handler = new SLF4JMessageHandler(logger);

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
        SLF4JMessageHandler handler = new SLF4JMessageHandler(logger);
        RuntimeException exception = new RuntimeException("boom");

        handler.handleException("prefix", exception);

        verify(logger).error("prefix: boom", (Throwable) exception);
    }
}
