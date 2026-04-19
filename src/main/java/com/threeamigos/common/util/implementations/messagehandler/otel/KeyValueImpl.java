package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.util.Objects;

/**
 * Immutable implementation of {@link KeyValue}.
 *
 * @author Stefano Reksten
 */
public final class KeyValueImpl implements KeyValue {

    private final String key;
    private final AnyValue value;

    public KeyValueImpl(final String key, final AnyValue value) {
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.value = Objects.requireNonNull(value, "value must not be null");
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
