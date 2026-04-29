package com.threeamigos.common.util.interfaces.messagehandler.otel;

import java.util.List;

/**
 * Represents an OpenTelemetry Entity.
 * <p>
 * Specification references:
 * <ul>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/entities/data-model/">OpenTelemetry Entity
 *   Data Model</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/common/">OpenTelemetry Common Concepts
 *   (Attribute)</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/entities/sdk/">OpenTelemetry Entities SDK</a></li>
 * </ul>
 * <p>
 * The data model defines:
 * <ul>
 *     <li>a required non-empty {@code type}</li>
 *     <li>a required non-empty ID map of identifying attributes</li>
 *     <li>an optional Description map of non-identifying attributes</li>
 * </ul>
 * This library represents the map-like attribute fields as {@link List}&lt;{@link KeyValue}&gt;
 * with unique keys.
 *
 * @author Stefano Reksten
 */
public interface Entity {

    /**
     * @return the entity type (for example {@code "service"} or {@code "host"}); never {@code null}.
     */
    String getType();

    /**
     * @return the entity Schema URL used for semantic conventions, or {@code null} if not set.
     */
    String getSchemaUrl();

    /**
     * @return identifying attributes (the Entity ID); never {@code null} and never empty.
     */
    List<KeyValue> getId();

    /**
     * @return descriptive (non-identifying) attributes; never {@code null}, may be empty.
     */
    List<KeyValue> getDescription();

    /**
     * Merges this entity with another entity according to the OpenTelemetry Entity merge model.
     * <p>
     * Entities can merge only when they are compatible (same {@code type}, same {@code schemaUrl},
     * and same ID key-value pairs). If incompatible, this entity is returned unchanged.
     * <p>
     * When compatible, the resulting description is the union of both descriptions; when a key
     * appears in both, the value from {@code other} is used.
     *
     * @param other the entity to merge into this one
     * @return merged entity when compatible; otherwise this entity
     */
    Entity merge(Entity other);
}
