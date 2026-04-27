package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;

/**
 * Shared validation utilities for fluent builder inputs.
 */
final class BuilderValidationUtils {

    private BuilderValidationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    static String requireNonBlank(final String value, final String fieldName) {
        if (value == null) {
            throw new NullPointerException(MessageHandlerResourceBundle.format(
                    "builderFieldMustNotBeNull",
                    fieldName));
        }
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(MessageHandlerResourceBundle.format(
                    "builderFieldMustNotBeBlank",
                    fieldName));
        }
        return value;
    }
}
