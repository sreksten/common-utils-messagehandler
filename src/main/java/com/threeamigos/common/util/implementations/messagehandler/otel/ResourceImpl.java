package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Entity;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * An immutable implementation of a {@link Resource}.
 * <p>
 * All fields are set at construction time.
 *
 * @author Stefano Reksten
 */
final class ResourceImpl implements Resource {

    private static final String RESOURCE_ATTRIBUTES_FIELD_NAME = MessageHandlerResourceBundle.get("resourceAttributesFieldName");
    private static final String RESOURCE_ENTITIES_FIELD_NAME = MessageHandlerResourceBundle.get("resourceEntitiesFieldName");

    private final String schemaUrl;
    private final List<Entity> entities;
    private final List<KeyValue> attributes;

    ResourceImpl(final String schemaUrl,
                 final List<Entity> entities,
                 final List<KeyValue> attributes) {
        this.entities = Collections.unmodifiableList(normalizeEntities(entities));
        this.attributes = Collections.unmodifiableList(removeEntityCoveredAttributes(
                OpenTelemetryAttributeValidator.copyAndValidateKeyValues(attributes, RESOURCE_ATTRIBUTES_FIELD_NAME),
                this.entities));
        this.schemaUrl = resolveSchemaUrl(schemaUrl, this.entities);
    }

    @Override
    public String getSchemaUrl() {
        return schemaUrl;
    }

    @Override
    public List<Entity> getEntities() {
        return entities;
    }

    @Override
    public List<KeyValue> getAttributes() {
        return attributes;
    }

    @Override
    public Resource merge(final Resource other) {
        if (other == null) {
            OpenTelemetryAttributeValidator.handleBundled("resourceMustNotBeNull");
            return this;
        }
        List<Entity> mergedEntities = mergeEntities(entities, other.getEntities());
        List<KeyValue> mergedAttributes = mergeLooseAttributes(attributes, other.getAttributes(), mergedEntities);
        String fallbackSchemaUrl = resolveMergedSchemaUrl(schemaUrl, other.getSchemaUrl());
        return new ResourceImpl(fallbackSchemaUrl, mergedEntities, mergedAttributes);
    }

    private static List<Entity> normalizeEntities(final List<Entity> entities) {
        if (entities == null) {
            return new ArrayList<>();
        }

        LinkedHashMap<String, Entity> byType = new LinkedHashMap<>();
        int index = 0;
        for (Entity entity : entities) {
            if (entity == null) {
                OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                        "fieldContainsNullElementAtIndex",
                        RESOURCE_ENTITIES_FIELD_NAME,
                        index));
                index++;
                continue;
            }
            Entity canonical = new EntityImpl(entity.getType(), entity.getSchemaUrl(), entity.getId(), entity.getDescription());
            Entity existing = byType.get(canonical.getType());
            if (existing == null) {
                byType.put(canonical.getType(), canonical);
            } else {
                byType.put(canonical.getType(), existing.merge(canonical));
            }
            index++;
        }
        return removeConflictingEntities(new ArrayList<>(byType.values()));
    }

    private static List<Entity> mergeEntities(final List<Entity> currentEntities,
                                              final List<Entity> incomingEntities) {
        LinkedHashMap<String, Entity> byType = new LinkedHashMap<>();
        for (Entity entity : normalizeEntities(currentEntities)) {
            byType.put(entity.getType(), entity);
        }

        for (Entity entity : normalizeEntities(incomingEntities)) {
            Entity existing = byType.get(entity.getType());
            if (existing == null) {
                byType.put(entity.getType(), entity);
            } else {
                byType.put(entity.getType(), existing.merge(entity));
            }
        }
        return removeConflictingEntities(new ArrayList<>(byType.values()));
    }

    private static List<KeyValue> mergeLooseAttributes(final List<KeyValue> currentAttributes,
                                                       final List<KeyValue> incomingAttributes,
                                                       final List<Entity> mergedEntities) {
        LinkedHashMap<String, KeyValue> mergedByKey = new LinkedHashMap<>();
        for (KeyValue keyValue : OpenTelemetryAttributeValidator.copyAndValidateKeyValues(
                currentAttributes,
                RESOURCE_ATTRIBUTES_FIELD_NAME)) {
            mergedByKey.put(keyValue.getKey(), keyValue);
        }
        for (KeyValue keyValue : OpenTelemetryAttributeValidator.copyAndValidateKeyValues(
                incomingAttributes,
                RESOURCE_ATTRIBUTES_FIELD_NAME)) {
            mergedByKey.put(keyValue.getKey(), keyValue);
        }
        return removeEntityCoveredAttributes(new ArrayList<>(mergedByKey.values()), mergedEntities);
    }

    private static List<Entity> removeConflictingEntities(final List<Entity> candidates) {
        List<Entity> accepted = new ArrayList<>(candidates.size());
        Set<String> usedKeys = new HashSet<>();
        for (Entity entity : candidates) {
            if (hasAnyEntityKeyConflict(entity, usedKeys)) {
                continue;
            }
            accepted.add(entity);
            collectEntityKeys(entity, usedKeys);
        }
        return accepted;
    }

    private static boolean hasAnyEntityKeyConflict(final Entity entity, final Set<String> usedKeys) {
        for (KeyValue kv : safeEntityValues(entity, true)) {
            if (kv == null || kv.getKey() == null) {
                OpenTelemetryAttributeValidator.handleBundled("entityIdContainsNullKeyValueEntry");
                continue;
            }
            if (usedKeys.contains(kv.getKey())) {
                return true;
            }
        }
        for (KeyValue kv : safeEntityValues(entity, false)) {
            if (kv == null || kv.getKey() == null) {
                OpenTelemetryAttributeValidator.handleBundled("entityDescriptionContainsNullKeyValueEntry");
                continue;
            }
            if (usedKeys.contains(kv.getKey())) {
                return true;
            }
        }
        return false;
    }

    private static List<KeyValue> removeEntityCoveredAttributes(final List<KeyValue> candidates,
                                                                final List<Entity> entities) {
        if (candidates.isEmpty() || entities.isEmpty()) {
            return candidates;
        }
        Set<String> entityKeys = new HashSet<>();
        for (Entity entity : entities) {
            collectEntityKeys(entity, entityKeys);
        }

        List<KeyValue> filtered = new ArrayList<>(candidates.size());
        for (KeyValue keyValue : candidates) {
            if (!entityKeys.contains(keyValue.getKey())) {
                filtered.add(keyValue);
            }
        }
        return filtered;
    }

    private static void collectEntityKeys(final Entity entity, final Set<String> target) {
        for (KeyValue kv : safeEntityValues(entity, true)) {
            if (kv == null || kv.getKey() == null) {
                OpenTelemetryAttributeValidator.handleBundled("entityIdContainsNullKeyValueEntry");
                continue;
            }
            target.add(kv.getKey());
        }
        for (KeyValue kv : safeEntityValues(entity, false)) {
            if (kv == null || kv.getKey() == null) {
                OpenTelemetryAttributeValidator.handleBundled("entityDescriptionContainsNullKeyValueEntry");
                continue;
            }
            target.add(kv.getKey());
        }
    }

    private static String resolveSchemaUrl(final String fallbackSchemaUrl, final List<Entity> entities) {
        if (entities.isEmpty()) {
            return fallbackSchemaUrl;
        }
        String resolved = null;
        for (Entity entity : entities) {
            String entitySchemaUrl = entity.getSchemaUrl();
            if (entitySchemaUrl == null || entitySchemaUrl.isEmpty()) {
                return null;
            }
            if (resolved == null) {
                resolved = entitySchemaUrl;
                continue;
            }
            if (!resolved.equals(entitySchemaUrl)) {
                return null;
            }
        }
        return resolved;
    }

    private static String resolveMergedSchemaUrl(final String currentSchemaUrl,
                                                 final String incomingSchemaUrl) {
        String normalizedCurrent = normalizeMergeSchemaUrl(currentSchemaUrl);
        String normalizedIncoming = normalizeMergeSchemaUrl(incomingSchemaUrl);

        if (normalizedCurrent == null) {
            return normalizedIncoming;
        }
        if (normalizedIncoming == null) {
            return normalizedCurrent;
        }
        if (normalizedCurrent.equals(normalizedIncoming)) {
            return normalizedIncoming;
        }

        OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                "resourceSchemaUrlConflictOnMerge",
                normalizedCurrent,
                normalizedIncoming));
        return null;
    }

    private static String normalizeMergeSchemaUrl(final String schemaUrl) {
        if (schemaUrl == null || schemaUrl.trim().isEmpty()) {
            return null;
        }
        return schemaUrl;
    }

    private static List<KeyValue> safeEntityValues(final Entity entity, final boolean idValues) {
        if (entity == null) {
            OpenTelemetryAttributeValidator.handleBundled("resourceContainsNullEntity");
            return Collections.emptyList();
        }
        List<KeyValue> values = idValues ? entity.getId() : entity.getDescription();
        if (values == null) {
            OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                    "entityContainsNullKeyValueList",
                    idValues
                            ? MessageHandlerResourceBundle.get("entityIdValuesLabel")
                            : MessageHandlerResourceBundle.get("entityDescriptionValuesLabel")));
            return Collections.emptyList();
        }
        return values;
    }
}
