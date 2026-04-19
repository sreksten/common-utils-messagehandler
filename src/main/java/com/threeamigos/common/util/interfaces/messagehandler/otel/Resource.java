package com.threeamigos.common.util.interfaces.messagehandler.otel;

import java.util.List;

/**
 * Describes the entity that produced a telemetry signal, following the OpenTelemetry
 * <a href="https://opentelemetry.io/docs/specs/otel/resource/sdk/">Resource specification</a>.
 * <p>
 * Common attributes include {@code service.name}, {@code service.version},
 * {@code host.name}, and {@code process.pid}.
 *
 * @author Stefano Reksten
 */
public interface Resource {

    /**
     * @return the Schema URL that identifies the semantic convention schema used by this resource,
     *         or {@code null} if not set. Example: {@code "https://opentelemetry.io/schemas/1.25.0"}.
     */
    String getSchemaUrl();

    /**
     * @return the resource attributes; never {@code null}, may be empty.
     */
    List<KeyValue> getAttributes();

    /**
     * @return the number of attributes that were dropped due to collection limits; {@code 0} if none.
     */
    int getDroppedAttributesCount();
}
