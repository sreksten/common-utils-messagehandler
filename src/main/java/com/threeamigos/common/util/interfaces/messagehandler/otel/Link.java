package com.threeamigos.common.util.interfaces.messagehandler.otel;

import java.util.List;

/**
 * Link attached to a span.
 * <p>
 * Specification reference:
 * <a href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/api.md#add-link">OpenTelemetry Trace API: Add Link</a>.
 *
 * @author Stefano Reksten
 */
public interface Link {

    SpanContext getSpanContext();

    List<KeyValue> getAttributes();
}
