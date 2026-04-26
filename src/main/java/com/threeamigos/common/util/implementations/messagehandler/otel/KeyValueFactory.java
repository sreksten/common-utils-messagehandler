package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

/**
 * Utility factory for creating {@link KeyValue} values.
 *
 * @author Stefano Reksten
 */
public final class KeyValueFactory {

    private KeyValueFactory() {
        throw new UnsupportedOperationException();
    }

    public static KeyValue of(final String key, final AnyValue value) {
        return new KeyValueImpl(key, value);
    }

    public static KeyValue of(final Names name, final AnyValue value) {
        return new KeyValueImpl(name, value);
    }
}
