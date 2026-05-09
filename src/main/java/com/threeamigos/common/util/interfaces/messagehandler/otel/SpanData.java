package com.threeamigos.common.util.interfaces.messagehandler.otel;

import java.time.Instant;
import java.util.List;

/**
 * Immutable snapshot-like view of span data meant for export.
 */
public interface SpanData {

    String getName();

    SpanContext getSpanContext();

    String getParentSpanId();

    InstrumentationScope getInstrumentationScope();

    Instant getStartTimestamp();

    Instant getEndTimestamp();

    StatusCode getStatusCode();

    String getStatusDescription();

    List<KeyValue> getAttributes();

    List<Event> getEvents();

    List<Link> getLinks();
}
