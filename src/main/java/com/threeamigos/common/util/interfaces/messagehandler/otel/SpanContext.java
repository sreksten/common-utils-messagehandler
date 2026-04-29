package com.threeamigos.common.util.interfaces.messagehandler.otel;

import java.util.List;

/**
 * A SpanContext represents the portion of a Span which must be serialized and propagated alongside a distributed
 *  context. SpanContexts are immutable.
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
     * @return the trace state as a list of KeyValue pairs
     */
    List<KeyValue> getTraceState();
}
