package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A mutable implementation of a {@link LogRecord}.
 *
 * @author Stefano Reksten
 */
public class LogRecordImpl implements LogRecord {

    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("(?!0{32})[0-9a-f]{32}");
    private static final Pattern SPAN_ID_PATTERN  = Pattern.compile("(?!0{16})[0-9a-f]{16}");
    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger UINT64_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

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
        try {
            if (timestamp == null) {
                reportBundled("timestampMustNotBeNull");
                this.timestamp = Instant.now();
                return;
            }
            this.timestamp = normalizeTimestamp(timestamp, Instant.now());
        } catch (RuntimeException ex) {
            reportBundled("failedToSetTimestamp", ex.getMessage());
            this.timestamp = Instant.now();
        }
    }

    @Override
    public Instant getObservedTimestamp() {
        return observedTimestamp;
    }

    public void setObservedTimestamp(final Instant observedTimestamp) {
        try {
            if (observedTimestamp == null) {
                this.observedTimestamp = null;
                return;
            }
            this.observedTimestamp = normalizeTimestamp(observedTimestamp, null);
        } catch (RuntimeException ex) {
            reportBundled("failedToSetObservedTimestamp", ex.getMessage());
            this.observedTimestamp = null;
        }
    }

    @Override
    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(final String traceId) {
        try {
            if (traceId == null) {
                this.traceId = null;
                return;
            }
            String normalizedTraceId = normalizeText(traceId).toLowerCase(Locale.ROOT);
            if (!traceIdPattern().matcher(normalizedTraceId).matches()) {
                report(safeBundleFormat(
                        "traceIdInvalid",
                        traceId));
                this.traceId = null;
                return;
            }
            this.traceId = normalizedTraceId;
        } catch (RuntimeException ex) {
            reportBundled("failedToSetTraceId", ex.getMessage());
            this.traceId = null;
        }
    }

    @Override
    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(final String spanId) {
        try {
            if (spanId == null) {
                this.spanId = null;
                return;
            }
            String normalizedSpanId = normalizeText(spanId).toLowerCase(Locale.ROOT);
            if (!spanIdPattern().matcher(normalizedSpanId).matches()) {
                report(safeBundleFormat(
                        "spanIdInvalid",
                        spanId));
                this.spanId = null;
                return;
            }
            this.spanId = normalizedSpanId;
        } catch (RuntimeException ex) {
            reportBundled("failedToSetSpanId", ex.getMessage());
            this.spanId = null;
        }
    }

    @Override
    public int getTraceFlags() {
        return traceFlags;
    }

    public void setTraceFlags(final int traceFlags) {
        try {
            if (traceFlags < 0 || traceFlags > 0xFF) {
                report(safeBundleFormat(
                        "traceFlagsOutOfRange",
                        traceFlags));
                this.traceFlags = traceFlags & 0xFF;
                return;
            }
            this.traceFlags = traceFlags;
        } catch (RuntimeException ex) {
            reportBundled("failedToSetTraceFlags", ex.getMessage());
            this.traceFlags = 0;
        }
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
        try {
            if (severityText == null) {
                this.severityText = null;
                return;
            }
            String normalizedSeverityText = normalizeText(severityText);
            this.severityText = normalizedSeverityText.isEmpty() ? null : normalizedSeverityText;
        } catch (RuntimeException ex) {
            reportBundled("failedToSetSeverityText", ex.getMessage());
            this.severityText = null;
        }
    }

    public void setSeverityNumber(final SeverityNumber severityNumber) {
        try {
            this.severityNumber = normalizeSeverityNumber(severityNumber);
        } catch (RuntimeException ex) {
            reportBundled("failedToSetSeverityNumber", ex.getMessage());
            this.severityNumber = SeverityNumber.UNSPECIFIED;
        }
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
        try {
            OpenTelemetryAttributeValidator.ValidationResult validationResult =
                    validateAttributes(
                    attributes,
                    safeBundleGet("logRecordAttributesFieldName", "logRecordAttributesFieldName")
                    );
            this.attributes = validationResult.getAttributes();
            this.droppedAttributesCount = validationResult.getDroppedAttributesCount();
        } catch (RuntimeException ex) {
            reportBundled("failedToSetLogRecordAttributes", ex.getMessage());
            this.attributes = new ArrayList<>();
            this.droppedAttributesCount = 0;
        }
    }

    public int getDroppedAttributesCount() {
        return droppedAttributesCount;
    }

    @Override
    public String getEventName() {
        return eventName;
    }

    public void setEventName(final String eventName) {
        try {
            if (eventName == null) {
                this.eventName = null;
                return;
            }
            String normalizedEventName = normalizeText(eventName);
            this.eventName = normalizedEventName.isEmpty() ? null : normalizedEventName;
        } catch (RuntimeException ex) {
            reportBundled("failedToSetEventName", ex.getMessage());
            this.eventName = null;
        }
    }

    Instant normalizeTimestamp(final Instant input, final Instant fallback) {
        if (!isUint64NanoTimestamp(input)) {
            reportBundled("timestampOutsideOtlpRange", input);
            return fallback;
        }
        return input;
    }

    boolean isUint64NanoTimestamp(final Instant value) {
        BigInteger nanos = BigInteger.valueOf(value.getEpochSecond())
                .multiply(NANOS_PER_SECOND)
                .add(BigInteger.valueOf(value.getNano()));
        return nanos.signum() >= 0 && nanos.compareTo(UINT64_MAX) <= 0;
    }

    void reportBundled(final String messageKey) {
        OpenTelemetryAttributeValidator.reportBundled(messageKey);
    }

    void reportBundled(final String messageKey, final Object... args) {
        OpenTelemetryAttributeValidator.reportBundled(messageKey, args);
    }

    void report(final String message) {
        OpenTelemetryAttributeValidator.report(message);
    }

    String normalizeText(final String value) {
        return value.trim();
    }

    SeverityNumber normalizeSeverityNumber(final SeverityNumber value) {
        return value != null ? value : SeverityNumber.UNSPECIFIED;
    }

    OpenTelemetryAttributeValidator.ValidationResult validateAttributes(final List<KeyValue> attributes,
                                                                        final String fieldName) {
        return OpenTelemetryAttributeValidator.copyValidateAndLimitKeyValuesLenient(attributes, fieldName);
    }

    Pattern traceIdPattern() {
        return TRACE_ID_PATTERN;
    }

    Pattern spanIdPattern() {
        return SPAN_ID_PATTERN;
    }

    private static String safeBundleFormat(final String key, final Object... args) {
        try {
            return MessageHandlerResourceBundle.format(key, args);
        } catch (RuntimeException ex) {
            return key;
        }
    }

    private static String safeBundleGet(final String key, final String fallback) {
        try {
            return MessageHandlerResourceBundle.get(key);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }
}
