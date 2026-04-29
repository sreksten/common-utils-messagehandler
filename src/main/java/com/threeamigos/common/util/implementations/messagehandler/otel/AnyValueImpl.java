package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable implementation of {@link AnyValue}. Instances are created through
 * {@link AnyValueFactory}.
 * <p>
 * Specification references used for this implementation:
 * <ul>
 *     <li><a href="https://opentelemetry.io/docs/specs/otel/common/#anyvalue">OpenTelemetry
 *     Common: AnyValue</a></li>
 *     <li><a href="https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/common/v1/common.proto">
 *     OTLP Common Protobuf: AnyValue / ArrayValue / KeyValueList</a></li>
 * </ul>
 * <p>
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
        this.type = type != null ? type : Type.EMPTY;
        this.stringValue = stringValue != null ? stringValue : "";
        this.boolValue = boolValue;
        this.longValue = longValue;
        this.doubleValue = doubleValue;
        this.arrayValue = arrayValue != null ? arrayValue : Collections.emptyList();
        this.kvListValue = kvListValue != null ? kvListValue : Collections.emptyList();
        this.bytesValue = bytesValue != null ? bytesValue : new byte[0];
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
        if (!Objects.equals(this.type, other.type)) {
            return false;
        }
        if (this.type == Type.EMPTY) {
            return true;
        }
        if (this.type == Type.STRING) {
            return Objects.equals(this.stringValue, other.stringValue);
        }
        if (this.type == Type.BOOL) {
            return this.boolValue == other.boolValue;
        }
        if (this.type == Type.INT) {
            return this.longValue == other.longValue;
        }
        if (this.type == Type.DOUBLE) {
            return Double.compare(this.doubleValue, other.doubleValue) == 0;
        }
        if (this.type == Type.ARRAY) {
            return Objects.equals(this.arrayValue, other.arrayValue);
        }
        if (this.type == Type.KVLIST) {
            return kvListEquals(this.kvListValue, other.kvListValue);
        }
        return Arrays.equals(this.bytesValue, other.bytesValue);
    }

    @Override
    public int hashCode() {
        if (type == Type.EMPTY) {
            return Objects.hash(type);
        }
        if (type == Type.STRING) {
            return Objects.hash(type, stringValue);
        }
        if (type == Type.BOOL) {
            return Objects.hash(type, boolValue);
        }
        if (type == Type.INT) {
            return Objects.hash(type, longValue);
        }
        if (type == Type.DOUBLE) {
            return Objects.hash(type, Double.hashCode(doubleValue));
        }
        if (type == Type.ARRAY) {
            return Objects.hash(type, arrayValue);
        }
        if (type == Type.KVLIST) {
            return Objects.hash(type, kvListHash(kvListValue));
        }
        return Objects.hash(type, Arrays.hashCode(bytesValue));
    }

    private static boolean kvListEquals(final List<KeyValue> left, final List<KeyValue> right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        Map<String, AnyValue> leftMap = toUniqueMap(left);
        Map<String, AnyValue> rightMap = toUniqueMap(right);
        if (leftMap == null || rightMap == null) {
            return left.equals(right);
        }
        return leftMap.equals(rightMap);
    }

    private static int kvListHash(final List<KeyValue> values) {
        Map<String, AnyValue> map = toUniqueMap(values);
        return map != null ? map.hashCode() : values.hashCode();
    }

    private static Map<String, AnyValue> toUniqueMap(final List<KeyValue> values) {
        if (values == null) {
            return null;
        }
        Map<String, AnyValue> map = new LinkedHashMap<>(values.size());
        for (KeyValue kv : values) {
            if (kv == null || kv.getKey() == null) {
                return null;
            }
            if (map.containsKey(kv.getKey())) {
                return null;
            }
            map.put(kv.getKey(), kv.getValue());
        }
        return map;
    }
}
