package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
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

    static final int DEFAULT_ATTRIBUTE_COUNT_LIMIT = 128;

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
                throw new NullPointerException(MessageHandlerResourceBundle.format(
                        "fieldContainsNullElementAtIndex",
                        fieldName,
                        index));
            }

            String key = Objects.requireNonNull(kv.getKey(),
                    MessageHandlerResourceBundle.format(
                            "fieldKeyMustNotBeNullAtIndex",
                            fieldName,
                            index));
            if (key.isEmpty()) {
                throw new IllegalArgumentException(MessageHandlerResourceBundle.format(
                        "fieldKeyMustNotBeEmptyAtIndex",
                        fieldName,
                        index));
            }
            Objects.requireNonNull(kv.getValue(),
                    MessageHandlerResourceBundle.format(
                            "fieldValueMustNotBeNullAtIndex",
                            fieldName,
                            index));
            if (!keys.add(key)) {
                throw new IllegalArgumentException(MessageHandlerResourceBundle.format(
                        "fieldContainsDuplicateKey",
                        fieldName,
                        key));
            }
            copy.add(kv);
            index++;
        }
        return copy;
    }

    static ValidationResult copyValidateAndLimitKeyValues(final List<KeyValue> keyValues,
                                                          final String fieldName,
                                                          final int maxAttributeCount) {
        List<KeyValue> validated = copyAndValidateKeyValues(keyValues, fieldName);
        if (validated.size() <= maxAttributeCount) {
            return new ValidationResult(validated, 0);
        }

        int droppedCount = validated.size() - maxAttributeCount;
        return new ValidationResult(new ArrayList<>(validated.subList(0, maxAttributeCount)), droppedCount);
    }

    static final class ValidationResult {
        private final List<KeyValue> attributes;
        private final int droppedAttributesCount;

        ValidationResult(final List<KeyValue> attributes, final int droppedAttributesCount) {
            this.attributes = attributes;
            this.droppedAttributesCount = droppedAttributesCount;
        }

        List<KeyValue> getAttributes() {
            return attributes;
        }

        int getDroppedAttributesCount() {
            return droppedAttributesCount;
        }
    }
}
