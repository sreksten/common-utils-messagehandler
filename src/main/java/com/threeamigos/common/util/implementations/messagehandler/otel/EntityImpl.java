package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.RawJsonRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Entity;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable implementation of {@link Entity}.
 * All fields are set at construction time.
 *
 * @author Stefano Reksten
 */
final class EntityImpl implements Entity {

    private static final RawJsonRecordFormatter RAW_JSON_RECORD_FORMATTER = new RawJsonRecordFormatter();
    private static final Instant CANONICAL_TIMESTAMP = Instant.EPOCH;

    private final String type;
    private final String schemaUrl;
    private final List<KeyValue> id;
    private final List<KeyValue> description;

    EntityImpl(final String type,
               final String schemaUrl,
               final List<KeyValue> id,
               final List<KeyValue> description) {
        this.type = Objects.requireNonNull(type, MessageHandlerResourceBundle.get("entityTypeMustNotBeNull"));
        if (type.trim().isEmpty()) {
            throw new IllegalArgumentException(MessageHandlerResourceBundle.get("entityTypeMustNotBeEmpty"));
        }

        this.schemaUrl = schemaUrl;

        List<KeyValue> validatedId = OpenTelemetryAttributeValidator.copyAndValidateKeyValues(
                id,
                MessageHandlerResourceBundle.get("entityIdFieldName"));
        if (validatedId.isEmpty()) {
            throw new IllegalArgumentException(MessageHandlerResourceBundle.get("entityIdMustNotBeEmpty"));
        }
        this.id = Collections.unmodifiableList(validatedId);

        this.description = Collections.unmodifiableList(OpenTelemetryAttributeValidator.copyAndValidateKeyValues(
                description,
                MessageHandlerResourceBundle.get("entityDescriptionFieldName")));
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getSchemaUrl() {
        return schemaUrl;
    }

    @Override
    public List<KeyValue> getId() {
        return id;
    }

    @Override
    public List<KeyValue> getDescription() {
        return description;
    }

    @Override
    public Entity merge(final Entity other) {
        Objects.requireNonNull(other, MessageHandlerResourceBundle.get("entityMustNotBeNull"));
        if (!isCompatibleWith(other)) {
            return this;
        }
        if (other.getDescription().isEmpty()) {
            return this;
        }

        LinkedHashMap<String, KeyValue> merged = descriptionByKey(description);
        boolean changed = false;
        for (KeyValue incoming : other.getDescription()) {
            KeyValue current = merged.get(incoming.getKey());
            if (current == null) {
                merged.put(incoming.getKey(), incoming);
                changed = true;
                continue;
            }
            if (!canonicalAnyValue(current.getValue()).equals(canonicalAnyValue(incoming.getValue()))) {
                merged.put(incoming.getKey(), incoming);
                changed = true;
            }
        }

        if (!changed) {
            return this;
        }

        return new EntityImpl(type, schemaUrl, id, new ArrayList<>(merged.values()));
    }

    private boolean isCompatibleWith(final Entity other) {
        return Objects.equals(type, other.getType()) &&
                Objects.equals(schemaUrl, other.getSchemaUrl()) &&
                canonicalKeyValueMap(id).equals(canonicalKeyValueMap(other.getId()));
    }

    private static LinkedHashMap<String, KeyValue> descriptionByKey(final List<KeyValue> values) {
        LinkedHashMap<String, KeyValue> byKey = new LinkedHashMap<>(values.size());
        for (KeyValue kv : values) {
            byKey.put(kv.getKey(), kv);
        }
        return byKey;
    }

    private static Map<String, String> canonicalKeyValueMap(final List<KeyValue> values) {
        Map<String, String> canonical = new LinkedHashMap<>(values.size());
        for (KeyValue kv : values) {
            canonical.put(kv.getKey(), canonicalAnyValue(kv.getValue()));
        }
        return canonical;
    }

    private static String canonicalAnyValue(final com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue value) {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(CANONICAL_TIMESTAMP);
        record.setBody(value);
        return RAW_JSON_RECORD_FORMATTER.format(record);
    }
}
