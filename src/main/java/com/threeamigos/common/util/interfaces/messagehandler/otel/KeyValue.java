package com.threeamigos.common.util.interfaces.messagehandler.otel;

/**
 * An immutable key-value pair.
 * <p>
 * Specification references:
 * <ul>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/common/#attribute">OpenTelemetry Common: Attribute</a></li>
 *   <li><a href="https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/common/v1/common.proto">OTLP
 *   common.proto (KeyValue)</a></li>
 * </ul>
 * The key MUST be a non-null, non-empty string.
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
