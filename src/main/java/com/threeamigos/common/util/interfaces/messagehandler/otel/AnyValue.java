package com.threeamigos.common.util.interfaces.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.otel.AnyValueFactory;
import com.threeamigos.common.util.implementations.messagehandler.otel.OpenTelemetryAttributeValidator;

import java.util.List;

/**
 * A typed value container following the OpenTelemetry Specification references:
 * <ul>
 *     <li><a href="https://opentelemetry.io/docs/specs/otel/common/#anyvalue">OpenTelemetry
 *     Common: AnyValue</a></li>
 *     <li><a href="https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/common/v1/common.proto">
 *     OTLP Common Protobuf: AnyValue / ArrayValue / KeyValueList</a></li>
 *     <li><a href="https://opentelemetry.io/docs/specs/otel/error-handling/">Error handling in OpenTelemetry</a></li>
 * </ul>
 * <p>
 * Instances are created through {@link AnyValueFactory}.<br/>
 * An {@code AnyValue} holds one typed value (or an explicit empty value) whose kind is indicated by {@link #getType()}.
 * In this implementation, if the user tries to access a value of a different type, an exception is thrown; unless we
 * are running in <i>lenient mode</i> (see {@link OpenTelemetryAttributeValidator}), in which case default values are
 * returned in order not to have a crash in a production environment due to the logging system.
 *
 * @author Stefano Reksten
 */
public interface AnyValue {

    /**
     * The discriminated-union type of {@link AnyValue}.
     */
    enum Type {
        EMPTY, STRING, BOOL, INT, DOUBLE, ARRAY, KVLIST, BYTES
    }

    /**
     * @return the type of value held by this instance.
     */
    Type getType();

    /**
     * @return the string value, or {@code ""} when this value is not {@link Type#STRING}.
     */
    String asString();

    /**
     * @return the boolean value, or {@code false} when this value is not {@link Type#BOOL}.
     */
    boolean asBoolean();

    /**
     * @return the integer (long) value, or {@code 0L} when this value is not {@link Type#INT}.
     */
    long asLong();

    /**
     * @return the floating-point (double) value, or {@code 0.0} when this value is not {@link Type#DOUBLE}.
     */
    double asDouble();

    /**
     * @return the array value as a list of {@link AnyValue} elements, or an empty list when this
     * value is not {@link Type#ARRAY}.
     */
    List<AnyValue> asArray();

    /**
     * @return the key-value list value, or an empty list when this value is not {@link Type#KVLIST}.
     */
    List<KeyValue> asKvList();

    /**
     * @return the raw bytes value, or an empty byte array when this value is not {@link Type#BYTES}.
     */
    byte[] asBytes();
}
