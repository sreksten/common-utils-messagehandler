package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.util.Objects;

/**
 * Immutable implementation of {@link KeyValue}.
 *
 * @author Stefano Reksten
 */
final class KeyValueImpl implements KeyValue {

    private final String key;
    private final AnyValue value;

    /**
     * Creates a key-value pair using an explicit attribute key string.
     *
     * @param key   attribute key
     * @param value attribute value
     */
    KeyValueImpl(final String key, final AnyValue value) {
        Objects.requireNonNull(key, MessageHandlerResourceBundle.get("keyMustNotBeNull"));
        if (key.isEmpty()) {
            throw new IllegalArgumentException(MessageHandlerResourceBundle.get("keyMustNotBeEmpty"));
        }
        Objects.requireNonNull(value, MessageHandlerResourceBundle.get("valueMustNotBeNull"));
        this.key = key;
        this.value = value;
    }

    /**
     * Creates a key-value pair using a known OpenTelemetry {@link OTelTags} key.
     *
     * @param name  known OpenTelemetry attribute/resource name
     * @param value attribute value
     */
    public KeyValueImpl(final OTelTags name, final AnyValue value) {
        this(Objects.requireNonNull(name, MessageHandlerResourceBundle.get("keyMustNotBeNull")).getValue(), value);
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public AnyValue getValue() {
        return value;
    }
}
