package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable implementation of {@link AnyValue}.
 * Use the static factory methods to create instances.
 *
 * @author Stefano Reksten
 */
public final class AnyValueImpl implements AnyValue {

    private final Type type;
    private final String stringValue;
    private final boolean boolValue;
    private final long longValue;
    private final double doubleValue;
    private final List<AnyValue> arrayValue;
    private final List<KeyValue> kvListValue;
    private final byte[] bytesValue;

    private AnyValueImpl(final Type type,
                         final String stringValue,
                         final boolean boolValue,
                         final long longValue,
                         final double doubleValue,
                         final List<AnyValue> arrayValue,
                         final List<KeyValue> kvListValue,
                         final byte[] bytesValue) {
        this.type = type;
        this.stringValue = stringValue;
        this.boolValue = boolValue;
        this.longValue = longValue;
        this.doubleValue = doubleValue;
        this.arrayValue = arrayValue;
        this.kvListValue = kvListValue;
        this.bytesValue = bytesValue;
    }

    public static AnyValue ofString(final String value) {
        Objects.requireNonNull(value, "value must not be null");
        return new AnyValueImpl(Type.STRING, value, false, 0L, 0.0, null, null, null);
    }

    public static AnyValue ofBoolean(final boolean value) {
        return new AnyValueImpl(Type.BOOL, null, value, 0L, 0.0, null, null, null);
    }

    public static AnyValue ofLong(final long value) {
        return new AnyValueImpl(Type.INT, null, false, value, 0.0, null, null, null);
    }

    public static AnyValue ofDouble(final double value) {
        return new AnyValueImpl(Type.DOUBLE, null, false, 0L, value, null, null, null);
    }

    public static AnyValue ofArray(final List<AnyValue> value) {
        Objects.requireNonNull(value, "value must not be null");
        return new AnyValueImpl(Type.ARRAY, null, false, 0L, 0.0,
                Collections.unmodifiableList(new ArrayList<>(value)), null, null);
    }

    public static AnyValue ofKvList(final List<KeyValue> value) {
        Objects.requireNonNull(value, "value must not be null");
        return new AnyValueImpl(Type.KVLIST, null, false, 0L, 0.0,
                null, Collections.unmodifiableList(new ArrayList<>(value)), null);
    }

    public static AnyValue ofBytes(final byte[] value) {
        Objects.requireNonNull(value, "value must not be null");
        byte[] copy = new byte[value.length];
        System.arraycopy(value, 0, copy, 0, value.length);
        return new AnyValueImpl(Type.BYTES, null, false, 0L, 0.0, null, null, copy);
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public String asString() {
        if (type != Type.STRING) {
            throw new IllegalStateException("AnyValue type is " + type + ", not STRING");
        }
        return stringValue;
    }

    @Override
    public boolean asBoolean() {
        if (type != Type.BOOL) {
            throw new IllegalStateException("AnyValue type is " + type + ", not BOOL");
        }
        return boolValue;
    }

    @Override
    public long asLong() {
        if (type != Type.INT) {
            throw new IllegalStateException("AnyValue type is " + type + ", not INT");
        }
        return longValue;
    }

    @Override
    public double asDouble() {
        if (type != Type.DOUBLE) {
            throw new IllegalStateException("AnyValue type is " + type + ", not DOUBLE");
        }
        return doubleValue;
    }

    @Override
    public List<AnyValue> asArray() {
        if (type != Type.ARRAY) {
            throw new IllegalStateException("AnyValue type is " + type + ", not ARRAY");
        }
        return arrayValue;
    }

    @Override
    public List<KeyValue> asKvList() {
        if (type != Type.KVLIST) {
            throw new IllegalStateException("AnyValue type is " + type + ", not KVLIST");
        }
        return kvListValue;
    }

    @Override
    public byte[] asBytes() {
        if (type != Type.BYTES) {
            throw new IllegalStateException("AnyValue type is " + type + ", not BYTES");
        }
        byte[] copy = new byte[bytesValue.length];
        System.arraycopy(bytesValue, 0, copy, 0, bytesValue.length);
        return copy;
    }
}
