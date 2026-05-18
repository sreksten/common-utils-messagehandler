package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
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
        handler.setDebugEnabled(true);
        handler.setTraceEnabled(true);

        handler.info("info");
        verify(logger).info("info");

        handler.warn("warn");
        verify(logger).warn("warn");

        handler.error("error");
        verify(logger).error("error");

        handler.fatal("fatal");
        verify(logger).fatal("fatal");

        handler.debug("debug");
        verify(logger).debug("debug");

        handler.trace("trace");
        verify(logger).trace("trace");

        RuntimeException exception = new RuntimeException("boom");
        handler.exception(exception);
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

        verifyNoInteractions(logger);
    }

    @Test
    @DisplayName("Should log exception with custom message")
    void shouldLogExceptionWithCustomMessage() {
        Logger logger = mock(Logger.class);
        Log4JMessageHandler handler = new Log4JMessageHandler(logger);
        RuntimeException exception = new RuntimeException("boom");

        handler.exception("prefix", exception);

        verify(logger).error("prefix", (Throwable) exception);
    }

    @Test
    @DisplayName("Unknown severity should fallback to INFO message")
    void unknownSeverityShouldFallbackToInfoMessage() {
        Logger logger = mock(Logger.class);
        Log4JMessageHandler handler = new Log4JMessageHandler(logger);

        handler.handleMessage(com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber.UNSPECIFIED, "payload");

        verify(logger).info("Unknown severity level: {}. Logging as INFO. {}",
                com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber.UNSPECIFIED, "payload");
    }

    @Test
    @DisplayName("Empty throwable message should fallback to throwable detail")
    void emptyThrowableMessageShouldFallbackToDetail() {
        Logger logger = mock(Logger.class);
        Log4JMessageHandler handler = new Log4JMessageHandler(logger);
        RuntimeException exception = new RuntimeException("boom");

        handler.exception("", exception);

        verify(logger).error("boom", (Throwable) exception);
    }

    @Test
    @DisplayName("Should use provided formatter when factory and formatter are configured")
    void shouldUseProvidedFormatterWhenFactoryAndFormatterAreConfigured() {
        Logger logger = mock(Logger.class);
        LogRecordFactory factory = mock(LogRecordFactory.class);
        LogRecordFormatter formatter = mock(LogRecordFormatter.class);
        com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord infoRecord =
                mock(com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord.class);
        com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord exceptionRecord =
                mock(com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord.class);
        RuntimeException boom = new RuntimeException("boom");

        when(factory.create(SeverityNumber.INFO, "plain-info")).thenReturn(infoRecord);
        when(formatter.format(infoRecord)).thenReturn("formatted-info");
        when(factory.create("prefix", boom)).thenReturn(exceptionRecord);
        when(formatter.format(exceptionRecord)).thenReturn("formatted-prefix");

        Log4JMessageHandler handler = new Log4JMessageHandler(logger, factory, formatter);
        handler.info("plain-info");
        handler.exception("prefix", boom);

        verify(factory).create(SeverityNumber.INFO, "plain-info");
        verify(factory).create("prefix", boom);
        verify(formatter).format(infoRecord);
        verify(formatter).format(exceptionRecord);
        verify(logger).info("formatted-info");
        verify(logger).error("formatted-prefix", (Throwable) boom);
    }

    @Test
    @DisplayName("Factory-only configuration should keep fallback formatting behavior")
    void factoryOnlyConfigurationShouldKeepFallbackFormattingBehavior() {
        Logger logger = mock(Logger.class);
        LogRecordFactory factory = mock(LogRecordFactory.class);
        Log4JMessageHandler handler = new Log4JMessageHandler(logger, factory, null);
        RuntimeException boom = new RuntimeException("boom");

        handler.info("plain-info");
        handler.exception("", boom);

        verify(logger).info("plain-info");
        verify(logger).error("boom", (Throwable) boom);
        verifyNoInteractions(factory);
    }
}
