package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Event;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Link;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanData;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanDispatcher;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import com.threeamigos.common.util.interfaces.messagehandler.otel.StatusCode;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;
import com.threeamigos.common.util.implementations.messagehandler.tracecontext.TraceContextGenerator;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A mutable implementation of a {@link Span}.
 * <p>
 * Package-private on purpose: spans should be created through
 * {@link com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer#createSpan(String)}
 * and
 * {@link com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer#createSpan(String, com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext)}.
 *
 * @author Stefano Reksten
 */
final class SpanImpl implements Span {

    private static final String EXCEPTION_EVENT_NAME = "exception";
    private static final String EVENT_ATTRIBUTES_FIELD_NAME = MessageHandlerResourceBundle.get("eventAttributesFieldName");
    private static final String LINK_ATTRIBUTES_FIELD_NAME = MessageHandlerResourceBundle.get("linkAttributesFieldName");
    private static final String EXCEPTION_ATTRIBUTES_FIELD_NAME = MessageHandlerResourceBundle.get("exceptionAttributesFieldName");

    private final Object lock = new Object();
    private final SpanContext spanContext;
    private final InstrumentationScope instrumentationScope;
    private final Instant startTimestamp;
    private final boolean recordingEnabled;
    private final AutoCloseable scopeToken;
    private final String parentSpanId;
    private final SpanDispatcher spanDispatcher;
    private final Tracer spanCreator;
    private final Map<String, AnyValue> attributes = new LinkedHashMap<>();
    private final List<Event> events = new ArrayList<>();
    private final List<Link> links = new ArrayList<>();

    private String name;
    private StatusCode statusCode = StatusCode.UNSET;
    private String statusDescription = "";
    private Instant endTimestamp;
    private volatile boolean ended;

    SpanImpl(final String name, final SpanContext spanContext) {
        this(name, spanContext, null, Instant.now(), true, null);
    }

    SpanImpl(final String name,
             final SpanContext spanContext,
             final InstrumentationScope instrumentationScope,
             final Instant startTimestamp,
             final boolean recordingEnabled) {
        this(name, spanContext, instrumentationScope, startTimestamp, recordingEnabled, null);
    }

    SpanImpl(final String name,
             final SpanContext spanContext,
             final InstrumentationScope instrumentationScope,
             final Instant startTimestamp,
             final boolean recordingEnabled,
             final AutoCloseable scopeToken) {
        this(name, spanContext, instrumentationScope, startTimestamp, recordingEnabled, scopeToken, null, null);
    }

    SpanImpl(final String name,
             final SpanContext spanContext,
             final InstrumentationScope instrumentationScope,
             final Instant startTimestamp,
             final boolean recordingEnabled,
             final AutoCloseable scopeToken,
             final String parentSpanId,
             final SpanDispatcher spanDispatcher) {
        this(name, spanContext, instrumentationScope, startTimestamp, recordingEnabled,
                scopeToken, parentSpanId, spanDispatcher, null);
    }

    SpanImpl(final String name,
             final SpanContext spanContext,
             final InstrumentationScope instrumentationScope,
             final Instant startTimestamp,
             final boolean recordingEnabled,
             final AutoCloseable scopeToken,
             final String parentSpanId,
             final SpanDispatcher spanDispatcher,
             final Tracer spanCreator) {
        this.name = OpenTelemetryAttributeValidator.requireNonBlank(name, "spanName");
        if (spanContext == null) {
            OpenTelemetryAttributeValidator.handleBundled("spanContextMustNotBeNull");
            this.spanContext = new SpanContextImpl();
        } else {
            this.spanContext = spanContext;
        }
        this.instrumentationScope = instrumentationScope;
        if (startTimestamp == null) {
            OpenTelemetryAttributeValidator.handleBundled("startTimestampMustNotBeNull");
            this.startTimestamp = Instant.now();
        } else {
            this.startTimestamp = startTimestamp;
        }
        this.recordingEnabled = recordingEnabled;
        this.scopeToken = scopeToken;
        this.parentSpanId = parentSpanId;
        this.spanDispatcher = spanDispatcher;
        this.spanCreator = spanCreator;
        this.ended = !recordingEnabled;
    }

    @Override
    public SpanContext getSpanContext() {
        return spanContext;
    }

    @Override
    public InstrumentationScope getInstrumentationScope() {
        return instrumentationScope;
    }

    @Override
    public Span create(final String spanName) {
        if (spanCreator != null) {
            return spanCreator.createSpan(spanName, spanContext);
        }
        String normalizedName = OpenTelemetryAttributeValidator.requireNonBlank(spanName, "spanName");
        String parentId = null;
        SpanContext childContext;
        if (spanContext == null || !spanContext.isValid()) {
            childContext = new SpanContextImpl(
                    TraceContextGenerator.generateTraceId(),
                    TraceContextGenerator.generateParentId(),
                    (byte) 0x00,
                    false,
                    new TraceStateImpl());
        } else {
            parentId = spanContext.getSpanId();
            childContext = new SpanContextImpl(
                    spanContext.getTraceId(),
                    TraceContextGenerator.generateParentId(),
                    spanContext.getTraceFlags(),
                    false,
                    spanContext.getTraceState());
        }
        return new SpanImpl(
                normalizedName,
                childContext,
                instrumentationScope,
                Instant.now(),
                true,
                null,
                parentId,
                spanDispatcher,
                null);
    }

    @Override
    public boolean isRecording() {
        return recordingEnabled && !ended;
    }

    @Override
    public void setAttribute(final String key, final AnyValue value) {
        String normalizedKey = OpenTelemetryAttributeValidator.requireNonBlank(key, "attributeKey");
        AnyValue normalizedValue = value;
        if (normalizedValue == null) {
            OpenTelemetryAttributeValidator.handleBundled("attributeValueMustNotBeNull");
            normalizedValue = AnyValueFactory.empty();
        }
        if (!isRecording()) {
            return;
        }
        synchronized (lock) {
            if (!isRecording()) {
                return;
            }
            attributes.put(normalizedKey, normalizedValue);
        }
    }

    @Override
    public void addEvent(final String name) {
        addEvent(name, Collections.<KeyValue>emptyList(), Instant.now());
    }

    @Override
    public void addEvent(final String name, final List<KeyValue> attributes) {
        addEvent(name, attributes, Instant.now());
    }

    @Override
    public void addEvent(final String name, final List<KeyValue> attributes, final Instant timestamp) {
        if (!isRecording()) {
            return;
        }
        String normalizedName = OpenTelemetryAttributeValidator.requireNonBlank(name, "eventName");
        Instant effectiveTimestamp = timestamp;
        if (effectiveTimestamp == null) {
            OpenTelemetryAttributeValidator.handleBundled("eventTimestampMustNotBeNull");
            effectiveTimestamp = Instant.now();
        }
        List<KeyValue> sanitizedAttributes = OpenTelemetryAttributeValidator.copyAndValidateKeyValues(
                attributes, EVENT_ATTRIBUTES_FIELD_NAME);
        synchronized (lock) {
            if (!isRecording()) {
                return;
            }
            events.add(new EventImpl(normalizedName, effectiveTimestamp, sanitizedAttributes));
        }
    }

    @Override
    public void addEvent(final Event event) {
        if (event == null) {
            OpenTelemetryAttributeValidator.handleBundled("eventMustNotBeNull");
            return;
        }
        addEvent(event.getName(), event.getAttributes(), event.getTimestamp());
    }

    @Override
    public void addLink(final SpanContext spanContext) {
        addLink(spanContext, Collections.<KeyValue>emptyList());
    }

    @Override
    public void addLink(final SpanContext spanContext, final List<KeyValue> attributes) {
        if (!isRecording()) {
            return;
        }
        if (spanContext == null) {
            OpenTelemetryAttributeValidator.handleBundled("linkSpanContextMustNotBeNull");
            return;
        }
        List<KeyValue> sanitizedAttributes = OpenTelemetryAttributeValidator.copyAndValidateKeyValues(
                attributes, LINK_ATTRIBUTES_FIELD_NAME);
        synchronized (lock) {
            if (!isRecording()) {
                return;
            }
            links.add(new LinkImpl(spanContext, sanitizedAttributes));
        }
    }

    @Override
    public void addLink(final Link link) {
        if (link == null) {
            OpenTelemetryAttributeValidator.handleBundled("linkMustNotBeNull");
            return;
        }
        addLink(link.getSpanContext(), link.getAttributes());
    }

    @Override
    public void setStatus(final StatusCode statusCode) {
        setStatus(statusCode, "");
    }

    @Override
    public void setStatus(final StatusCode statusCode, final String description) {
        if (statusCode == null) {
            OpenTelemetryAttributeValidator.handleBundled("statusCodeMustNotBeNull");
            return;
        }
        if (!isRecording()) {
            return;
        }
        if (statusCode == StatusCode.UNSET) {
            return;
        }
        synchronized (lock) {
            if (!isRecording()) {
                return;
            }
            if (this.statusCode == StatusCode.OK && statusCode != StatusCode.OK) {
                return;
            }
            if (statusCode == StatusCode.OK) {
                this.statusCode = StatusCode.OK;
                this.statusDescription = "";
                return;
            }
            this.statusCode = StatusCode.ERROR;
            this.statusDescription = description == null ? "" : description;
        }
    }

    @Override
    public void updateName(final String name) {
        String normalizedName = OpenTelemetryAttributeValidator.requireNonBlank(name, "spanName");
        if (!isRecording()) {
            return;
        }
        synchronized (lock) {
            if (!isRecording()) {
                return;
            }
            this.name = normalizedName;
        }
    }

    @Override
    public void end() {
        end(Instant.now());
    }

    @Override
    public void end(final Instant endTimestamp) {
        Instant effectiveEndTimestamp = endTimestamp;
        if (effectiveEndTimestamp == null) {
            OpenTelemetryAttributeValidator.handleBundled("endTimestampMustNotBeNull");
            effectiveEndTimestamp = Instant.now();
        }
        SpanData spanDataToDispatch = null;
        synchronized (lock) {
            if (ended) {
                return;
            }
            this.endTimestamp = effectiveEndTimestamp;
            this.ended = true;
            spanDataToDispatch = snapshotSpanData();
        }
        closeScopeTokenQuietly();
        dispatchSpanDataQuietly(spanDataToDispatch);
    }

    @Override
    public void recordException(final Throwable exception) {
        recordException(exception, Collections.<KeyValue>emptyList());
    }

    @Override
    public void recordException(final Throwable exception, final List<KeyValue> additionalAttributes) {
        if (exception == null) {
            OpenTelemetryAttributeValidator.handleBundled("exceptionMustNotBeNull");
            return;
        }
        if (!isRecording()) {
            return;
        }
        Map<String, AnyValue> mergedEventAttributes = new LinkedHashMap<>();
        mergedEventAttributes.put(OTelTags.EXCEPTION_TYPE.getValue(),
                AnyValueFactory.ofString(exception.getClass().getName()));
        if (exception.getMessage() != null) {
            mergedEventAttributes.put(OTelTags.EXCEPTION_MESSAGE.getValue(),
                    AnyValueFactory.ofString(exception.getMessage()));
        }
        String stackTrace = stackTraceAsString(exception);
        if (!stackTrace.isEmpty()) {
            mergedEventAttributes.put(OTelTags.EXCEPTION_STACKTRACE.getValue(),
                    AnyValueFactory.ofString(stackTrace));
        }
        List<KeyValue> sanitizedAdditional = OpenTelemetryAttributeValidator.copyAndValidateKeyValues(
                additionalAttributes, EXCEPTION_ATTRIBUTES_FIELD_NAME);
        for (KeyValue additional : sanitizedAdditional) {
            mergedEventAttributes.put(additional.getKey(), additional.getValue());
        }

        List<KeyValue> eventAttributes = new ArrayList<>(mergedEventAttributes.size());
        for (Map.Entry<String, AnyValue> entry : mergedEventAttributes.entrySet()) {
            eventAttributes.add(KeyValueFactory.of(entry.getKey(), entry.getValue()));
        }
        addEvent(EXCEPTION_EVENT_NAME, eventAttributes, Instant.now());
    }

    String getName() {
        synchronized (lock) {
            return name;
        }
    }

    Instant getStartTimestamp() {
        return startTimestamp;
    }

    Instant getEndTimestamp() {
        synchronized (lock) {
            return endTimestamp;
        }
    }

    StatusCode getStatusCode() {
        synchronized (lock) {
            return statusCode;
        }
    }

    String getStatusDescription() {
        synchronized (lock) {
            return statusDescription;
        }
    }

    List<KeyValue> getAttributesSnapshot() {
        synchronized (lock) {
            List<KeyValue> snapshot = new ArrayList<>(attributes.size());
            for (Map.Entry<String, AnyValue> entry : attributes.entrySet()) {
                snapshot.add(KeyValueFactory.of(entry.getKey(), entry.getValue()));
            }
            return snapshot;
        }
    }

    List<Event> getEventsSnapshot() {
        synchronized (lock) {
            return new ArrayList<>(events);
        }
    }

    List<Link> getLinksSnapshot() {
        synchronized (lock) {
            return new ArrayList<>(links);
        }
    }

    private static String stackTraceAsString(final Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        throwable.printStackTrace(printWriter);
        printWriter.flush();
        return stringWriter.toString();
    }

    private void closeScopeTokenQuietly() {
        if (scopeToken == null) {
            return;
        }
        try {
            scopeToken.close();
        } catch (Exception ex) {
            OpenTelemetryAttributeValidator.report(ex.getMessage(), ex);
        }
    }

    private void dispatchSpanDataQuietly(final SpanData spanData) {
        if (spanDispatcher == null || spanData == null) {
            return;
        }
        try {
            spanDispatcher.dispatchSpan(spanData);
        } catch (IOException ex) {
            OpenTelemetryAttributeValidator.report(ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            OpenTelemetryAttributeValidator.report(ex.getMessage(), ex);
        }
    }

    private SpanData snapshotSpanData() {
        final String snapshotName = this.name;
        final SpanContext snapshotSpanContext = this.spanContext;
        final String snapshotParentSpanId = this.parentSpanId;
        final InstrumentationScope snapshotScope = this.instrumentationScope;
        final Instant snapshotStart = this.startTimestamp;
        final Instant snapshotEnd = this.endTimestamp;
        final StatusCode snapshotStatusCode = this.statusCode;
        final String snapshotStatusDescription = this.statusDescription;
        final List<KeyValue> snapshotAttributes = getAttributesSnapshot();
        final List<Event> snapshotEvents = new ArrayList<Event>(this.events);
        final List<Link> snapshotLinks = new ArrayList<Link>(this.links);

        return new SpanData() {
            @Override
            public String getName() {
                return snapshotName;
            }

            @Override
            public SpanContext getSpanContext() {
                return snapshotSpanContext;
            }

            @Override
            public String getParentSpanId() {
                return snapshotParentSpanId;
            }

            @Override
            public InstrumentationScope getInstrumentationScope() {
                return snapshotScope;
            }

            @Override
            public Instant getStartTimestamp() {
                return snapshotStart;
            }

            @Override
            public Instant getEndTimestamp() {
                return snapshotEnd;
            }

            @Override
            public StatusCode getStatusCode() {
                return snapshotStatusCode;
            }

            @Override
            public String getStatusDescription() {
                return snapshotStatusDescription;
            }

            @Override
            public List<KeyValue> getAttributes() {
                return Collections.unmodifiableList(snapshotAttributes);
            }

            @Override
            public List<Event> getEvents() {
                return Collections.unmodifiableList(snapshotEvents);
            }

            @Override
            public List<Link> getLinks() {
                return Collections.unmodifiableList(snapshotLinks);
            }
        };
    }
}
