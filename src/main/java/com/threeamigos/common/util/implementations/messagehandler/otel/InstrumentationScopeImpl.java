package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Immutable implementation of {@link InstrumentationScope}.
 * <p>
 * All fields are set at construction time. Name, version, schemaUrl, and attributes are optional
 * in this model; absent or blank values are represented as {@code null}.
 * Scope attributes are internally capped to the default OpenTelemetry attribute count limit and
 * any overflow is reported through {@link #getDroppedAttributesCount()}.
 * <p>
 * Specification references:
 * <a href="https://opentelemetry.io/docs/specs/otel/common/instrumentation-scope/">OpenTelemetry Instrumentation Scope</a>,
 * <a href="https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/common/v1/common.proto">OTLP common.proto (InstrumentationScope)</a>,
 * <a href="https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/logs/v1/logs.proto">OTLP logs.proto (ScopeLogs)</a>.
 *
 * @author Stefano Reksten
 */
final class InstrumentationScopeImpl implements InstrumentationScope {

    private final String name;
    private final String version;
    private final String schemaUrl;
    private final List<KeyValue> attributes;
    private final int droppedAttributesCount;

    public InstrumentationScopeImpl(final @Nullable String name,
                                    final @Nullable String version,
                                    final @Nullable String schemaUrl,
                                    final @Nullable List<KeyValue> attributes) {
        OpenTelemetryAttributeValidator.ValidationResult validationResult =
                OpenTelemetryAttributeValidator.copyValidateAndLimitKeyValues(
                        attributes,
                        MessageHandlerResourceBundle.get("scopeAttributesFieldName")
                );
        this.name = normalizeName(name);
        this.version = normalizeVersion(version);
        this.schemaUrl = normalizeSchemaUrl(schemaUrl);
        this.attributes = Collections.unmodifiableList(validationResult.getAttributes());
        this.droppedAttributesCount = validationResult.getDroppedAttributesCount();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getSchemaUrl() {
        return schemaUrl;
    }

    @Override
    public List<KeyValue> getAttributes() {
        return attributes;
    }

    @Override
    public int getDroppedAttributesCount() {
        return droppedAttributesCount;
    }

    private static String normalizeName(final String rawName) {
        if (rawName == null) {
            return null;
        }
        String normalizedName = rawName.trim();
        return normalizedName.isEmpty() ? null : normalizedName;
    }

    private static String normalizeVersion(final String rawVersion) {
        if (rawVersion == null) {
            return null;
        }
        String normalizedVersion = rawVersion.trim();
        return normalizedVersion.isEmpty() ? null : normalizedVersion;
    }

    private static String normalizeSchemaUrl(final String rawSchemaUrl) {
        if (rawSchemaUrl == null) {
            return null;
        }
        String normalizedSchemaUrl = rawSchemaUrl.trim();
        return normalizedSchemaUrl.isEmpty() ? null : normalizedSchemaUrl;
    }
}
