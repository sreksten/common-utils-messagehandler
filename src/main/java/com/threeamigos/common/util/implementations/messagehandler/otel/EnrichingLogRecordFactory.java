package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.*;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 *
 * @author Stefano Reksten
 */
public class EnrichingLogRecordFactory implements LogRecordFactory {
    private final LogRecordFactory delegate;
    private final Resource resource;
    private final InstrumentationScope scope;
    private final List<KeyValue> commonAttributes;
    private final SpanContext explicitSpanContext;
    private final Supplier<SpanContext> activeSpanContextResolver;
    private final Supplier<InstrumentationScope> activeInstrumentationScopeResolver;

    public EnrichingLogRecordFactory(
            LogRecordFactory delegate,
            Resource resource,
            InstrumentationScope scope,
            List<KeyValue> commonAttributes) {
        this(delegate, resource, scope, commonAttributes, null, null, null);
    }

    public EnrichingLogRecordFactory(
            LogRecordFactory delegate,
            Resource resource,
            InstrumentationScope scope,
            List<KeyValue> commonAttributes,
            SpanContext explicitSpanContext) {
        this(delegate, resource, scope, commonAttributes, explicitSpanContext, null, null);
    }

    public EnrichingLogRecordFactory(
            LogRecordFactory delegate,
            Resource resource,
            InstrumentationScope scope,
            List<KeyValue> commonAttributes,
            @Nullable SpanContext explicitSpanContext,
            @Nullable Supplier<SpanContext> activeSpanContextResolver,
            @Nullable Supplier<InstrumentationScope> activeInstrumentationScopeResolver) {
        this.delegate = delegate;
        this.resource = resource;
        this.scope = scope;
        this.commonAttributes = commonAttributes;
        this.explicitSpanContext = explicitSpanContext;
        this.activeSpanContextResolver = activeSpanContextResolver;
        this.activeInstrumentationScopeResolver = activeInstrumentationScopeResolver;
    }

    @Override
    public LogRecord create() {
        return enrich(delegate.create());
    }

    @Override
    public LogRecord create(SeverityNumber severityNumber, String message) {
        return enrich(delegate.create(severityNumber, message));
    }

    @Override
    public LogRecord create(String message, Throwable throwable) {
        return enrich(delegate.create(message, throwable));
    }

    @Override
    public LogRecord create(Throwable throwable) {
        return enrich(delegate.create(throwable));
    }

    private LogRecord enrich(LogRecord record) {
        if (!(record instanceof LogRecordImpl)) {
            return record;
        }
        LogRecordImpl mutable = (LogRecordImpl) record;
        if (resource != null && mutable.getResource() == null) {
            mutable.setResource(resource);
        }

        if (mutable.getInstrumentationScope() == null) {
            InstrumentationScope resolvedScope = resolveInstrumentationScope();
            if (resolvedScope != null) {
                mutable.setInstrumentationScope(resolvedScope);
            }
        }

        if (commonAttributes != null && !commonAttributes.isEmpty()) {
            mergeMissingAttributes(mutable);
        }

        if (isBlank(mutable.getTraceId()) || isBlank(mutable.getSpanId())) {
            SpanContext resolvedSpanContext = resolveSpanContext();
            if (resolvedSpanContext != null) {
                enrichTraceCorrelation(mutable, resolvedSpanContext);
            }
        }
        return mutable;
    }

    private InstrumentationScope resolveInstrumentationScope() {
        if (scope != null) {
            return scope;
        }
        if (activeInstrumentationScopeResolver == null) {
            return null;
        }
        try {
            return activeInstrumentationScopeResolver.get();
        } catch (RuntimeException ex) {
            OpenTelemetryAttributeValidator.report(ex.getMessage(), ex);
            return null;
        }
    }

    private SpanContext resolveSpanContext() {
        SpanContext fromExplicit = validateSpanContext(explicitSpanContext);
        if (fromExplicit != null) {
            return fromExplicit;
        }
        if (activeSpanContextResolver == null) {
            return null;
        }
        try {
            return validateSpanContext(activeSpanContextResolver.get());
        } catch (RuntimeException ex) {
            OpenTelemetryAttributeValidator.report(ex.getMessage(), ex);
            return null;
        }
    }

    private static SpanContext validateSpanContext(final SpanContext candidate) {
        if (candidate == null) {
            return null;
        }
        try {
            return candidate.isValid() ? candidate : null;
        } catch (RuntimeException ex) {
            OpenTelemetryAttributeValidator.report(ex.getMessage(), ex);
            return null;
        }
    }

    private static void enrichTraceCorrelation(final LogRecordImpl record,
                                               final SpanContext spanContext) {
        boolean missingTraceId = isBlank(record.getTraceId());
        boolean missingSpanId = isBlank(record.getSpanId());

        if (missingTraceId) {
            record.setTraceId(spanContext.getTraceId());
        }
        if (missingSpanId) {
            record.setSpanId(spanContext.getSpanId());
        }
        if (record.getTraceFlags() == 0 && (missingTraceId || missingSpanId)) {
            record.setTraceFlags(Byte.toUnsignedInt(spanContext.getTraceFlags()));
        }
    }

    private void mergeMissingAttributes(final LogRecordImpl record) {
        List<KeyValue> currentAttributes = record.getAttributes();
        List<KeyValue> merged = new ArrayList<>(currentAttributes);
        Set<String> keys = new HashSet<>(currentAttributes.size() + commonAttributes.size());
        for (KeyValue attribute : currentAttributes) {
            String key = attribute == null ? null : attribute.getKey();
            if (!isBlank(key)) {
                keys.add(key);
            }
        }
        for (KeyValue commonAttribute : commonAttributes) {
            if (commonAttribute == null || isBlank(commonAttribute.getKey())) {
                continue;
            }
            if (keys.add(commonAttribute.getKey())) {
                merged.add(commonAttribute);
            }
        }
        if (merged.size() != currentAttributes.size()) {
            record.setAttributes(merged);
        }
    }

    private static boolean isBlank(final String value) {
        return value == null || value.trim().isEmpty();
    }
}
