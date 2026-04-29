package com.threeamigos.common.util.interfaces.messagehandler.otel;

import java.time.Instant;
import java.util.List;

/**
 * Event attached to a span.
 * <p>
 * Specification reference:
 * <a href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/api.md#add-events">OpenTelemetry Trace API: Add Events</a>.
 *
 * @author Stefano Reksten
 */
public interface Event {

    String getName();

    Instant getTimestamp();

    List<KeyValue> getAttributes();
}
