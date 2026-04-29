package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;
import com.threeamigos.common.util.implementations.messagehandler.tracecontext.TraceContextGenerator;

import java.util.Collection;
import java.util.Collections;

/**
 *
 * @author Stefano Reksten
 */
public class TracerImpl implements Tracer {

    private final String instrumentationName;
    private final String version;
    private final String schemaUrl;
    private final Collection<KeyValue> attributes;

    public TracerImpl(String instrumentationName, String version, String schemaUrl, Collection<KeyValue> attributes) {
        if (instrumentationName == null) {
            java.util.logging.Logger.getLogger(TracerImpl.class.getName()).severe(
                    MessageHandlerResourceBundle.get("nullInstrumentationNameProvided"));
            instrumentationName = "";
        }
        this.instrumentationName = instrumentationName;
        this.version = version;
        this.schemaUrl = schemaUrl;
        this.attributes = attributes;
    }

    @Override
    public Span createSpan(final String name) {
        String normalizedName = OpenTelemetryAttributeValidator.requireNonBlank(name, "spanName");
        return new SpanImpl(
                normalizedName,
                new SpanContextImpl(
                        TraceContextGenerator.generateTraceId(),
                        TraceContextGenerator.generateParentId(),
                        (byte) 0x00,
                        false,
                        Collections.<KeyValue>emptyList()));
    }
}
