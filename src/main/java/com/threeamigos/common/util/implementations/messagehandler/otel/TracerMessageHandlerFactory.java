package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.JULMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.Log4JMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.SLF4JMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.SwingMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.VoidMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.RawJsonRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import org.apache.logging.log4j.Logger;

/**
 * Internal factory used by {@link TracerImpl} to build message handlers bound to tracer enrichment.
 *
 * @author Stefano Reksten
 */
final class TracerMessageHandlerFactory {

    private final LogRecordFactory logRecordFactory;
    private final String defaultFilePath;
    private final LogRecordFormatter formatter = new RawJsonRecordFormatter();

    TracerMessageHandlerFactory(final LogRecordFactory logRecordFactory,
                                final String defaultFilePath) {
        this.logRecordFactory = logRecordFactory;
        this.defaultFilePath = defaultFilePath;
    }

    MessageHandler createConsole() {
        return new ConsoleMessageHandler(logRecordFactory, formatter);
    }

    MessageHandler createFile(final String filePath) {
        return new FileMessageHandler(logRecordFactory, formatter, filePath);
    }

    MessageHandler createInMemory() {
        return new StructuredBackendMessageHandler(new InMemoryMessageHandler(), logRecordFactory, formatter);
    }

    MessageHandler createJUL(final String loggerName) {
        return new StructuredBackendMessageHandler(new JULMessageHandler(loggerName), logRecordFactory, formatter);
    }

    MessageHandler createJUL(final java.util.logging.Logger logger) {
        return new StructuredBackendMessageHandler(new JULMessageHandler(logger), logRecordFactory, formatter);
    }

    MessageHandler createLog4J(final String loggerName) {
        return new StructuredBackendMessageHandler(new Log4JMessageHandler(loggerName), logRecordFactory, formatter);
    }

    MessageHandler createLog4J(final Logger logger) {
        return new StructuredBackendMessageHandler(new Log4JMessageHandler(logger), logRecordFactory, formatter);
    }

    MessageHandler createSLF4J(final String loggerName) {
        return new StructuredBackendMessageHandler(new SLF4JMessageHandler(loggerName), logRecordFactory, formatter);
    }

    MessageHandler createSLF4J(final org.slf4j.Logger logger) {
        return new StructuredBackendMessageHandler(new SLF4JMessageHandler(logger), logRecordFactory, formatter);
    }

    MessageHandler createSwing() {
        return new StructuredBackendMessageHandler(new SwingMessageHandler(), logRecordFactory, formatter);
    }

    MessageHandler createVoid() {
        return new VoidMessageHandler();
    }
}
