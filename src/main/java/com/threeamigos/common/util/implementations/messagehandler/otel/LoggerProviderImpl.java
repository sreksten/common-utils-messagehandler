package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.CorrelationResolver;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Logger;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LoggerProvider;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Stateful provider for creating and caching {@link Logger} instances by instrumentation scope identity.
 * <p>
 * Provider-level defaults and correlation resolver are applied when log records are emitted.
 *
 * @author Stefano Reksten
 */
public class LoggerProviderImpl implements LoggerProvider {

    private static final LoggerProviderImpl INSTANCE = new LoggerProviderImpl();
    private static final LogRecordFactory UNUSED_RECORD_FACTORY = new LogRecordFactoryImpl();
    private static final Consumer<LogRecord> NO_OP_CONSUMER = new Consumer<LogRecord>() {
        @Override
        public void accept(final LogRecord logRecord) {
            // no-op by default
        }
    };

    private final Map<LoggerKey, Logger> loggersByScope = new ConcurrentHashMap<>();

    private volatile Resource defaultResource;
    private volatile String defaultSchemaUrl;
    private volatile List<KeyValue> defaultCommonAttributes = Collections.emptyList();
    private volatile CorrelationResolver correlationResolver;
    private volatile Consumer<LogRecord> logRecordConsumer = NO_OP_CONSUMER;
    private volatile boolean enabled = true;

    public static LoggerProviderImpl getGlobal() {
        return INSTANCE;
    }

    private LoggerProviderImpl() {
    }

    public static LoggerProviderImpl createProvider() {
        return new LoggerProviderImpl();
    }

    public void setDefaultResource(final @Nullable Resource defaultResource) {
        this.defaultResource = defaultResource;
    }

    public @Nullable Resource getDefaultResource() {
        return defaultResource;
    }

    public void setDefaultSchemaUrl(final @Nullable String defaultSchemaUrl) {
        this.defaultSchemaUrl = normalizeNullable(defaultSchemaUrl);
    }

    public @Nullable String getDefaultSchemaUrl() {
        return defaultSchemaUrl;
    }

    public void setDefaultCommonAttributes(final @Nullable Collection<KeyValue> commonAttributes) {
        List<KeyValue> normalizedAttributes = copyAndNormalizeAttributes(commonAttributes);
        this.defaultCommonAttributes = Collections.unmodifiableList(normalizedAttributes);
    }

    public @Nonnull List<KeyValue> getDefaultCommonAttributes() {
        return defaultCommonAttributes;
    }

    public void setCorrelationResolver(final @Nullable CorrelationResolver correlationResolver) {
        this.correlationResolver = correlationResolver;
    }

    public @Nullable CorrelationResolver getCorrelationResolver() {
        return correlationResolver;
    }

    public void setLogRecordConsumer(final @Nullable Consumer<LogRecord> logRecordConsumer) {
        this.logRecordConsumer = logRecordConsumer == null ? NO_OP_CONSUMER : logRecordConsumer;
    }

    public @Nonnull Consumer<LogRecord> getLogRecordConsumer() {
        return logRecordConsumer;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Logger getLogger(final String name,
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

        LoggerKey key = new LoggerKey(
                normalizedName,
                normalizedVersion,
                resolvedSchemaUrl,
                toAttributeSignature(resolvedAttributes));
        return loggersByScope.computeIfAbsent(key, newLoggerKey -> new LoggerImpl(this, scope));
    }

    LogRecord enrichForLogger(final InstrumentationScope scope, final LogRecord record) {
        CorrelationResolver resolver = correlationResolver;
        EnrichingLogRecordFactory enricher;
        if (resolver == null) {
            enricher = new EnrichingLogRecordFactory(
                    UNUSED_RECORD_FACTORY,
                    defaultResource,
                    scope,
                    defaultCommonAttributes);
        } else {
            enricher = new EnrichingLogRecordFactory(
                    UNUSED_RECORD_FACTORY,
                    defaultResource,
                    scope,
                    defaultCommonAttributes,
                    null,
                    resolver::resolveSpanContext,
                    resolver::resolveInstrumentationScope);
        }
        return enricher.enrichRecord(record);
    }

    void emit(final LogRecord record) {
        try {
            logRecordConsumer.accept(record);
        } catch (RuntimeException ex) {
            OpenTelemetryAttributeValidator.report(
                    MessageHandlerResourceBundle.format("failedToEmitLogRecord", ex.getMessage()),
                    ex);
        }
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

    private static List<KeyValue> copyAndNormalizeAttributes(final Collection<KeyValue> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Collections.emptyList();
        }
        List<KeyValue> asList = new ArrayList<>(attributes);
        return OpenTelemetryAttributeValidator.copyAndValidateKeyValuesLenient(
                asList,
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

    private static final class LoggerKey {
        private final String instrumentationName;
        private final String version;
        private final String schemaUrl;
        private final List<String> attributeSignature;

        private LoggerKey(final String instrumentationName,
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
            if (!(o instanceof LoggerKey)) {
                return false;
            }
            LoggerKey loggerKey = (LoggerKey) o;
            return Objects.equals(instrumentationName, loggerKey.instrumentationName)
                    && Objects.equals(version, loggerKey.version)
                    && Objects.equals(schemaUrl, loggerKey.schemaUrl)
                    && Objects.equals(attributeSignature, loggerKey.attributeSignature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(instrumentationName, version, schemaUrl, attributeSignature);
        }
    }
}
