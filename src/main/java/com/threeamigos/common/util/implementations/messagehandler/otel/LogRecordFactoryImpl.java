package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.*;
import jakarta.annotation.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 *
 * @author Stefano Reksten
 */
public class LogRecordFactoryImpl implements LogRecordFactory {
    private final LogRecordFactory delegate;
    private final Resource resource;
    private final InstrumentationScope scope;
    private final List<KeyValue> commonAttributes;
    private final SpanContext explicitSpanContext;
    private final Supplier<SpanContext> activeSpanContextResolver;
    private final Supplier<InstrumentationScope> activeInstrumentationScopeResolver;
    private final Supplier<Span> activeSpanResolver;

    public LogRecordFactoryImpl() {
        this(null, null, null, null, null, null, null, null);
    }

    public LogRecordFactoryImpl(
            Resource resource,
            InstrumentationScope scope,
            List<KeyValue> commonAttributes) {
        this(null, resource, scope, commonAttributes, null, null, null, null);
    }

    public LogRecordFactoryImpl(
            Resource resource,
            InstrumentationScope scope,
            List<KeyValue> commonAttributes,
            SpanContext explicitSpanContext) {
        this(null, resource, scope, commonAttributes, explicitSpanContext, null, null, null);
    }

    public LogRecordFactoryImpl(
            Resource resource,
            InstrumentationScope scope,
            List<KeyValue> commonAttributes,
            @Nullable SpanContext explicitSpanContext,
            @Nullable Supplier<SpanContext> activeSpanContextResolver,
            @Nullable Supplier<InstrumentationScope> activeInstrumentationScopeResolver,
            @Nullable Supplier<Span> activeSpanResolver) {
        this(null, resource, scope, commonAttributes, explicitSpanContext,
                activeSpanContextResolver, activeInstrumentationScopeResolver, activeSpanResolver);
    }

    public LogRecordFactoryImpl(
            LogRecordFactory delegate,
            Resource resource,
            InstrumentationScope scope,
            List<KeyValue> commonAttributes) {
        this(delegate, resource, scope, commonAttributes, null, null, null);
    }

    public LogRecordFactoryImpl(
            LogRecordFactory delegate,
            Resource resource,
            InstrumentationScope scope,
            List<KeyValue> commonAttributes,
            SpanContext explicitSpanContext) {
        this(delegate, resource, scope, commonAttributes, explicitSpanContext, null, null);
    }

    public LogRecordFactoryImpl(
            LogRecordFactory delegate,
            Resource resource,
            InstrumentationScope scope,
            List<KeyValue> commonAttributes,
            @Nullable SpanContext explicitSpanContext,
            @Nullable Supplier<SpanContext> activeSpanContextResolver,
            @Nullable Supplier<InstrumentationScope> activeInstrumentationScopeResolver) {
        this(delegate, resource, scope, commonAttributes, explicitSpanContext,
                activeSpanContextResolver, activeInstrumentationScopeResolver, null);
    }

    public LogRecordFactoryImpl(
            LogRecordFactory delegate,
            Resource resource,
            InstrumentationScope scope,
            List<KeyValue> commonAttributes,
            @Nullable SpanContext explicitSpanContext,
            @Nullable Supplier<SpanContext> activeSpanContextResolver,
            @Nullable Supplier<InstrumentationScope> activeInstrumentationScopeResolver,
            @Nullable Supplier<Span> activeSpanResolver) {
        this.delegate = delegate;
        this.resource = resource;
        this.scope = scope;
        this.commonAttributes = commonAttributes;
        this.explicitSpanContext = explicitSpanContext;
        this.activeSpanContextResolver = activeSpanContextResolver;
        this.activeInstrumentationScopeResolver = activeInstrumentationScopeResolver;
        this.activeSpanResolver = activeSpanResolver;
    }

    @Override
    public LogRecord create() {
        LogRecord record = delegate == null ? new LogRecordImpl() : delegate.create();
        return enrich(record);
    }

    @Override
    public LogRecord create(SeverityNumber severityNumber, String message) {
        if (delegate != null) {
            return enrich(delegate.create(severityNumber, message));
        }
        SeverityNumber resolvedSeverity = severityNumber;
        if (resolvedSeverity == null) {
            OpenTelemetryAttributeValidator.handleBundled("valueMustNotBeNull");
            resolvedSeverity = SeverityNumber.UNSPECIFIED;
        }
        String resolvedMessage = message;
        if (resolvedMessage == null) {
            OpenTelemetryAttributeValidator.handleBundled("nullMessageProvided");
            resolvedMessage = "";
        }
        LogRecordImpl logRecord = new LogRecordImpl();
        logRecord.setSeverityNumber(resolvedSeverity);
        logRecord.setSeverityText(resolvedSeverity.name());
        logRecord.setBody(AnyValueFactory.ofString(resolvedMessage));
        return enrich(logRecord);
    }

    @Override
    public LogRecord create(String message, Throwable throwable) {
        if (delegate != null) {
            return enrich(delegate.create(message, throwable));
        }
        String resolvedMessage = message;
        if (resolvedMessage == null) {
            OpenTelemetryAttributeValidator.handleBundled("nullMessageProvided");
            resolvedMessage = "";
        }
        if (throwable == null) {
            OpenTelemetryAttributeValidator.handleBundled("nullThrowableProvided");
        }
        LogRecordImpl logRecord = new LogRecordImpl();
        setErrorSeverity(logRecord);
        logRecord.setBody(AnyValueFactory.ofString(resolvedMessage));
        if (throwable != null) {
            addThrowableDetails(logRecord, throwable);
        }
        return enrich(logRecord);
    }

    @Override
    public LogRecord create(Throwable throwable) {
        if (delegate != null) {
            return enrich(delegate.create(throwable));
        }
        if (throwable == null) {
            OpenTelemetryAttributeValidator.handleBundled("nullThrowableProvided");
            LogRecordImpl fallback = new LogRecordImpl();
            setErrorSeverity(fallback);
            fallback.setBody(AnyValueFactory.ofString(""));
            return enrich(fallback);
        }
        LogRecordImpl logRecord = new LogRecordImpl();
        setErrorSeverity(logRecord);
        String throwableMessage = throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
        logRecord.setBody(AnyValueFactory.ofString(throwableMessage));
        addThrowableDetails(logRecord, throwable);
        return enrich(logRecord);
    }

    /**
     * Enriches an existing record instance with configured resource/scope/attributes/correlation.
     *
     * @param record record to enrich
     * @return enriched record; never {@code null}
     */
    public LogRecord enrichRecord(final LogRecord record) {
        if (record == null) {
            OpenTelemetryAttributeValidator.handleBundled("logRecordMustNotBeNull");
            return new LogRecordImpl();
        }
        return enrich(record);
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
        appendAsActiveSpanEvent(mutable);
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

    private void appendAsActiveSpanEvent(final LogRecordImpl record) {
        Span activeSpan = resolveActiveSpan();
        if (activeSpan == null || !activeSpan.isRecording()) {
            return;
        }
        SpanContext activeSpanContext = activeSpan.getSpanContext();
        if (activeSpanContext == null || !activeSpanContext.isValid()) {
            return;
        }
        if (!matchesActiveSpan(record, activeSpanContext)) {
            return;
        }
        String message = record.getBody() == null ? null : record.getBody().asString();
        if (isBlank(message)) {
            return;
        }
        List<KeyValue> eventAttributes = new ArrayList<KeyValue>(3);
        eventAttributes.add(KeyValueFactory.of("log.message", AnyValueFactory.ofString(message)));
        String severity = record.getSeverityText();
        if (!isBlank(severity)) {
            eventAttributes.add(KeyValueFactory.of("log.severity", AnyValueFactory.ofString(severity)));
        }
        activeSpan.addEvent("log", eventAttributes, record.getTimestamp());
    }

    private Span resolveActiveSpan() {
        if (activeSpanResolver == null) {
            return null;
        }
        try {
            return activeSpanResolver.get();
        } catch (RuntimeException ex) {
            OpenTelemetryAttributeValidator.report(ex.getMessage(), ex);
            return null;
        }
    }

    private static boolean matchesActiveSpan(final LogRecordImpl record, final SpanContext activeSpanContext) {
        if (!isBlank(record.getTraceId()) && !record.getTraceId().equalsIgnoreCase(activeSpanContext.getTraceId())) {
            return false;
        }
        if (!isBlank(record.getSpanId()) && !record.getSpanId().equalsIgnoreCase(activeSpanContext.getSpanId())) {
            return false;
        }
        return true;
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

    private static void setErrorSeverity(final LogRecordImpl logRecord) {
        logRecord.setSeverityNumber(SeverityNumber.ERROR);
        logRecord.setSeverityText(SeverityNumber.ERROR.name());
    }

    private static void addThrowableDetails(final LogRecordImpl logRecord, final Throwable throwable) {
        logRecord.setEventName(OTelTags.EVENT_EXCEPTION.getValue());
        List<KeyValue> attributes = new ArrayList<KeyValue>(3);
        attributes.add(KeyValueFactory.of(OTelTags.EXCEPTION_TYPE, AnyValueFactory.ofString(throwable.getClass().getName())));
        if (throwable.getMessage() != null) {
            attributes.add(KeyValueFactory.of(OTelTags.EXCEPTION_MESSAGE, AnyValueFactory.ofString(throwable.getMessage())));
        }
        attributes.add(KeyValueFactory.of(OTelTags.EXCEPTION_STACKTRACE, AnyValueFactory.ofString(stackTraceAsString(throwable))));
        logRecord.setAttributes(attributes);
    }

    private static String stackTraceAsString(final Throwable throwable) {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            throwable.printStackTrace(pw);
            return sw.toString();
        }
    }

    private static boolean isBlank(final String value) {
        return value == null || value.trim().isEmpty();
    }
}
