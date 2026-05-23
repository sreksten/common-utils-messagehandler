package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared validation helpers for OTel key-value collections.
 *
 * <p>Strict/lenient behavior defaults to {@code OTEL_ERROR_HANDLER_LENIENT}, resolved once when this
 * class is initialized. A runtime override can be applied through
 * {@link #setLenientModeOverride(boolean)} and cleared with {@link #clearLenientModeOverride()}.
 */
public final class OpenTelemetryAttributeValidator {

    static final int DEFAULT_ATTRIBUTE_COUNT_LIMIT = 128;
    private static final boolean LENIENT_MODE_FROM_ENV;
    private static volatile Boolean lenientModeOverride;
    private static volatile LogTrap testLogTrap;

    static {
        LENIENT_MODE_FROM_ENV = "true".equalsIgnoreCase(System.getenv("OTEL_ERROR_HANDLER_LENIENT"));
        lenientModeOverride = null;
    }

    private OpenTelemetryAttributeValidator() {
    }

    public static void handle(final String message) {
        if (isLenientMode()) {
            logWithStackTrace(message, new IllegalArgumentException(message));
        } else {
            throw new IllegalArgumentException(message);
        }
    }

    public static void handleBundled(final String errorMessage) {
        String message = resolveBundledMessage(errorMessage);
        if (isLenientMode()) {
            logWithStackTrace(message, new IllegalArgumentException(message));
        } else {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Returns the effective lenient mode.
     *
     * <p>If a runtime override is configured through {@link #setLenientModeOverride(boolean)}, that
     * value is returned. Otherwise, the mode resolved from {@code OTEL_ERROR_HANDLER_LENIENT} at class
     * initialization time is returned.
     *
     * @return {@code true} when validation errors are logged and suppressed, {@code false} when
     *         validation errors throw {@link IllegalArgumentException}
     */
    public static boolean isLenientMode() {
        Boolean override = lenientModeOverride;
        return override != null ? override.booleanValue() : LENIENT_MODE_FROM_ENV;
    }

    /**
     * Applies a runtime override for lenient mode.
     *
     * <p>The override takes precedence over the value loaded from
     * {@code OTEL_ERROR_HANDLER_LENIENT} until {@link #clearLenientModeOverride()} is called.
     *
     * @param value {@code true} to enable lenient mode, {@code false} to force strict mode
     */
    public static void setLenientModeOverride(final boolean value) {
        lenientModeOverride = Boolean.valueOf(value);
    }

    /**
     * Clears the runtime lenient-mode override and restores environment-based behavior.
     */
    public static void clearLenientModeOverride() {
        lenientModeOverride = null;
    }

    public static void report(final String message) {
        logWithStackTrace(message, new IllegalArgumentException(message));
    }

    public static void report(final String message, final Throwable throwable) {
        Throwable effectiveThrowable = throwable == null
                ? new IllegalArgumentException(message)
                : throwable;
        logWithStackTrace(message, effectiveThrowable);
    }

    public static void reportBundled(final String messageKey, final Object... args) {
        String message = resolveBundledMessage(messageKey, args);
        report(message, new IllegalArgumentException(message));
    }

    static void setLenientModeForTests(final boolean value) {
        setLenientModeOverride(value);
    }

    static void setLogTrapForTests(final LogTrap trap) {
        testLogTrap = trap;
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
        return copyAndValidateKeyValues(keyValues, fieldName, false);
    }

    static List<KeyValue> copyAndValidateKeyValuesLenient(final List<KeyValue> keyValues, final String fieldName) {
        return copyAndValidateKeyValues(keyValues, fieldName, true);
    }

    private static List<KeyValue> copyAndValidateKeyValues(final List<KeyValue> keyValues,
                                                           final String fieldName,
                                                           final boolean forceLenient) {
        if (keyValues == null) {
            return new ArrayList<>();
        }

        List<KeyValue> copy = new ArrayList<>(keyValues.size());
        Set<String> keys = new HashSet<>(keyValues.size());
        for (int index = 0; index < keyValues.size(); index++) {
            KeyValue kv = keyValues.get(index);
            if (kv == null) {
                handle(MessageHandlerResourceBundle.format(
                        "fieldContainsNullElementAtIndex",
                        fieldName,
                        index), forceLenient);
                continue;
            }

            String key = kv.getKey();
            if (key == null) {
                handle(MessageHandlerResourceBundle.format(
                        "fieldKeyMustNotBeNullAtIndex",
                        fieldName,
                        index), forceLenient);
                continue;
            }
            if (key.trim().isEmpty()) {
                handle(MessageHandlerResourceBundle.format(
                        "fieldKeyMustNotBeEmptyAtIndex",
                        fieldName,
                        index), forceLenient);
                continue;
            }

            if (!keys.add(key)) {
                handle(MessageHandlerResourceBundle.format(
                        "fieldContainsDuplicateKey",
                        fieldName,
                        key), forceLenient);
                continue;
            }

            AnyValue value = kv.getValue();
            if (value == null) {
                handle(MessageHandlerResourceBundle.format(
                        "fieldValueMustNotBeNullAtIndex",
                        fieldName,
                        index), forceLenient);
                value = AnyValueFactory.empty();
            }

            copy.add(KeyValueFactory.of(key, value));
        }
        return copy;
    }

    private static void handle(final String message, final boolean forceLenient) {
        if (forceLenient || isLenientMode()) {
            logWithStackTrace(message, new IllegalArgumentException(message));
        } else {
            throw new IllegalArgumentException(message);
        }
    }

    static ValidationResult copyValidateAndLimitKeyValues(final List<KeyValue> keyValues,
                                                          final String fieldName) {
        return copyValidateAndLimitKeyValues(keyValues, fieldName, false);
    }

    static ValidationResult copyValidateAndLimitKeyValuesLenient(final List<KeyValue> keyValues,
                                                                 final String fieldName) {
        return copyValidateAndLimitKeyValues(keyValues, fieldName, true);
    }

    private static ValidationResult copyValidateAndLimitKeyValues(final List<KeyValue> keyValues,
                                                                  final String fieldName,
                                                                  final boolean forceLenient) {
        List<KeyValue> validated = forceLenient
                ? copyAndValidateKeyValuesLenient(keyValues, fieldName)
                : copyAndValidateKeyValues(keyValues, fieldName);
        if (OpenTelemetryAttributeValidator.DEFAULT_ATTRIBUTE_COUNT_LIMIT <= 0) {
            handle(MessageHandlerResourceBundle.format(
                    "maxAttributeCountMustBePositive",
                    OpenTelemetryAttributeValidator.DEFAULT_ATTRIBUTE_COUNT_LIMIT),
                    forceLenient);
            return new ValidationResult(new ArrayList<>(), validated.size());
        }
        if (validated.size() <= OpenTelemetryAttributeValidator.DEFAULT_ATTRIBUTE_COUNT_LIMIT) {
            return new ValidationResult(validated, 0);
        }

        int droppedCount = validated.size() - OpenTelemetryAttributeValidator.DEFAULT_ATTRIBUTE_COUNT_LIMIT;
        return new ValidationResult(new ArrayList<>(validated.subList(0, OpenTelemetryAttributeValidator.DEFAULT_ATTRIBUTE_COUNT_LIMIT)), droppedCount);
    }

    private static void logWithStackTrace(final String message, final Throwable throwable) {
        LogTrap trap = testLogTrap;
        if (trap != null) {
            try {
                trap.onLog(message, throwable);
                return;
            } catch (Exception ignored) {
                // Fall back to regular logging if test hook fails.
            }
        }

        java.util.logging.Logger.getLogger(OpenTelemetryAttributeValidator.class.getName())
                .log(java.util.logging.Level.SEVERE, message, throwable);
    }

    private static String resolveBundledMessage(final String errorMessage, final Object... args) {
        try {
            return args == null || args.length == 0
                    ? MessageHandlerResourceBundle.get(errorMessage)
                    : MessageHandlerResourceBundle.format(errorMessage, args);
        } catch (Exception e) {
            if (isLenientMode()) {
                logWithStackTrace(e.getMessage(), e);
            } else if (args == null || args.length == 0) {
                throw e;
            }
            return args == null || args.length == 0
                    ? errorMessage
                    : errorMessage + " " + Arrays.toString(args);
        }
    }

    @FunctionalInterface
    interface LogTrap {
        void onLog(String message, Throwable throwable);
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
