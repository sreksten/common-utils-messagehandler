package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.util.Objects;

/**
 * Immutable implementation of {@link KeyValue}.
 * <p>
 * Specification references:
 * <a href="https://opentelemetry.io/docs/specs/otel/common/#attribute">OpenTelemetry Common: Attribute</a>,
 * <a href="https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/common/v1/common.proto">OTLP common.proto (KeyValue)</a>.
 *
 * @author Stefano Reksten
 */
final class KeyValueImpl implements KeyValue {

    private final String key;
    private final AnyValue value;

    /**
     * Creates a key-value pair using an explicit attribute key string.
     *
     * @param key   attribute key
     * @param value attribute value
     */
    KeyValueImpl(final String key, final AnyValue value) {
        this.key = normalizeKey(key);
        this.value = normalizeValue(value);
    }

    /**
     * Creates a key-value pair using a known OpenTelemetry {@link OTelTags} key.
     *
     * @param name  known OpenTelemetry attribute/resource name
     * @param value attribute value
     */
    public KeyValueImpl(final OTelTags name, final AnyValue value) {
        this(resolveTagValue(name), value);
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public AnyValue getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof KeyValueImpl)) {
            return false;
        }
        KeyValueImpl other = (KeyValueImpl) obj;
        return Objects.equals(key, other.key) && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    private static String resolveTagValue(final OTelTags name) {
        if (name == null) {
            OpenTelemetryAttributeValidator.handleBundled("keyMustNotBeNull");
            return "unknown";
        }
        return normalizeKey(name.getValue());
    }

    private static String normalizeKey(final String key) {
        if (key == null) {
            OpenTelemetryAttributeValidator.handleBundled("keyMustNotBeNull");
            return "unknown";
        }
        if (key.trim().isEmpty()) {
            OpenTelemetryAttributeValidator.handleBundled("keyMustNotBeEmpty");
            return "unknown";
        }
        return key;
    }

    private static AnyValue normalizeValue(final AnyValue value) {
        if (value == null) {
            OpenTelemetryAttributeValidator.handleBundled("valueMustNotBeNull");
            return AnyValueFactory.empty();
        }
        return value;
    }
}
