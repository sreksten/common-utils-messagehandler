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

    private final LogRecordFactory enrichingFactory;
    private final String defaultFilePath;
    private final LogRecordFormatter formatter = new RawJsonRecordFormatter();

    TracerMessageHandlerFactory(final LogRecordFactory enrichingFactory,
                                final String defaultFilePath) {
        this.enrichingFactory = enrichingFactory;
        this.defaultFilePath = defaultFilePath;
    }

    MessageHandler createConsole() {
        return new ConsoleMessageHandler(enrichingFactory, formatter);
    }

    MessageHandler createFile(final String filePath) {
        return new FileMessageHandler(enrichingFactory, formatter, filePath);
    }

    MessageHandler createInMemory() {
        return new StructuredBackendMessageHandler(new InMemoryMessageHandler(), enrichingFactory, formatter);
    }

    MessageHandler createJUL(final String loggerName) {
        return new StructuredBackendMessageHandler(new JULMessageHandler(loggerName), enrichingFactory, formatter);
    }

    MessageHandler createJUL(final java.util.logging.Logger logger) {
        return new StructuredBackendMessageHandler(new JULMessageHandler(logger), enrichingFactory, formatter);
    }

    MessageHandler createLog4J(final String loggerName) {
        return new StructuredBackendMessageHandler(new Log4JMessageHandler(loggerName), enrichingFactory, formatter);
    }

    MessageHandler createLog4J(final Logger logger) {
        return new StructuredBackendMessageHandler(new Log4JMessageHandler(logger), enrichingFactory, formatter);
    }

    MessageHandler createSLF4J(final String loggerName) {
        return new StructuredBackendMessageHandler(new SLF4JMessageHandler(loggerName), enrichingFactory, formatter);
    }

    MessageHandler createSLF4J(final org.slf4j.Logger logger) {
        return new StructuredBackendMessageHandler(new SLF4JMessageHandler(logger), enrichingFactory, formatter);
    }

    MessageHandler createSwing() {
        return new StructuredBackendMessageHandler(new SwingMessageHandler(), enrichingFactory, formatter);
    }

    MessageHandler createVoid() {
        return new VoidMessageHandler();
    }
}
