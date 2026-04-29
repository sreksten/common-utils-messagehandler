package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.Event;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable span event implementation.
 *
 * @author Stefano Reksten
 */
final class EventImpl implements Event {

    private final String name;
    private final Instant timestamp;
    private final List<KeyValue> attributes;

    EventImpl(final String name, final Instant timestamp, final List<KeyValue> attributes) {
        this.name = OpenTelemetryAttributeValidator.requireNonBlank(name, "eventName");
        if (timestamp == null) {
            OpenTelemetryAttributeValidator.handle("eventTimestamp must not be null");
            this.timestamp = Instant.now();
        } else {
            this.timestamp = timestamp;
        }
        this.attributes = Collections.unmodifiableList(new ArrayList<>(
                OpenTelemetryAttributeValidator.copyAndValidateKeyValues(attributes, "event attributes")));
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
