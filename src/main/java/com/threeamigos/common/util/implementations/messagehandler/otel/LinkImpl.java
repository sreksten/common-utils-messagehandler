package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Link;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable span link implementation.
 *
 * @author Stefano Reksten
 */
final class LinkImpl implements Link {

    private final SpanContext spanContext;
    private final List<KeyValue> attributes;

    LinkImpl(final SpanContext spanContext, final List<KeyValue> attributes) {
        if (spanContext == null) {
            OpenTelemetryAttributeValidator.handle("linkSpanContext must not be null");
            this.spanContext = new SpanContextImpl();
        } else {
            this.spanContext = spanContext;
        }
        this.attributes = Collections.unmodifiableList(new ArrayList<>(
                OpenTelemetryAttributeValidator.copyAndValidateKeyValues(attributes, "link attributes")));
    }

    @Override
    public SpanContext getSpanContext() {
        return spanContext;
    }

    @Override
    public List<KeyValue> getAttributes() {
        return attributes;
    }
}
