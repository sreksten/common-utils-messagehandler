package com.threeamigos.common.util.interfaces.messagehandler.otel;

import java.util.List;

/**
 * A typed value container following the OTel
 * <a href="https://opentelemetry.io/docs/specs/otel/common/#anyvalue">AnyValue</a> specification.
 * <p>
 * An {@code AnyValue} holds exactly one value whose type is indicated by {@link #getType()}.
 * Calling a getter for a type other than the held type will throw {@link IllegalStateException}.
 * <p>
 * <b>Homogeneity requirement for attribute arrays:</b> When an {@code AnyValue} of type
 * {@link Type#ARRAY} is used as the value of a {@link KeyValue} attribute (i.e., inside
 * {@code LogRecord.getAttributes()}, {@code Resource.getAttributes()}, or
 * {@code InstrumentationScope.getAttributes()}), the OTel specification requires that all
 * elements of the array have the same primitive type (STRING, BOOL, INT, or DOUBLE).
 * Heterogeneous arrays are only permitted when {@code AnyValue} is used as the log record
 * body or as a value inside a {@link Type#KVLIST}.
 *
 * @author Stefano Reksten
 */
public interface AnyValue {

    /**
     * The discriminated-union type of {@link AnyValue}.
     */
    enum Type {
        STRING, BOOL, INT, DOUBLE, ARRAY, KVLIST, BYTES
    }

    /**
     * @return the type of value held by this instance.
     */
    Type getType();

    /**
     * @return the string value.
     * @throws IllegalStateException if {@link #getType()} is not {@link Type#STRING}.
     */
    String asString();

    /**
     * @return the boolean value.
     * @throws IllegalStateException if {@link #getType()} is not {@link Type#BOOL}.
     */
    boolean asBoolean();

    /**
     * @return the integer (long) value.
     * @throws IllegalStateException if {@link #getType()} is not {@link Type#INT}.
     */
    long asLong();

    /**
     * @return the floating-point (double) value.
     * @throws IllegalStateException if {@link #getType()} is not {@link Type#DOUBLE}.
     */
    double asDouble();

    /**
     * @return the array value as a list of {@link AnyValue} elements.
     * @throws IllegalStateException if {@link #getType()} is not {@link Type#ARRAY}.
     */
    List<AnyValue> asArray();

    /**
     * @return the key-value list value.
     * @throws IllegalStateException if {@link #getType()} is not {@link Type#KVLIST}.
     */
    List<KeyValue> asKvList();

    /**
     * @return the raw bytes value.
     * @throws IllegalStateException if {@link #getType()} is not {@link Type#BYTES}.
     */
    byte[] asBytes();
}
