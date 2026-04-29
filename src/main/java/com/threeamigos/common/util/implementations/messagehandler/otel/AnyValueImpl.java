package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An immutable implementation of {@link AnyValue}. Instances are created through {@link AnyValueFactory}.<br/>
 * If the user tries to access a value of a different type, and we are running in lenient mode, default values are
 * returned in order not to have a crash in a production environment due to the logging system. Otherwise, an exception
 * is thrown. See also {@link OpenTelemetryAttributeValidator}.
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
        if (isTypeNot(Type.STRING)) {
            return "";
        }
        return stringValue;
    }

    @Override
    public boolean asBoolean() {
        if (isTypeNot(Type.BOOL)) {
            return false;
        }
        return boolValue;
    }

    @Override
    public long asLong() {
        if (isTypeNot(Type.INT)) {
            return 0L;
        }
        return longValue;
    }

    @Override
    public double asDouble() {
        if (isTypeNot(Type.DOUBLE)) {
            return 0.0d;
        }
        return doubleValue;
    }

    @Override
    public List<AnyValue> asArray() {
        if (isTypeNot(Type.ARRAY)) {
            return Collections.emptyList();
        }
        return arrayValue;
    }

    @Override
    public List<KeyValue> asKvList() {
        if (isTypeNot(Type.KVLIST)) {
            return Collections.emptyList();
        }
        return kvListValue;
    }

    @Override
    public byte[] asBytes() {
        if (isTypeNot(Type.BYTES)) {
            return new byte[0];
        }
        byte[] copy = new byte[bytesValue.length];
        System.arraycopy(bytesValue, 0, copy, 0, bytesValue.length);
        return copy;
    }

    private boolean isTypeNot(final Type expectedType) {
        if (type != expectedType) {
            OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                    "anyValueTypeMismatch", type, expectedType));
            return true;
        }
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        AnyValueImpl other = (AnyValueImpl) obj;
        return Objects.equals(this.type, other.type)
                && Objects.equals(this.stringValue, other.stringValue)
                && Objects.equals(this.boolValue, other.boolValue)
                && Objects.equals(this.longValue, other.longValue)
                && Objects.equals(this.doubleValue, other.doubleValue)
                && Objects.equals(this.arrayValue, other.arrayValue)
                && Objects.equals(this.kvListValue, other.kvListValue)
                && Arrays.equals(this.bytesValue, other.bytesValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, stringValue, boolValue, longValue, doubleValue, arrayValue, kvListValue,
                Arrays.hashCode(bytesValue));
    }
}
