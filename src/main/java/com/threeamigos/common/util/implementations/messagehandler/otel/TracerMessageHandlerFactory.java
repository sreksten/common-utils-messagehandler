package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.GrafanaMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.JaegerMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.JULMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.Log4JMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.SLF4JMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.SwingMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.VoidMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ExportLogsServiceRequestLogRecordFormatter;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.RawJsonRecordFormatter;
import com.threeamigos.common.util.implementations.messagehandler.utils.GrafanaLogRecordDispatcher;
import com.threeamigos.common.util.implementations.messagehandler.utils.JaegerLogRecordDispatcher;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * Internal factory used by {@link TracerImpl} to build message handlers bound to tracer enrichment.
 *
 * @author Stefano Reksten
 */
final class TracerMessageHandlerFactory {

    private final LogRecordFactory logRecordFactory;
    private final String defaultFilePath;
    private final LogRecordFormatter formatter = new RawJsonRecordFormatter();
    private final LogRecordFormatter exportLogsFormatter = new ExportLogsServiceRequestLogRecordFormatter();

    TracerMessageHandlerFactory(final LogRecordFactory logRecordFactory,
                                final String defaultFilePath) {
        this.logRecordFactory = logRecordFactory;
        this.defaultFilePath = defaultFilePath;
    }

    ConsoleMessageHandler createConsole() {
        return new ConsoleMessageHandler(logRecordFactory, formatter);
    }

    FileMessageHandler createFile(final String filePath) {
        return new FileMessageHandler(logRecordFactory, formatter, filePath);
    }

    InMemoryMessageHandler createInMemory() {
        return new InMemoryMessageHandler(logRecordFactory, formatter);
    }

    JULMessageHandler createJUL(final String loggerName) {
        return new JULMessageHandler(loggerName, logRecordFactory, formatter);
    }

    JULMessageHandler createJUL(final java.util.logging.Logger logger) {
        return new JULMessageHandler(logger, logRecordFactory, formatter);
    }

    Log4JMessageHandler createLog4J(final String loggerName) {
        return new Log4JMessageHandler(loggerName, logRecordFactory, formatter);
    }

    Log4JMessageHandler createLog4J(final Logger logger) {
        return new Log4JMessageHandler(logger, logRecordFactory, formatter);
    }

    SLF4JMessageHandler createSLF4J(final String loggerName) {
        return new SLF4JMessageHandler(loggerName, logRecordFactory, formatter);
    }

    SLF4JMessageHandler createSLF4J(final org.slf4j.Logger logger) {
        return new SLF4JMessageHandler(logger, logRecordFactory, formatter);
    }

    SwingMessageHandler createSwing() {
        return new SwingMessageHandler(logRecordFactory, formatter);
    }

    JaegerMessageHandler createJaeger(final String endpointUrl) {
        return new JaegerMessageHandler(
                logRecordFactory,
                exportLogsFormatter,
                new JaegerLogRecordDispatcher(endpointUrl),
                false,
                0,
                false);
    }

    JaegerMessageHandler createJaeger(final String endpointUrl,
                                      final String username,
                                      final String password) {
        return new JaegerMessageHandler(
                logRecordFactory,
                exportLogsFormatter,
                new JaegerLogRecordDispatcher(endpointUrl, username, password),
                false,
                0,
                false);
    }

    JaegerMessageHandler createJaeger(final String endpointUrl,
                                      final String username,
                                      final String password,
                                      final String bearerToken,
                                      final int connectTimeoutMillis,
                                      final int readTimeoutMillis,
                                      final Map<String, String> additionalHeaders,
                                      final boolean async,
                                      final int queueCapacity,
                                      final boolean registerShutdownHook) {
        return new JaegerMessageHandler(
                logRecordFactory,
                exportLogsFormatter,
                new JaegerLogRecordDispatcher(
                        endpointUrl,
                        username,
                        password,
                        bearerToken,
                        connectTimeoutMillis,
                        readTimeoutMillis,
                        additionalHeaders),
                async,
                queueCapacity,
                registerShutdownHook);
    }

    GrafanaMessageHandler createGrafana(final String endpointUrl) {
        return new GrafanaMessageHandler(
                logRecordFactory,
                exportLogsFormatter,
                new GrafanaLogRecordDispatcher(endpointUrl),
                false,
                0,
                false);
    }

    GrafanaMessageHandler createGrafana(final String endpointUrl,
                                        final String username,
                                        final String password) {
        return new GrafanaMessageHandler(
                logRecordFactory,
                exportLogsFormatter,
                new GrafanaLogRecordDispatcher(endpointUrl, username, password),
                false,
                0,
                false);
    }

    GrafanaMessageHandler createGrafana(final String endpointUrl,
                                        final String username,
                                        final String password,
                                        final String bearerToken,
                                        final int connectTimeoutMillis,
                                        final int readTimeoutMillis,
                                        final Map<String, String> additionalHeaders,
                                        final boolean async,
                                        final int queueCapacity,
                                        final boolean registerShutdownHook) {
        return new GrafanaMessageHandler(
                logRecordFactory,
                exportLogsFormatter,
                new GrafanaLogRecordDispatcher(
                        endpointUrl,
                        username,
                        password,
                        bearerToken,
                        connectTimeoutMillis,
                        readTimeoutMillis,
                        additionalHeaders),
                async,
                queueCapacity,
                registerShutdownHook);
    }

    VoidMessageHandler createVoid() {
        return new VoidMessageHandler();
    }
}
