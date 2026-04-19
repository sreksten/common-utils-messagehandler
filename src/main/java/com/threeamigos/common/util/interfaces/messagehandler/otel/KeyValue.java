package com.threeamigos.common.util.interfaces.messagehandler.otel;

/**
 * An immutable key-value pair following the OTel
 * <a href="https://opentelemetry.io/docs/specs/otel/common/#attribute">KeyValue</a> specification.
 * <p>
 * The key MUST be a non-null, non-empty string. If the value is an {@link AnyValue} of type
 * {@link AnyValue.Type#ARRAY}, all elements of that array MUST be of the same type —
 * {@code KeyValueImpl} enforces this at construction time by counting distinct element types
 * and throwing {@link IllegalArgumentException} if more than one is found.
 *
 * @author Stefano Reksten
 */
public interface KeyValue {

    /**
     * @return the attribute key; never {@code null}.
     */
    String getKey();

    /**
     * @return the attribute value; never {@code null}.
     */
    AnyValue getValue();
}
