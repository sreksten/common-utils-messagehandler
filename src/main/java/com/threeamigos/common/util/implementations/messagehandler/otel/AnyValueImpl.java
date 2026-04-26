package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.util.List;

/**
 * Immutable implementation of {@link AnyValue}.
 * Instances are created through {@link AnyValueFactory}.
 *
 * @author Stefano Reksten
 */
final class AnyValueImpl implements AnyValue {

    private final Type type;
    private final String stringValue;
    private final boolean boolValue;
    private final long longValue;
    private final double doubleValue;
    private final List<AnyValue> arrayValue;
    private final List<KeyValue> kvListValue;
    private final byte[] bytesValue;

    AnyValueImpl(final Type type,
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

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public String asString() {
        checkType(Type.STRING);
        return stringValue;
    }

    @Override
    public boolean asBoolean() {
        checkType(Type.BOOL);
        return boolValue;
    }

    @Override
    public long asLong() {
        checkType(Type.INT);
        return longValue;
    }

    @Override
    public double asDouble() {
        checkType(Type.DOUBLE);
        return doubleValue;
    }

    @Override
    public List<AnyValue> asArray() {
        checkType(Type.ARRAY);
        return arrayValue;
    }

    @Override
    public List<KeyValue> asKvList() {
        checkType(Type.KVLIST);
        return kvListValue;
    }

    @Override
    public byte[] asBytes() {
        checkType(Type.BYTES);
        byte[] copy = new byte[bytesValue.length];
        System.arraycopy(bytesValue, 0, copy, 0, bytesValue.length);
        return copy;
    }

    private void checkType(final Type expectedType) {
        if (type != expectedType) {
            throw new IllegalStateException(MessageHandlerResourceBundle.format(
                    "anyValueTypeMismatch",
                    type,
                    expectedType));
        }
    }
}
