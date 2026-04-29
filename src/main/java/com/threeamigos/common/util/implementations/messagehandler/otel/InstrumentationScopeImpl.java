package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.util.Collections;
import java.util.List;

/**
 * Immutable implementation of {@link InstrumentationScope}.
 * <p>
 * All fields are set at construction time.
 * Scope attributes are internally capped to the default OpenTelemetry attribute count limit and
 * any overflow is reported through {@link #getDroppedAttributesCount()}.
 *
 * @author Stefano Reksten
 */
final class InstrumentationScopeImpl implements InstrumentationScope {

    private final String name;
    private final String version;
    private final String schemaUrl;
    private final List<KeyValue> attributes;
    private final int droppedAttributesCount;

    public InstrumentationScopeImpl(final String name,
                                    final String version,
                                    final String schemaUrl,
                                    final List<KeyValue> attributes) {
        OpenTelemetryAttributeValidator.ValidationResult validationResult =
                OpenTelemetryAttributeValidator.copyValidateAndLimitKeyValues(
                        attributes,
                        MessageHandlerResourceBundle.get("scopeAttributesFieldName")
                );
        this.name = name;
        this.version = version;
        this.schemaUrl = schemaUrl;
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
}
