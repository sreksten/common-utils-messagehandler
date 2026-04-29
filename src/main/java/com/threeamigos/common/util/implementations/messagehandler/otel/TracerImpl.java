package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;
import com.threeamigos.common.util.implementations.messagehandler.tracecontext.TraceContextGenerator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * An immutable implementation of a {@link Tracer}.
 * <p>
 * Package-private on purpose: callers should obtain tracers only via {@link TracerProvider},
 * e.g. {@link TracerProvider#getTracer(String, String)} and related provider factory methods.
 *
 * @author Stefano Reksten
 */
class TracerImpl implements Tracer {

    private final InstrumentationScope instrumentationScope;
    private final boolean enabled;

    TracerImpl(final String instrumentationName,
               final String version,
               final String schemaUrl,
               final Collection<KeyValue> attributes) {
        this(instrumentationName, version, schemaUrl, attributes, true);
    }

    TracerImpl(final String instrumentationName,
               final String version,
               final String schemaUrl,
               final Collection<KeyValue> attributes,
               final boolean enabled) {
        String resolvedInstrumentationName = instrumentationName;
        if (resolvedInstrumentationName == null || resolvedInstrumentationName.trim().isEmpty()) {
            OpenTelemetryAttributeValidator.reportBundled("nullInstrumentationNameProvided");
            resolvedInstrumentationName = "";
        }
        String instrumentationName1 = resolvedInstrumentationName;
        Collection<KeyValue> attributes1 = attributes == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(attributes));
        this.instrumentationScope = InstrumentationScopeFactory.create(
                instrumentationName1,
                version,
                schemaUrl,
                new ArrayList<>(attributes1));
        this.enabled = enabled;
    }

    @Override
    public Span createSpan(final String name) {
        return createSpan(name, null);
    }

    @Override
    public Span createSpan(final String name, final SpanContext parentSpanContext) {
        String normalizedName = OpenTelemetryAttributeValidator.requireNonBlank(name, "spanName");
        if (!enabled) {
            return Span.wrap(null);
        }
        SpanContext spanContext;
        if (parentSpanContext == null || !parentSpanContext.isValid()) {
            spanContext = new SpanContextImpl(
                    TraceContextGenerator.generateTraceId(),
                    TraceContextGenerator.generateParentId(),
                    (byte) 0x00,
                    false,
                    new TraceStateImpl());
        } else {
            spanContext = new SpanContextImpl(
                    parentSpanContext.getTraceId(),
                    TraceContextGenerator.generateParentId(),
                    parentSpanContext.getTraceFlags(),
                    false,
                    parentSpanContext.getTraceState());
        }
        return new SpanImpl(
                normalizedName,
                spanContext,
                instrumentationScope,
                java.time.Instant.now(),
                true);
    }

    @Override
    public InstrumentationScope getInstrumentationScope() {
        return instrumentationScope;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
