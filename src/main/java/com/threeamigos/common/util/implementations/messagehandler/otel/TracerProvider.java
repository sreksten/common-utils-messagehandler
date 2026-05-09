package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Filter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanDispatcher;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Stateful provider for creating and caching {@link Tracer} instances by instrumentation scope identity.
 * <p>
 * Provider-level defaults can be configured and reused by enrichment helpers.
 *
 * @author Stefano Reksten
 */
public class TracerProvider {

    private static final TracerProvider INSTANCE = new TracerProvider();

    private final Map<TracerKey, Tracer> tracersByScope = new ConcurrentHashMap<>();

    private volatile Resource defaultResource;
    private volatile String defaultSchemaUrl;
    private volatile List<KeyValue> defaultCommonAttributes = Collections.emptyList();
    private final CorrelationResolver correlationResolver = new CorrelationResolver();
    private volatile String defaultFilePath = "message-handler.log";
    private volatile SpanDispatcher defaultSpanDispatcher;

    public static TracerProvider getGlobal() {
        return INSTANCE;
    }

    private TracerProvider() {
    }

    public static TracerProvider createProvider() {
        return new TracerProvider();
    }

    public static TracerProviderBuilder builder() {
        return new TracerProviderBuilder();
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

    public void setDefaultFilePath(final @Nullable String defaultFilePath) {
        String normalized = normalizeNullable(defaultFilePath);
        this.defaultFilePath = normalized == null ? "message-handler.log" : normalized;
    }

    public @Nonnull String getDefaultFilePath() {
        return defaultFilePath;
    }

    public void setDefaultSpanDispatcher(final @Nullable SpanDispatcher defaultSpanDispatcher) {
        this.defaultSpanDispatcher = defaultSpanDispatcher;
    }

    public @Nullable SpanDispatcher getDefaultSpanDispatcher() {
        return defaultSpanDispatcher;
    }

    /**
     * Returns a tracer instance with the given instrumentation name and version.
     * <p>
     * This overload resolves to {@link #getTracer(String, String, String, Collection)}
     * with {@code schemaUrl = null} and {@code attributes = Collections.emptyList()}.
     *
     * @param instrumentationName The name of the instrumentation. Should not be null or empty.
     * @param version The version of the instrumentation.
     * @return A tracer instance.
     */
    public Tracer getTracer(final @Nullable String instrumentationName,
                            final @Nullable String version) {
        return getTracer(instrumentationName, version, null, Collections.emptyList());
    }

    /**
     * Returns a tracer with an optional tracer-level filter.
     * <p>
     * Filter-aware tracers are not cached: each invocation returns an isolated tracer instance
     * carrying its own filter reference.
     *
     * @param instrumentationName The name of the instrumentation.
     * @param filter filter used when creating message handlers from the tracer
     * @return A tracer instance.
     */
    public Tracer getTracer(final @Nullable String instrumentationName,
                            final @Nullable Filter filter) {
        return getTracer(instrumentationName, null, null, Collections.emptyList(), filter);
    }

    /**
     * Returns a tracer with version and optional tracer-level filter.
     * <p>
     * Filter-aware tracers are not cached: each invocation returns an isolated tracer instance
     * carrying its own filter reference.
     *
     * @param instrumentationName The name of the instrumentation.
     * @param version The version of the instrumentation.
     * @param filter filter used when creating message handlers from the tracer
     * @return A tracer instance.
     */
    public Tracer getTracer(final @Nullable String instrumentationName,
                            final @Nullable String version,
                            final @Nullable Filter filter) {
        return getTracer(instrumentationName, version, null, Collections.emptyList(), filter);
    }

    /**
     * Returns a tracer instance with the given instrumentation name, version, schema URL, and attributes.
     * @param instrumentationName The name of the instrumentation. Should not be null or empty. As per spec, if it is
     *                            null, it will be treated as an empty string.
     * @param version The version of the instrumentation.
     * @param schemaUrl The schema URL for the instrumentation.
     * @param attributes The attributes associated with the instrumentation.
     * @return A tracer instance or null if not available.
     */
    public Tracer getTracer(final @Nullable String instrumentationName,
                            final @Nullable String version,
                            final @Nullable String schemaUrl,
                            final @Nullable Collection<KeyValue> attributes) {
        String normalizedInstrumentationName = normalizeInstrumentationName(instrumentationName);
        String normalizedVersion = normalizeNullable(version);
        String normalizedSchemaUrl = normalizeNullable(schemaUrl);
        final String resolvedSchemaUrl = normalizedSchemaUrl == null ? defaultSchemaUrl : normalizedSchemaUrl;
        final List<KeyValue> resolvedAttributes = copyAndNormalizeAttributes(attributes);
        final String resolvedInstrumentationName = normalizedInstrumentationName;
        final String resolvedVersion = normalizedVersion;
        TracerKey key = new TracerKey(
                resolvedInstrumentationName,
                resolvedVersion,
                resolvedSchemaUrl,
                toAttributeSignature(resolvedAttributes));
        return tracersByScope.computeIfAbsent(key, ignored ->
                new TracerImpl(
                        this,
                        resolvedInstrumentationName,
                        resolvedVersion,
                        resolvedSchemaUrl,
                        resolvedAttributes));
    }

    public Tracer getTracer(final @Nullable String instrumentationName,
                            final @Nullable String version,
                            final @Nullable String schemaUrl,
                            final @Nullable Collection<KeyValue> attributes,
                            final @Nullable Filter filter) {
        String normalizedInstrumentationName = normalizeInstrumentationName(instrumentationName);
        String normalizedVersion = normalizeNullable(version);
        String normalizedSchemaUrl = normalizeNullable(schemaUrl);
        final String resolvedSchemaUrl = normalizedSchemaUrl == null ? defaultSchemaUrl : normalizedSchemaUrl;
        final List<KeyValue> resolvedAttributes = copyAndNormalizeAttributes(attributes);
        return new TracerImpl(
                this,
                normalizedInstrumentationName,
                normalizedVersion,
                resolvedSchemaUrl,
                resolvedAttributes,
                true,
                filter);
    }

    public Tracer getTracer(final @Nullable InstrumentationScope instrumentationScope) {
        if (instrumentationScope == null) {
            return getTracer(null, null, null, null);
        }
        return getTracer(
                instrumentationScope.getName(),
                instrumentationScope.getVersion(),
                instrumentationScope.getSchemaUrl(),
                instrumentationScope.getAttributes());
    }

    public Tracer getTracer(final @Nullable InstrumentationScope instrumentationScope,
                            final @Nullable Filter filter) {
        if (instrumentationScope == null) {
            return getTracer(null, null, null, null, filter);
        }
        return getTracer(
                instrumentationScope.getName(),
                instrumentationScope.getVersion(),
                instrumentationScope.getSchemaUrl(),
                instrumentationScope.getAttributes(),
                filter);
    }

    LogRecordFactory getDefaultEnrichingFactory(final @Nullable InstrumentationScope scope) {
        return getLogRecordFactory(scope);
    }

    TracerMessageHandlerFactory buildMessageHandlerFactory(final @Nullable InstrumentationScope scope) {
        return buildMessageHandlerFactory(scope, null);
    }

    TracerMessageHandlerFactory buildMessageHandlerFactory(final @Nullable InstrumentationScope scope,
                                                           final @Nullable String filePath) {
        String normalizedFilePath = normalizeNullable(filePath);
        String resolvedFilePath = normalizedFilePath == null ? defaultFilePath : normalizedFilePath;
        return new TracerMessageHandlerFactory(getDefaultEnrichingFactory(scope), resolvedFilePath);
    }

    public LogRecordFactory getLogRecordFactory() {
        return getLogRecordFactory(null);
    }

    public LogRecordFactory getLogRecordFactory(final @Nullable InstrumentationScope scope) {
        return getLogRecordFactory(scope, null);
    }

    public LogRecordFactory getLogRecordFactory(final @Nullable InstrumentationScope scope,
                                                final @Nullable SpanContext explicitSpanContext) {
        return new EnrichingLogRecordFactory(
                new LogRecordFactoryImpl(),
                defaultResource,
                scope,
                defaultCommonAttributes,
                explicitSpanContext,
                correlationResolver::resolveSpanContext,
                correlationResolver::resolveInstrumentationScope,
                correlationResolver::resolveSpan);
    }

    /**
     * @deprecated use {@link #getLogRecordFactory(InstrumentationScope)}.
     */
    @Deprecated
    public LogRecordFactory enrichingLogRecordFactory(final @Nonnull LogRecordFactory delegate,
                                                      final @Nullable InstrumentationScope scope) {
        return getLogRecordFactory(scope);
    }

    /**
     * @deprecated use {@link #getLogRecordFactory(InstrumentationScope, SpanContext)}.
     */
    @Deprecated
    public LogRecordFactory enrichingLogRecordFactory(final @Nonnull LogRecordFactory delegate,
                                                      final @Nullable InstrumentationScope scope,
                                                      final @Nullable SpanContext explicitSpanContext) {
        return getLogRecordFactory(scope, explicitSpanContext);
    }

    public Runnable wrap(final Runnable task) {
        return correlationResolver.wrap(task);
    }

    public <V> Callable<V> wrap(final Callable<V> task) {
        return correlationResolver.wrap(task);
    }

    public Executor contextAwareExecutor(final Executor delegate) {
        return correlationResolver.contextAwareExecutor(delegate);
    }

    public ExecutorService contextAwareExecutorService(final ExecutorService delegate) {
        return correlationResolver.contextAwareExecutorService(delegate);
    }

    public CorrelationScope attachCorrelation(final @Nullable SpanContext spanContext,
                                              final @Nullable InstrumentationScope instrumentationScope) {
        return new CorrelationScope(correlationResolver.attach(spanContext, instrumentationScope));
    }

    public CorrelationScope attachCorrelation(final @Nullable SpanContext spanContext) {
        return attachCorrelation(spanContext, null);
    }

    public void clearCorrelation() {
        correlationResolver.clear();
    }

    CorrelationResolver getCorrelationResolver() {
        return correlationResolver;
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

    private static final class TracerKey {
        private final String instrumentationName;
        private final String version;
        private final String schemaUrl;
        private final List<String> attributeSignature;

        private TracerKey(final String instrumentationName,
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
            if (!(o instanceof TracerKey)) {
                return false;
            }
            TracerKey tracerKey = (TracerKey) o;
            return Objects.equals(instrumentationName, tracerKey.instrumentationName)
                    && Objects.equals(version, tracerKey.version)
                    && Objects.equals(schemaUrl, tracerKey.schemaUrl)
                    && Objects.equals(attributeSignature, tracerKey.attributeSignature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(instrumentationName, version, schemaUrl, attributeSignature);
        }
    }

    public static final class CorrelationScope implements AutoCloseable {
        private final CorrelationResolver.ScopeToken token;

        private CorrelationScope(final CorrelationResolver.ScopeToken token) {
            this.token = token;
        }

        @Override
        public void close() {
            token.close();
        }
    }

}
