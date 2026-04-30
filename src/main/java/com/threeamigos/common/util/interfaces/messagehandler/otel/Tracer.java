package com.threeamigos.common.util.interfaces.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.VoidMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Filter;

import java.io.File;

/**
 * OpenTelemetry-like tracer API.
 * <p>
 * Specification references:
 * <ul>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/trace/api/">OpenTelemetry Trace API</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/trace/api/#tracer">OpenTelemetry Trace API: Tracer</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/trace/api/#span-operations">OpenTelemetry Trace API:
 *   Span operations</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/trace/api/#enabled">OpenTelemetry Trace API: Enabled</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/trace/api/#concurrency-requirements">OpenTelemetry
 *   Trace API: Concurrency requirements</a></li>
 * </ul>
 * Implementations are expected to be safe for concurrent use by default.
 *
 * @author Stefano Reksten
 */
public interface Tracer {

    /**
     * Creates and starts a new span with the given name.
     * <p>
     * If no explicit parent is provided, the implementation may start a root span.
     *
     * @param name span name
     * @return a started span
     */
    Span createSpan(String name);

    /**
     * Creates and starts a new span with an explicit parent span context.
     * <p>
     * If the parent is null or invalid, the implementation may start a root span.
     *
     * @param name span name
     * @param parentSpanContext explicit parent span context
     * @return a started span
     */
    Span createSpan(String name, SpanContext parentSpanContext);

    /**
     * Returns the effective instrumentation scope for this tracer.
     * <p>
     * Implementations may derive this value from a provider and/or tracer-local configuration.
     *
     * @return effective instrumentation scope, or {@code null} when unavailable
     */
    default InstrumentationScope getInstrumentationScope() {
        return null;
    }

    default MessageHandler getConsoleMessageHandler() {
        return new VoidMessageHandler();
    }

    default MessageHandler getConsoleMessageHandler(final Filter filter) {
        return getConsoleMessageHandler();
    }

    default MessageHandler getFileMessageHandler(final String filePath) {
        return new VoidMessageHandler();
    }

    default MessageHandler getFileMessageHandler(final String filePath,
                                                 final Filter filter) {
        return getFileMessageHandler(filePath);
    }

    default MessageHandler getFileMessageHandler(final File file) {
        return new VoidMessageHandler();
    }

    default MessageHandler getFileMessageHandler(final File file,
                                                 final Filter filter) {
        return getFileMessageHandler(file);
    }

    default MessageHandler getInMemoryMessageHandler() {
        return new VoidMessageHandler();
    }

    default MessageHandler getInMemoryMessageHandler(final Filter filter) {
        return getInMemoryMessageHandler();
    }

    default MessageHandler getJULMessageHandler(final java.util.logging.Logger logger) {
        return new VoidMessageHandler();
    }

    default MessageHandler getJULMessageHandler(final java.util.logging.Logger logger,
                                                final Filter filter) {
        return getJULMessageHandler(logger);
    }

    default MessageHandler getLog4JMessageHandler(final org.apache.logging.log4j.Logger logger) {
        return new VoidMessageHandler();
    }

    default MessageHandler getLog4JMessageHandler(final org.apache.logging.log4j.Logger logger,
                                                  final Filter filter) {
        return getLog4JMessageHandler(logger);
    }

    default MessageHandler getSLF4JMessageHandler(final org.slf4j.Logger logger) {
        return new VoidMessageHandler();
    }

    default MessageHandler getSLF4JMessageHandler(final org.slf4j.Logger logger,
                                                  final Filter filter) {
        return getSLF4JMessageHandler(logger);
    }

    default MessageHandler getSwingMessageHandler() {
        return new VoidMessageHandler();
    }

    default MessageHandler getSwingMessageHandler(final Filter filter) {
        return getSwingMessageHandler();
    }

    default MessageHandler getVoidMessageHandler() {
        return new VoidMessageHandler();
    }

    /**
     * Returns whether this tracer is currently enabled for span creation.
     * <p>
     * Callers should not cache this value because it may change over time.
     *
     * @return {@code true} when tracing is enabled for this tracer
     */
    boolean isEnabled();
}
