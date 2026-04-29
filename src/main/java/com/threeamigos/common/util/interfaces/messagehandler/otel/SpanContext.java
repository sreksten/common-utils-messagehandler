package com.threeamigos.common.util.interfaces.messagehandler.otel;

/**
 * A SpanContext represents the portion of a Span which must be serialized and propagated alongside a distributed
 * context. SpanContexts are immutable.
 * <p>
 * Specification references:
 * <ul>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/trace/api/#spancontext">OpenTelemetry Trace API:
 *   SpanContext</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/trace/api/#retrieving-the-traceid-and-spanid">
 *   OpenTelemetry Trace API: Retrieving the TraceId and SpanId</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/trace/api/#tracestate">OpenTelemetry Trace API:
 *   TraceState</a></li>
 *   <li><a href="https://www.w3.org/TR/trace-context-2/">W3C Trace Context Level 2</a>:
 *   <a href="https://www.w3.org/TR/trace-context-2/#trace-id">trace-id</a>,
 *   <a href="https://www.w3.org/TR/trace-context-2/#parent-id">parent-id</a>,
 *   <a href="https://www.w3.org/TR/trace-context-2/#trace-flags">trace-flags</a>,
 *   <a href="https://www.w3.org/TR/trace-context-2/#tracestate-header">tracestate</a></li>
 * </ul>
 *
 * @author Stefano Reksten
 */
public interface SpanContext {

    /**
     * String representation of the traceId as a 32-hex-character lowercase string
     *
     * @return the traceId
     */
    String getTraceId();

    /**
     * A valid trace identifier is a 16-byte array with at least one non-zero byte.
     *
     * @return the traceId
     */
    byte[] getTraceIdBytes();

    /**
     * String representation of the spanID as a 16-hex-character lowercase string
     *
     * @return the spanId
     */
    String getSpanId();

    /**
     * A valid span identifier is an 8-byte array with at least one non-zero byte.
     *
     * @return the spanId
     */
    byte[] getSpanIdBytes();

    /**
     * Returns the raw trace flags byte.
     * <p>
     * Bit 0 is sampled and bit 1 is random.
     *
     * @return the trace flags byte
     */
    byte getTraceFlags();

    boolean isSampled();

    boolean isRandom();

    /**
     * a boolean value, which is true if the SpanContext has a non-zero TraceID and a non-zero SpanID.
     *
     * @return true if valid
     */
    boolean isValid();

    /**
     * A boolean indicating whether the SpanContext was received from somewhere else or locally generated.
     *
     * @return true if remotely generated
     */
    boolean isRemote();

    /**
     * Returns the trace state associated with this SpanContext.
     *
     * @return the trace state
     */
    TraceState getTraceState();
}
