package com.threeamigos.common.util.interfaces.messagehandler.otel;

import java.util.List;

/**
 * A typed value container following the OpenTelemetry
 * <a href="https://opentelemetry.io/docs/specs/otel/common/#anyvalue">AnyValue</a> specification.
 * <p>
 * An {@code AnyValue} holds one typed value (or an explicit empty value) whose kind is indicated
 * by {@link #getType()}. Calling a getter for a type other than the held type will throw
 * {@link IllegalStateException}.
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
