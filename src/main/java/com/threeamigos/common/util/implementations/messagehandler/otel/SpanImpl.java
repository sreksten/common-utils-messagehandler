package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Event;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Link;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import com.threeamigos.common.util.interfaces.messagehandler.otel.StatusCode;

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
 *
 * @author Stefano Reksten
 */
public final class SpanImpl implements Span {

    private static final String EXCEPTION_EVENT_NAME = "exception";
    private static final String EVENT_ATTRIBUTES_FIELD_NAME = MessageHandlerResourceBundle.get("eventAttributesFieldName");
    private static final String LINK_ATTRIBUTES_FIELD_NAME = MessageHandlerResourceBundle.get("linkAttributesFieldName");
    private static final String EXCEPTION_ATTRIBUTES_FIELD_NAME = MessageHandlerResourceBundle.get("exceptionAttributesFieldName");

    private final Object lock = new Object();
    private final SpanContext spanContext;
    private final Instant startTimestamp;
    private final boolean recordingEnabled;
    private final Map<String, AnyValue> attributes = new LinkedHashMap<>();
    private final List<Event> events = new ArrayList<>();
    private final List<Link> links = new ArrayList<>();

    private String name;
    private StatusCode statusCode = StatusCode.UNSET;
    private String statusDescription = "";
    private Instant endTimestamp;
    private volatile boolean ended;

    public SpanImpl(final String name, final SpanContext spanContext) {
        this(name, spanContext, Instant.now(), true);
    }

    SpanImpl(final String name,
             final SpanContext spanContext,
             final Instant startTimestamp,
             final boolean recordingEnabled) {
        this.name = OpenTelemetryAttributeValidator.requireNonBlank(name, "spanName");
        if (spanContext == null) {
            OpenTelemetryAttributeValidator.handleBundled("spanContextMustNotBeNull");
            this.spanContext = new SpanContextImpl();
        } else {
            this.spanContext = spanContext;
        }
        if (startTimestamp == null) {
            OpenTelemetryAttributeValidator.handleBundled("startTimestampMustNotBeNull");
            this.startTimestamp = Instant.now();
        } else {
            this.startTimestamp = startTimestamp;
        }
        this.recordingEnabled = recordingEnabled;
        this.ended = !recordingEnabled;
    }

    @Override
    public SpanContext getSpanContext() {
        return spanContext;
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
        synchronized (lock) {
            if (ended) {
                return;
            }
            this.endTimestamp = effectiveEndTimestamp;
            this.ended = true;
        }
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
}
