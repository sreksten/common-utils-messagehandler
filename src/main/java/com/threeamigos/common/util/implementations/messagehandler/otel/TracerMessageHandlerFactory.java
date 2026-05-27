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
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * Internal factory used by {@link TracerImpl} to build message handlers bound to tracer enrichment.
 *
 * @author Stefano Reksten
 */
public final class TracerMessageHandlerFactory {

    private final Tracer tracer;
    private final LogRecordFormatter formatter = new RawJsonRecordFormatter();
    private final LogRecordFormatter exportLogsFormatter = new ExportLogsServiceRequestLogRecordFormatter();

    TracerMessageHandlerFactory(final Tracer tracer) {
        this.tracer = tracer;
    }

    ConsoleMessageHandler createConsole() {
        return bind(new ConsoleMessageHandler(new LogRecordFactoryImpl(), formatter));
    }

    ConsoleMessageHandler createConsole(final LogRecordFormatter logRecordFormatter) {
        return bind(new ConsoleMessageHandler(new LogRecordFactoryImpl(), logRecordFormatter));
    }

    FileMessageHandler createFile(final String filePath) {
        return bind(new FileMessageHandler(new LogRecordFactoryImpl(), formatter, filePath,
                false, 0, false, true));
    }

    FileMessageHandler createFile(final String filePath,
                                  final LogRecordFormatter logRecordFormatter) {
        return bind(new FileMessageHandler(new LogRecordFactoryImpl(), logRecordFormatter, filePath,
                false, 0, false, true));
    }

    InMemoryMessageHandler createInMemory() {
        return bind(new InMemoryMessageHandler(new LogRecordFactoryImpl(), formatter));
    }

    JULMessageHandler createJUL(final String loggerName) {
        return bind(new JULMessageHandler(loggerName, new LogRecordFactoryImpl(), formatter));
    }

    JULMessageHandler createJUL(final java.util.logging.Logger logger) {
        return bind(new JULMessageHandler(logger, new LogRecordFactoryImpl(), formatter));
    }

    Log4JMessageHandler createLog4J(final String loggerName) {
        return bind(new Log4JMessageHandler(loggerName, new LogRecordFactoryImpl(), formatter));
    }

    Log4JMessageHandler createLog4J(final Logger logger) {
        return bind(new Log4JMessageHandler(logger, new LogRecordFactoryImpl(), formatter));
    }

    SLF4JMessageHandler createSLF4J(final String loggerName) {
        return bind(new SLF4JMessageHandler(loggerName, new LogRecordFactoryImpl(), formatter));
    }

    SLF4JMessageHandler createSLF4J(final org.slf4j.Logger logger) {
        return bind(new SLF4JMessageHandler(logger, new LogRecordFactoryImpl(), formatter));
    }

    SwingMessageHandler createSwing() {
        return bind(new SwingMessageHandler(new LogRecordFactoryImpl(), formatter));
    }

    JaegerMessageHandler createJaeger(final String endpointUrl) {
        return bind(new JaegerMessageHandler(
                new LogRecordFactoryImpl(),
                exportLogsFormatter,
                new JaegerLogRecordDispatcher(endpointUrl),
                false,
                0,
                false));
    }

    JaegerMessageHandler createJaeger(final String endpointUrl,
                                      final String username,
                                      final String password) {
        return bind(new JaegerMessageHandler(
                new LogRecordFactoryImpl(),
                exportLogsFormatter,
                new JaegerLogRecordDispatcher(endpointUrl, username, password),
                false,
                0,
                false));
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
        return bind(new JaegerMessageHandler(
                new LogRecordFactoryImpl(),
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
                registerShutdownHook));
    }

    GrafanaMessageHandler createGrafana(final String endpointUrl) {
        return bind(new GrafanaMessageHandler(
                new LogRecordFactoryImpl(),
                exportLogsFormatter,
                new GrafanaLogRecordDispatcher(endpointUrl),
                false,
                0,
                false));
    }

    GrafanaMessageHandler createGrafana(final String endpointUrl,
                                        final String username,
                                        final String password) {
        return bind(new GrafanaMessageHandler(
                new LogRecordFactoryImpl(),
                exportLogsFormatter,
                new GrafanaLogRecordDispatcher(endpointUrl, username, password),
                false,
                0,
                false));
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
        return bind(new GrafanaMessageHandler(
                new LogRecordFactoryImpl(),
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
                registerShutdownHook));
    }

    VoidMessageHandler createVoid() {
        return bind(new VoidMessageHandler());
    }

    private <T extends com.threeamigos.common.util.interfaces.messagehandler.MessageHandler> T bind(final T handler) {
        return HandlerTracerBinder.bindTracer(handler, tracer);
    }
}
