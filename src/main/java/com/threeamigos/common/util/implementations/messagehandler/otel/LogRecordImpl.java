package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A mutable implementation of the {@link LogRecord} interface.
 * All fields default to {@code null} / {@code 0} / empty list and may be set via the corresponding setters.
 *
 * @author Stefano Reksten
 */
public class LogRecordImpl implements LogRecord {

    private Instant timestamp;
    private Instant observedTimestamp;
    private String traceId;
    private String spanId;
    private int traceFlags;
    private String severityText;
    private SeverityNumber severityNumber = SeverityNumber.UNSPECIFIED;
    private AnyValue body;
    private Resource resource;
    private InstrumentationScope instrumentationScope;
    private List<KeyValue> attributes = new ArrayList<>();
    private int droppedAttributesCount;
    private String eventName;

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public Instant getObservedTimestamp() {
        return observedTimestamp;
    }

    public void setObservedTimestamp(final Instant observedTimestamp) {
        this.observedTimestamp = observedTimestamp;
    }

    @Override
    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(final String traceId) {
        this.traceId = traceId;
    }

    @Override
    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(final String spanId) {
        this.spanId = spanId;
    }

    @Override
    public int getTraceFlags() {
        return traceFlags;
    }

    public void setTraceFlags(final int traceFlags) {
        this.traceFlags = traceFlags;
    }

    @Override
    public String getSeverityText() {
        return severityText;
    }

    public void setSeverityText(final String severityText) {
        this.severityText = severityText;
    }

    @Override
    public SeverityNumber getSeverityNumber() {
        return severityNumber;
    }

    public void setSeverityNumber(final SeverityNumber severityNumber) {
        this.severityNumber = severityNumber != null ? severityNumber : SeverityNumber.UNSPECIFIED;
    }

    @Override
    public AnyValue getBody() {
        return body;
    }

    public void setBody(final AnyValue body) {
        this.body = body;
    }

    @Override
    public Resource getResource() {
        return resource;
    }

    public void setResource(final Resource resource) {
        this.resource = resource;
    }

    @Override
    public InstrumentationScope getInstrumentationScope() {
        return instrumentationScope;
    }

    public void setInstrumentationScope(final InstrumentationScope instrumentationScope) {
        this.instrumentationScope = instrumentationScope;
    }

    @Override
    public List<KeyValue> getAttributes() {
        return Collections.unmodifiableList(attributes);
    }

    public void setAttributes(final List<KeyValue> attributes) {
        this.attributes = attributes != null ? attributes : new ArrayList<>();
    }

    @Override
    public int getDroppedAttributesCount() {
        return droppedAttributesCount;
    }

    public void setDroppedAttributesCount(final int droppedAttributesCount) {
        this.droppedAttributesCount = droppedAttributesCount;
    }

    @Override
    public String getEventName() {
        return eventName;
    }

    public void setEventName(final String eventName) {
        this.eventName = eventName;
    }
}
