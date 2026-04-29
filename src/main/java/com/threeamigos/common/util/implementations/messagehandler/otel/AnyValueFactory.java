package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility factory for creating {@link AnyValue} values. If an invalid value is provided, and we are running in lenient
 * mode, default (empty) values are used in order not to have a crash in a production environment due to the logging
 * system. Otherwise, an exception is thrown. See also {@link OpenTelemetryAttributeValidator}.
 *
 * @author Stefano Reksten
 */
public final class AnyValueFactory {

    private static final AnyValue EMPTY_VALUE =
            new AnyValueImpl(AnyValue.Type.EMPTY, null, false, 0L, 0.0, null, null, null);

    private AnyValueFactory() {}

    public static AnyValue empty() {
        return EMPTY_VALUE;
    }

    /**
     * If a null value is passed, an empty value is returned if in lenient mode. Otherwise, an exception is thrown.
     * @param value the value to use
     * @return an AnyValue instance
     */
    public static AnyValue ofString(final @Nonnull String value) {
        if (value == null) {
            OpenTelemetryAttributeValidator.handleBundled("valueMustNotBeNull");
            return EMPTY_VALUE;
        }
        return new AnyValueImpl(AnyValue.Type.STRING, value, false, 0L, 0.0, null, null, null);
    }

    public static AnyValue ofNullableString(final @Nullable String value) {
        return value == null ? EMPTY_VALUE : ofString(value);
    }

    public static AnyValue ofBoolean(final boolean value) {
        return new AnyValueImpl(AnyValue.Type.BOOL, null, value, 0L, 0.0, null, null, null);
    }

    public static AnyValue ofLong(final long value) {
        return new AnyValueImpl(AnyValue.Type.INT, null, false, value, 0.0, null, null, null);
    }

    public static AnyValue ofDouble(final double value) {
        return new AnyValueImpl(AnyValue.Type.DOUBLE, null, false, 0L, value, null, null, null);
    }

    /**
     * If a null value is passed, an empty value is returned if in lenient mode. Otherwise, an exception is thrown.
     * @param value the value to use
     * @return an AnyValue instance
     */
    public static AnyValue ofArray(final @Nonnull List<AnyValue> value) {
        if (value == null) {
            OpenTelemetryAttributeValidator.handleBundled("valueMustNotBeNull");
            return EMPTY_VALUE;
        }
        List<AnyValue> copy = new ArrayList<>(value.size());
        for (AnyValue entry : value) {
            // OTel requires preserving null array elements when accepted.
            // In this model, null is represented as an empty AnyValue.
            copy.add(entry != null ? entry : EMPTY_VALUE);
        }
        return new AnyValueImpl(AnyValue.Type.ARRAY, null, false, 0L, 0.0, Collections.unmodifiableList(copy), null, null);
    }

    /**
     * If a null value is passed, an empty value is returned if in lenient mode. Otherwise, an exception is thrown.
     * @param value the value to use
     * @return an AnyValue instance
     */
    public static AnyValue ofKvList(final @Nonnull List<KeyValue> value) {
        if (value == null) {
            OpenTelemetryAttributeValidator.handleBundled("valueMustNotBeNull");
            return EMPTY_VALUE;
        }
        List<KeyValue> copy = new ArrayList<>(value.size());
        Set<String> keys = new HashSet<>(value.size());
        for (int index = 0; index < value.size(); index++) {
            KeyValue kv = value.get(index);
            if (kv == null) {
                OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                        "fieldContainsNullElementAtIndex",
                        MessageHandlerResourceBundle.get("kvlistValuesFieldName"),
                        index));
                continue;
            }
            String key = kv.getKey();
            if (key == null) {
                OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                            "fieldKeyMustNotBeNullAtIndex",
                            MessageHandlerResourceBundle.get("kvlistValuesFieldName"),
                            index));
                continue;
            }
            if (!keys.add(key)) {
                // map<string, AnyValue> keys must be unique; by default, duplicates are removed.
                OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                        "fieldContainsDuplicateKey",
                        MessageHandlerResourceBundle.get("kvlistValuesFieldName"),
                        key));
                continue;
            }
            // For map<string, AnyValue>, an absent/null source value is represented as empty AnyValue.
            AnyValue normalizedValue = kv.getValue() != null ? kv.getValue() : EMPTY_VALUE;
            copy.add(new KvListKeyValue(key, normalizedValue));
        }
        return new AnyValueImpl(AnyValue.Type.KVLIST, null, false, 0L, 0.0, null, Collections.unmodifiableList(copy), null);
    }

    /**
     * If a null value is passed, an empty value is returned if in lenient mode. Otherwise, an exception is thrown.
     * @param value the value to use
     * @return an AnyValue instance
     */
    public static AnyValue ofBytes(final @Nonnull byte[] value) {
        if (value == null) {
            OpenTelemetryAttributeValidator.handleBundled("valueMustNotBeNull");
            return EMPTY_VALUE;
        }
        byte[] copy = new byte[value.length];
        System.arraycopy(value, 0, copy, 0, value.length);
        return new AnyValueImpl(AnyValue.Type.BYTES, null, false, 0L, 0.0, null, null, copy);
    }

    private static final class KvListKeyValue implements KeyValue {
        private final String key;
        private final AnyValue value;

        private KvListKeyValue(final String key, final AnyValue value) {
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
}
