package com.threeamigos.common.util.interfaces.messagehandler.otel;

import java.util.List;

/**
 * A link attached to a Span.
 * <p>
 * Specification references:
 * <ul>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/trace/api/#link">
 *   OpenTelemetry Trace API: Link</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/trace/api/#add-link">
 *   OpenTelemetry Trace API: Add Link</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/trace/api/#concurrency-requirements">
 *   OpenTelemetry Trace API: Concurrency requirements</a></li>
 * </ul>
 *
 * @author Stefano Reksten
 */
public interface Link {

    SpanContext getSpanContext();

    List<KeyValue> getAttributes();
}
