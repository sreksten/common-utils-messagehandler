package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.CorrelationResolver;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Meter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.MetricsProvider;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stateful provider for creating and caching {@link Meter} instances by instrumentation scope identity.
 * <p>
 * The configured {@link CorrelationResolver} is shared configuration intended for future instrument operations.
 *
 * @author Stefano Reksten
 */
public class MetricsProviderImpl implements MetricsProvider {

    private static final MetricsProviderImpl INSTANCE = new MetricsProviderImpl();

    private final Map<MeterKey, Meter> metersByScope = new ConcurrentHashMap<>();

    private volatile String defaultSchemaUrl;
    private volatile CorrelationResolver correlationResolver;

    public static MetricsProviderImpl getGlobal() {
        return INSTANCE;
    }

    private MetricsProviderImpl() {
    }

    public static MetricsProviderImpl createProvider() {
        return new MetricsProviderImpl();
    }

    public void setDefaultSchemaUrl(final @Nullable String defaultSchemaUrl) {
        this.defaultSchemaUrl = normalizeNullable(defaultSchemaUrl);
    }

    public @Nullable String getDefaultSchemaUrl() {
        return defaultSchemaUrl;
    }

    public void setCorrelationResolver(final @Nullable CorrelationResolver correlationResolver) {
        this.correlationResolver = correlationResolver;
    }

    public @Nullable CorrelationResolver getCorrelationResolver() {
        return correlationResolver;
    }

    @Override
    public Meter getMeter(final String name,
                          final String version,
                          final String schemaUrl,
                          final KeyValue... attributes) {
        String normalizedName = normalizeInstrumentationName(name);
        String normalizedVersion = normalizeNullable(version);
        String normalizedSchemaUrl = normalizeNullable(schemaUrl);
        final String resolvedSchemaUrl = normalizedSchemaUrl == null ? defaultSchemaUrl : normalizedSchemaUrl;
        final List<KeyValue> resolvedAttributes = copyAndNormalizeAttributes(
                attributes == null ? Collections.<KeyValue>emptyList() : Arrays.asList(attributes));
        final InstrumentationScope scope = InstrumentationScopeFactory.create(
                normalizedName,
                normalizedVersion,
                resolvedSchemaUrl,
                resolvedAttributes);

        MeterKey key = new MeterKey(
                normalizedName,
                normalizedVersion,
                resolvedSchemaUrl,
                toAttributeSignature(resolvedAttributes));
        return metersByScope.computeIfAbsent(key, meterKey -> new MeterImpl(scope));
    }

    private static String normalizeInstrumentationName(final String instrumentationName) {
        String normalized = normalizeNullable(instrumentationName);
        if (normalized == null) {
            OpenTelemetryAttributeValidator.reportBundled("nullInstrumentationNameProvided");
            return "";
        }
        return normalized;
    }

    private static String normalizeNullable(final String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static List<KeyValue> copyAndNormalizeAttributes(final List<KeyValue> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Collections.emptyList();
        }
        return OpenTelemetryAttributeValidator.copyAndValidateKeyValuesLenient(
                attributes,
                MessageHandlerResourceBundle.get("scopeAttributesFieldName"));
    }

    private static List<String> toAttributeSignature(final List<KeyValue> attributes) {
        if (attributes.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> signature = new ArrayList<>(attributes.size());
        for (KeyValue keyValue : attributes) {
            signature.add(keyValue.getKey() + "=" + anyValueSignature(keyValue.getValue()));
        }
        signature.sort(Comparator.naturalOrder());
        return signature;
    }

    private static String anyValueSignature(final AnyValue value) {
        if (value == null || value.getType() == null) {
            return "EMPTY";
        }
        AnyValue.Type type = value.getType();
        switch (type) {
            case EMPTY:
                return "EMPTY";
            case STRING:
                return "STRING:" + value.asString();
            case BOOL:
                return "BOOL:" + value.asBoolean();
            case INT:
                return "INT:" + value.asLong();
            case DOUBLE:
                return "DOUBLE:" + value.asDouble();
            case BYTES:
                return "BYTES:" + Arrays.toString(value.asBytes());
            case ARRAY:
                return "ARRAY:" + value.asArray();
            case KVLIST:
                return "KVLIST:" + value.asKvList();
            default:
                return type.name();
        }
    }

    private static final class MeterKey {
        private final String instrumentationName;
        private final String version;
        private final String schemaUrl;
        private final List<String> attributeSignature;

        private MeterKey(final String instrumentationName,
                         final String version,
                         final String schemaUrl,
                         final List<String> attributeSignature) {
            this.instrumentationName = instrumentationName;
            this.version = version;
            this.schemaUrl = schemaUrl;
            this.attributeSignature = attributeSignature;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof MeterKey)) {
                return false;
            }
            MeterKey meterKey = (MeterKey) o;
            return Objects.equals(instrumentationName, meterKey.instrumentationName)
                    && Objects.equals(version, meterKey.version)
                    && Objects.equals(schemaUrl, meterKey.schemaUrl)
                    && Objects.equals(attributeSignature, meterKey.attributeSignature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(instrumentationName, version, schemaUrl, attributeSignature);
        }
    }
}
