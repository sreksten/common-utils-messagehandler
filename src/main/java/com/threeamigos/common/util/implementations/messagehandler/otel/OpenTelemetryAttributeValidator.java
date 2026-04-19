package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Shared validation helpers for OTel key-value collections.
 */
final class OpenTelemetryAttributeValidator {

    private OpenTelemetryAttributeValidator() {
    }

    static List<KeyValue> copyAndValidateKeyValues(final List<KeyValue> keyValues, final String fieldName) {
        if (keyValues == null) {
            return new ArrayList<>();
        }

        List<KeyValue> copy = new ArrayList<>(keyValues.size());
        Set<String> keys = new HashSet<>(keyValues.size());
        int index = 0;
        for (KeyValue kv : keyValues) {
            if (kv == null) {
                throw new NullPointerException(fieldName + " contains null element at index " + index);
            }

            String key = Objects.requireNonNull(kv.getKey(),
                    fieldName + " key must not be null at index " + index);
            if (key.isEmpty()) {
                throw new IllegalArgumentException(fieldName + " key must not be empty at index " + index);
            }
            Objects.requireNonNull(kv.getValue(),
                    fieldName + " value must not be null at index " + index);
            if (!keys.add(key)) {
                throw new IllegalArgumentException(fieldName + " contains duplicate key: \"" + key + "\"");
            }
            copy.add(kv);
            index++;
        }
        return copy;
    }
}
