package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Utility factory for creating {@link AnyValue} values.
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

    public static AnyValue ofString(final String value) {
        Objects.requireNonNull(value, MessageHandlerResourceBundle.get("valueMustNotBeNull"));
        return new AnyValueImpl(AnyValue.Type.STRING, value, false, 0L, 0.0, null, null, null);
    }

    public static AnyValue ofNullableString(final String value) {
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

    public static AnyValue ofArray(final List<AnyValue> value) {
        Objects.requireNonNull(value, MessageHandlerResourceBundle.get("valueMustNotBeNull"));
        List<AnyValue> copy = new ArrayList<>(value.size());
        for (AnyValue entry : value) {
            // OTel Common allows empty values and requires preserving nulls in arrays when accepted.
            copy.add(entry != null ? entry : EMPTY_VALUE);
        }
        return new AnyValueImpl(AnyValue.Type.ARRAY, null, false, 0L, 0.0, Collections.unmodifiableList(copy), null, null);
    }

    public static AnyValue ofKvList(final List<KeyValue> value) {
        Objects.requireNonNull(value, MessageHandlerResourceBundle.get("valueMustNotBeNull"));
        List<KeyValue> copy = new ArrayList<>(value.size());
        Set<String> keys = new HashSet<>(value.size());
        int index = 0;
        for (KeyValue kv : value) {
            if (kv == null) {
                throw new NullPointerException(MessageHandlerResourceBundle.format(
                        "fieldContainsNullElementAtIndex",
                        MessageHandlerResourceBundle.get("kvlistValuesFieldName"),
                        index));
            }
            String key = Objects.requireNonNull(kv.getKey(),
                    MessageHandlerResourceBundle.format(
                            "fieldKeyMustNotBeNullAtIndex",
                            MessageHandlerResourceBundle.get("kvlistValuesFieldName"),
                            index));
            if (!keys.add(key)) {
                throw new IllegalArgumentException(MessageHandlerResourceBundle.format(
                        "fieldContainsDuplicateKey",
                        MessageHandlerResourceBundle.get("kvlistValuesFieldName"),
                        key));
            }
            // OTel Common map<string, AnyValue>: null value is valid and maps to empty AnyValue.
            AnyValue normalizedValue = kv.getValue() != null ? kv.getValue() : EMPTY_VALUE;
            copy.add(new KvListKeyValue(key, normalizedValue));
            index++;
        }
        return new AnyValueImpl(AnyValue.Type.KVLIST, null, false, 0L, 0.0, null, Collections.unmodifiableList(copy), null);
    }

    public static AnyValue ofBytes(final byte[] value) {
        Objects.requireNonNull(value, MessageHandlerResourceBundle.get("valueMustNotBeNull"));
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
