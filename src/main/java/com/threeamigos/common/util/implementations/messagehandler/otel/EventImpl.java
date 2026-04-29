package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Event;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable implementation of a Span {@link Event}.
 *
 * @author Stefano Reksten
 */
final class EventImpl implements Event {

    private static final String EVENT_ATTRIBUTES_FIELD_NAME = MessageHandlerResourceBundle.get("eventAttributesFieldName");

    private final String name;
    private final Instant timestamp;
    private final List<KeyValue> attributes;

    EventImpl(final String name, final Instant timestamp, final List<KeyValue> attributes) {
        this.name = OpenTelemetryAttributeValidator.requireNonBlank(name, "eventName");
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
        this.attributes = Collections.unmodifiableList(new ArrayList<>(
                OpenTelemetryAttributeValidator.copyAndValidateKeyValues(attributes, EVENT_ATTRIBUTES_FIELD_NAME)));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public List<KeyValue> getAttributes() {
        return attributes;
    }
}
