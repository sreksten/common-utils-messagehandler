package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Link;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable implementation of a Span {@link Link}.
 *
 * @author Stefano Reksten
 */
final class LinkImpl implements Link {

    private static final String LINK_ATTRIBUTES_FIELD_NAME = MessageHandlerResourceBundle.get("linkAttributesFieldName");

    private final SpanContext spanContext;
    private final List<KeyValue> attributes;

    LinkImpl(final SpanContext spanContext, final List<KeyValue> attributes) {
        if (spanContext == null) {
            OpenTelemetryAttributeValidator.handleBundled("linkSpanContextMustNotBeNull");
            this.spanContext = new SpanContextImpl();
        } else {
            this.spanContext = spanContext;
        }
        this.attributes = Collections.unmodifiableList(new ArrayList<>(
                OpenTelemetryAttributeValidator.copyAndValidateKeyValues(attributes, LINK_ATTRIBUTES_FIELD_NAME)));
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
