package com.threeamigos.common.util.interfaces.messagehandler.otel;

import java.util.List;

/**
 * Identifies the library or component that emitted a telemetry signal, following the
 * OpenTelemetry <a href="https://opentelemetry.io/docs/specs/otel/glossary/#instrumentation-scope">
 * Instrumentation Scope specification</a>.
 *
 * @author Stefano Reksten
 */
public interface InstrumentationScope {

    /**
     * @return the name of the instrumentation scope (e.g., a library name); may be {@code null}.
     */
    String getName();

    /**
     * @return the version of the instrumentation scope; may be {@code null}.
     */
    String getVersion();

    /**
     * @return the Schema URL that identifies the semantic convention schema used by this scope,
     *         or {@code null} if not set. Example: {@code "https://opentelemetry.io/schemas/1.25.0"}.
     */
    String getSchemaUrl();

    /**
     * @return additional attributes attached to this scope; never {@code null}, may be empty.
     */
    List<KeyValue> getAttributes();

    /**
     * @return the number of attributes dropped due to limits; may be {@code 0} if not applicable.
     */
    int getDroppedAttributesCount();
}
