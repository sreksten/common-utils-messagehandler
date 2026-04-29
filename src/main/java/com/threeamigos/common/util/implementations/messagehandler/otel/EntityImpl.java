package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.RawJsonRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Entity;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An immutable implementation of an {@link Entity}.
 *
 * @author Stefano Reksten
 */
final class EntityImpl implements Entity {

    private static final RawJsonRecordFormatter RAW_JSON_RECORD_FORMATTER = new RawJsonRecordFormatter();
    private static final Instant CANONICAL_TIMESTAMP = Instant.EPOCH;
    private static final String UNKNOWN_TYPE = "unknown";
    private static final String UNKNOWN_ID_KEY = "unknown_id";
    private static final String UNKNOWN_ID_VALUE = "unknown";
    private static final Pattern ENTITY_TYPE_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9._-]*");

    private final String type;
    private final String schemaUrl;
    private final List<KeyValue> id;
    private final List<KeyValue> description;

    EntityImpl(final String type,
               final String schemaUrl,
               final List<KeyValue> id,
               final List<KeyValue> description) {
        this.type = normalizeType(type);
        this.schemaUrl = normalizeSchemaUrl(schemaUrl);
        this.id = Collections.unmodifiableList(sanitizeKeyValues(
                id,
                MessageHandlerResourceBundle.get("entityIdFieldName"),
                true));
        this.description = Collections.unmodifiableList(sanitizeKeyValues(
                description,
                MessageHandlerResourceBundle.get("entityDescriptionFieldName"),
                false));
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
        if (other == null) {
            return this;
        }

        final String otherType;
        final String otherSchemaUrl;
        final List<KeyValue> otherId;
        final List<KeyValue> otherDescription;
        try {
            otherType = normalizeTypeForComparison(other.getType());
            otherSchemaUrl = normalizeSchemaUrl(other.getSchemaUrl());
            otherId = sanitizeKeyValues(
                    other.getId(),
                    MessageHandlerResourceBundle.get("entityIdFieldName"),
                    false);
            otherDescription = sanitizeKeyValues(
                    other.getDescription(),
                    MessageHandlerResourceBundle.get("entityDescriptionFieldName"),
                    false);
        } catch (RuntimeException e) {
            logWarning(e.getMessage());
            return this;
        }

        if (!isCompatibleWith(otherType, otherSchemaUrl, otherId)) {
            return this;
        }
        if (otherDescription.isEmpty()) {
            return this;
        }

        LinkedHashMap<String, KeyValue> merged = descriptionByKey(description);
        boolean changed = false;
        for (KeyValue incoming : otherDescription) {
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

    private boolean isCompatibleWith(final String otherType,
                                     final String otherSchemaUrl,
                                     final List<KeyValue> otherId) {
        return Objects.equals(type, otherType) &&
                Objects.equals(schemaUrl, otherSchemaUrl) &&
                canonicalKeyValueMap(id).equals(canonicalKeyValueMap(otherId));
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

    private static String canonicalAnyValue(final AnyValue value) {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(CANONICAL_TIMESTAMP);
        record.setBody(value);
        return RAW_JSON_RECORD_FORMATTER.format(record);
    }

    private static String normalizeType(final String rawType) {
        if (rawType == null) {
            logBundledWarning("entityTypeMustNotBeNull");
            return UNKNOWN_TYPE;
        }
        String normalizedType = rawType.trim();
        if (normalizedType.isEmpty()) {
            logBundledWarning("entityTypeMustNotBeEmpty");
            return UNKNOWN_TYPE;
        }
        if (!ENTITY_TYPE_PATTERN.matcher(normalizedType).matches()) {
            OpenTelemetryAttributeValidator.reportBundled(
                    "entityTypeInvalidFallback",
                    normalizedType,
                    UNKNOWN_TYPE);
            return UNKNOWN_TYPE;
        }
        return normalizedType;
    }

    private static String normalizeTypeForComparison(final String rawType) {
        if (rawType == null || rawType.trim().isEmpty()) {
            return null;
        }
        String normalizedType = rawType.trim();
        return ENTITY_TYPE_PATTERN.matcher(normalizedType).matches() ? normalizedType : null;
    }

    private static String normalizeSchemaUrl(final String rawSchemaUrl) {
        if (rawSchemaUrl == null) {
            return null;
        }
        String normalizedSchemaUrl = rawSchemaUrl.trim();
        if (normalizedSchemaUrl.isEmpty()) {
            OpenTelemetryAttributeValidator.reportBundled(
                    "builderFieldMustNotBeBlank",
                    MessageHandlerResourceBundle.get("entitySchemaUrlFieldName"));
            return null;
        }
        try {
            new URI(normalizedSchemaUrl);
        } catch (URISyntaxException e) {
            OpenTelemetryAttributeValidator.reportBundled(
                    "entitySchemaUrlInvalidTreatAbsent",
                    normalizedSchemaUrl);
            return null;
        }
        return normalizedSchemaUrl;
    }

    private static List<KeyValue> sanitizeKeyValues(final List<KeyValue> keyValues,
                                                    final String fieldName,
                                                    final boolean enforceNonEmpty) {
        List<KeyValue> copy = OpenTelemetryAttributeValidator.copyAndValidateKeyValuesLenient(keyValues, fieldName);

        if (enforceNonEmpty && copy.isEmpty()) {
            logBundledWarning("entityIdMustNotBeEmpty");
            copy.add(KeyValueFactory.of(UNKNOWN_ID_KEY, AnyValueFactory.ofString(UNKNOWN_ID_VALUE)));
        }
        return copy;
    }

    private static void logBundledWarning(final String messageKey) {
        OpenTelemetryAttributeValidator.reportBundled(messageKey);
    }

    private static void logWarning(final String message) {
        OpenTelemetryAttributeValidator.report(message);
    }
}
