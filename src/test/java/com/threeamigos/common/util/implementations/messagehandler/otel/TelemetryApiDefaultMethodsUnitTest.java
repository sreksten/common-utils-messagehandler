package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.filters.FilterByClassName;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Telemetry API default methods")
@Tag("unit")
@Tag("messageHandler")
class TelemetryApiDefaultMethodsUnitTest {

    @Test
    @DisplayName("tracer default handler methods should return void handlers")
    void tracerDefaultHandlerMethodsShouldReturnVoidHandlers() {
        Tracer tracer = new Tracer() {
            @Override
            public Span createSpan(final String name) {
                return Span.wrap(null);
            }

            @Override
            public Span createSpan(final String name, final SpanContext parentSpanContext) {
                return Span.wrap(null);
            }

            @Override
            public boolean isEnabled() {
                return true;
            }
        };

        MessageHandler console = tracer.getConsoleMessageHandler();
        MessageHandler consoleWithFilter = tracer.getConsoleMessageHandler(new FilterByClassName());
        MessageHandler fileByString = tracer.getFileMessageHandler("x.log");
        MessageHandler fileByStringWithFilter = tracer.getFileMessageHandler("x.log", new FilterByClassName());
        MessageHandler fileByFile = tracer.getFileMessageHandler(new File("x.log"));
        MessageHandler fileByFileWithFilter = tracer.getFileMessageHandler(new File("x.log"), new FilterByClassName());
        MessageHandler inMemory = tracer.getInMemoryMessageHandler();
        MessageHandler inMemoryWithFilter = tracer.getInMemoryMessageHandler(new FilterByClassName());
        MessageHandler jul = tracer.getJULMessageHandler(java.util.logging.Logger.getLogger("default"));
        MessageHandler julWithFilter = tracer.getJULMessageHandler(java.util.logging.Logger.getLogger("default"), new FilterByClassName());
        MessageHandler log4j = tracer.getLog4JMessageHandler(org.apache.logging.log4j.LogManager.getLogger("default"));
        MessageHandler log4jWithFilter = tracer.getLog4JMessageHandler(org.apache.logging.log4j.LogManager.getLogger("default"), new FilterByClassName());
        MessageHandler slf4j = tracer.getSLF4JMessageHandler(org.slf4j.LoggerFactory.getLogger("default"));
        MessageHandler slf4jWithFilter = tracer.getSLF4JMessageHandler(org.slf4j.LoggerFactory.getLogger("default"), new FilterByClassName());
        MessageHandler swing = tracer.getSwingMessageHandler();
        MessageHandler swingWithFilter = tracer.getSwingMessageHandler(new FilterByClassName());
        MessageHandler voidHandler = tracer.getVoidMessageHandler();
        LogRecordFactory logRecordFactory = tracer.getLogRecordFactory();
        tracer.getInstrumentationScope();

        assertVoid(console);
        assertVoid(consoleWithFilter);
        assertVoid(fileByString);
        assertVoid(fileByStringWithFilter);
        assertVoid(fileByFile);
        assertVoid(fileByFileWithFilter);
        assertVoid(inMemory);
        assertVoid(inMemoryWithFilter);
        assertVoid(jul);
        assertVoid(julWithFilter);
        assertVoid(log4j);
        assertVoid(log4jWithFilter);
        assertVoid(slf4j);
        assertVoid(slf4jWithFilter);
        assertVoid(swing);
        assertVoid(swingWithFilter);
        assertVoid(voidHandler);
        assertTrue(logRecordFactory instanceof LogRecordFactoryImpl);
    }

    private static void assertVoid(final MessageHandler handler) {
        assertTrue(handler instanceof com.threeamigos.common.util.implementations.messagehandler.VoidMessageHandler);
    }
}
