package com.threeamigos.common.util.interfaces.messagehandler.otel;

import java.time.Instant;
import java.util.List;

/**
 * Event attached to a span.
 * <p>
 * Specification references:
 * <ul>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/trace/api/#add-events">
 *   OpenTelemetry Trace API: Add Events</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/trace/api/#concurrency-requirements">
 *   OpenTelemetry Trace API: Concurrency requirements (Events are immutable)</a></li>
 * </ul>
 *
 * @author Stefano Reksten
 */
public interface Event {

    String getName();

    Instant getTimestamp();

    List<KeyValue> getAttributes();
}
