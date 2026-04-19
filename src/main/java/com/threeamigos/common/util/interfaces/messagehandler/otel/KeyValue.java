package com.threeamigos.common.util.interfaces.messagehandler.otel;

/**
 * An immutable key-value pair following the OTel
 * <a href="https://opentelemetry.io/docs/specs/otel/common/#attribute">KeyValue</a> specification.
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
