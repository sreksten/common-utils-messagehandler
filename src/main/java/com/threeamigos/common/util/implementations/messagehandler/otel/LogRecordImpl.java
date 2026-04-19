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
import java.util.regex.Pattern;

/**
 * A mutable implementation of the {@link LogRecord} interface.
 * {@code timestamp} defaults to {@link Instant#now()} at construction time, reflecting the moment
 * the log record is created. All other fields default to {@code null} / {@code 0} / an empty list
 * and may be set via the corresponding setters.
 *
 * @author Stefano Reksten
 */
public class LogRecordImpl implements LogRecord {

    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("(?!0{32})[0-9a-f]{32}");
    private static final Pattern SPAN_ID_PATTERN  = Pattern.compile("(?!0{16})[0-9a-f]{16}");

    private Instant timestamp = Instant.now();
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
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
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
        if (traceId != null && !TRACE_ID_PATTERN.matcher(traceId).matches()) {
            throw new IllegalArgumentException(
                    "traceId must be 32 lowercase hex characters and not all zeros, got: \"" + traceId + "\"");
        }
        this.traceId = traceId;
    }

    @Override
    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(final String spanId) {
        if (spanId != null && !SPAN_ID_PATTERN.matcher(spanId).matches()) {
            throw new IllegalArgumentException(
                    "spanId must be 16 lowercase hex characters and not all zeros, got: \"" + spanId + "\"");
        }
        this.spanId = spanId;
    }

    @Override
    public int getTraceFlags() {
        return traceFlags;
    }

    public void setTraceFlags(final int traceFlags) {
        if (traceFlags < 0 || traceFlags > 0xFF) {
            throw new IllegalArgumentException("traceFlags must be in the range 0x00–0xFF, got: " + traceFlags);
        }
        this.traceFlags = traceFlags;
    }

    @Override
    public String getSeverityText() {
        return severityText;
    }

    @Override
    public SeverityNumber getSeverityNumber() {
        return severityNumber;
    }

    public void setSeverityText(final String severityText) {
        this.severityText = severityText;
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
        this.attributes = OpenTelemetryAttributeValidator.copyAndValidateKeyValues(attributes, "logRecord attributes");
    }

    @Override
    public int getDroppedAttributesCount() {
        return droppedAttributesCount;
    }

    public void setDroppedAttributesCount(final int droppedAttributesCount) {
        if (droppedAttributesCount < 0) {
            throw new IllegalArgumentException("droppedAttributesCount must not be negative, got: " + droppedAttributesCount);
        }
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
