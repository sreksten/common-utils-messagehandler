package com.threeamigos.common.util.interfaces.messagehandler.otel;

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
     * Returns whether this tracer is currently enabled for span creation.
     * <p>
     * Callers should not cache this value because it may change over time.
     *
     * @return {@code true} when tracing is enabled for this tracer
     */
    boolean isEnabled();
}
