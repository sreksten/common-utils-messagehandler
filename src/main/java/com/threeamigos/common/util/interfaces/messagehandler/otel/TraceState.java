package com.threeamigos.common.util.interfaces.messagehandler.otel;

import java.util.Collection;

/**
 * TraceState is an immutable section of SpanContext, represented by a list of string key-value pairs.
 * <p>
 * Specification references:
 * <ul>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/trace/api/#tracestate">OpenTelemetry Trace API:
 *   TraceState</a></li>
 *   <li><a href="https://www.w3.org/TR/trace-context/#tracestate-header">W3C Trace Context: tracestate</a></li>
 * </ul>
 * TraceState provides methods to retrieve, set, update, and delete key-value pairs that will return a new TraceState
 * instance. It should be prepared at the very beginning of the unit of work.
 *
 * @author Stefano Reksten
 */
public interface TraceState {

    /**
     * Returns the value associated with the given key.
     * @param key The key to retrieve the value for.
     * @return The value associated with the key, or null if the key is not found.
     */
    AnyValue get(String key);

    /**
     * Sets or updates the given key-value pair
     * @param key the key to set or update
     * @param value the value to associate with the key
     * @return a new TraceState instance with the updated key-value pair
     */
    TraceState set(String key, AnyValue value);

    /**
     * Deletes the key-value pair associated with the given key
     * @param key the key to delete
     * @return a new TraceState instance without the deleted key-value pair
     */
    TraceState delete(String key);

    /**
     * Returns a collection of all key-value pairs in the TraceState
     * @return a collection of KeyValue objects representing the key-value pairs
     */
    Collection<KeyValue> getValues();
}
