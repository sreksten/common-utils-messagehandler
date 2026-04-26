package com.threeamigos.common.util.interfaces.messagehandler.otel;

import java.util.List;

/**
 * Describes the source of telemetry, following the OpenTelemetry
 * <a href="https://opentelemetry.io/docs/specs/otel/resource/data-model/">Resource Data Model</a>.
 * <p>
 * A resource is represented by:
 * <ul>
 *     <li>zero or more typed {@link Entity} values</li>
 *     <li>zero or more loose resource attributes</li>
 * </ul>
 * The OTLP schema URL associated with the resource can be exposed through {@link #getSchemaUrl()}.
 *
 * @author Stefano Reksten
 */
public interface Resource {

    /**
     * Entities allow telemetry to explicitly model multiple related components within a single signal:
     * <ul>
     * <li>Service: The logical application component.</li>
     * <li>Process: The specific runtime instance of that service.</li>
     * <li>Container: The environment the process lives in.</li>
     * <li>Host/Node: The physical or virtual machine running the container.</li>
     * <li>Cloud/Cluster: The broader infrastructure context (e.g., a Kubernetes cluster or AWS region).</li>
     * </ul>
     * @return typed entities belonging to this resource; never {@code null}, may be empty.
     */
    List<Entity> getEntities();

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
     * Merges this resource with another one according to the OpenTelemetry Resource merge model.
     *
     * @param other incoming resource
     * @return merged resource
     */
    Resource merge(Resource other);
}
