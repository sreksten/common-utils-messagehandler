package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared validation helpers for OTel key-value collections.
 */
final class OpenTelemetryAttributeValidator {

    static final int DEFAULT_ATTRIBUTE_COUNT_LIMIT = 128;
    private static final boolean lenient;

    static {
        lenient = "true".equalsIgnoreCase(System.getenv("OTEL_ERROR_HANDLER_LENIENT"));
    }

    private OpenTelemetryAttributeValidator() {
    }

    static void handle(final String message) {
        if (lenient) {
            logWithStackTrace(message, new IllegalArgumentException(message));
        } else {
            throw new IllegalArgumentException(message);
        }
    }

    static void handleBundled(final String errorMessage) {
        String message;
        try {
            message = MessageHandlerResourceBundle.get(errorMessage);
        } catch (Exception e) {
            if (lenient) {
                logWithStackTrace(e.getMessage(), e);
                message = errorMessage;
            } else {
                throw e;
            }
        }
        if (lenient) {
            logWithStackTrace(message, new IllegalArgumentException(message));
        } else {
            throw new IllegalArgumentException(message);
        }
    }

    static String requireNonBlank(final String value, final String fieldName) {
        if (value == null) {
            OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                    "builderFieldMustNotBeNull", fieldName));
            return "unknown";
        }
        if (value.trim().isEmpty()) {
            OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                    "builderFieldMustNotBeBlank", fieldName));
            return "unknown";
        }
        return value;
    }

    static List<KeyValue> copyAndValidateKeyValues(final List<KeyValue> keyValues, final String fieldName) {
        if (keyValues == null) {
            return new ArrayList<>();
        }

        List<KeyValue> copy = new ArrayList<>(keyValues.size());
        Set<String> keys = new HashSet<>(keyValues.size());
        for (int index = 0; index < keyValues.size(); index++) {
            KeyValue kv = keyValues.get(index);
            if (kv == null) {
                OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                        "fieldContainsNullElementAtIndex",
                        fieldName,
                        index));
                continue;
            }

            String key = kv.getKey();
            if (key == null) {
                OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                        "fieldKeyMustNotBeNullAtIndex",
                        fieldName,
                        index));
                continue;
            }
            if (key.trim().isEmpty()) {
                OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                        "fieldKeyMustNotBeEmptyAtIndex",
                        fieldName,
                        index));
                continue;
            }

            if (!keys.add(key)) {
                OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                        "fieldContainsDuplicateKey",
                        fieldName,
                        key));
                continue;
            }

            AnyValue value = kv.getValue();
            if (value == null) {
                OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                        "fieldValueMustNotBeNullAtIndex",
                        fieldName,
                        index));
                value = AnyValueFactory.empty();
            }

            copy.add(KeyValueFactory.of(key, value));
        }
        return copy;
    }

    static ValidationResult copyValidateAndLimitKeyValues(final List<KeyValue> keyValues,
                                                          final String fieldName) {
        List<KeyValue> validated = copyAndValidateKeyValues(keyValues, fieldName);
        if (OpenTelemetryAttributeValidator.DEFAULT_ATTRIBUTE_COUNT_LIMIT <= 0) {
            OpenTelemetryAttributeValidator.handle(
                    "maxAttributeCount must be positive, got: " + OpenTelemetryAttributeValidator.DEFAULT_ATTRIBUTE_COUNT_LIMIT);
            return new ValidationResult(new ArrayList<>(), validated.size());
        }
        if (validated.size() <= OpenTelemetryAttributeValidator.DEFAULT_ATTRIBUTE_COUNT_LIMIT) {
            return new ValidationResult(validated, 0);
        }

        int droppedCount = validated.size() - OpenTelemetryAttributeValidator.DEFAULT_ATTRIBUTE_COUNT_LIMIT;
        return new ValidationResult(new ArrayList<>(validated.subList(0, OpenTelemetryAttributeValidator.DEFAULT_ATTRIBUTE_COUNT_LIMIT)), droppedCount);
    }

    private static void logWithStackTrace(final String message, final Throwable throwable) {
        java.util.logging.Logger.getLogger(OpenTelemetryAttributeValidator.class.getName())
                .log(java.util.logging.Level.SEVERE, message, throwable);
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
