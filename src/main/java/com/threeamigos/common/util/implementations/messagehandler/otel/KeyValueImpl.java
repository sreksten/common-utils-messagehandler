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
        Objects.requireNonNull(key, "key must not be null");
        if (key.isEmpty()) {
            throw new IllegalArgumentException("key must not be empty");
        }
        Objects.requireNonNull(value, "value must not be null");
        if (value.getType() == AnyValue.Type.ARRAY) {
            long distinctTypes = value.asArray().stream().map(AnyValue::getType).distinct().count();
            if (distinctTypes > 1) {
                throw new IllegalArgumentException(
                        "attribute array elements must all have the same type (OTel homogeneity requirement)");
            }
        }
        this.key = key;
        this.value = value;
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
