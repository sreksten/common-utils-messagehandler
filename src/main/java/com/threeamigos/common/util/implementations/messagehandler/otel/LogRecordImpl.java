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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A mutable implementation of the {@link LogRecord} interface.
 * <p>
 * Specification references:
 * <a href="https://opentelemetry.io/docs/specs/otel/logs/data-model/">OpenTelemetry Logs Data Model</a>,
 * <a href="https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/logs/v1/logs.proto">OTLP logs.proto (LogRecord)</a>,
 * <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>.
 * <p>
 * {@code timestamp} defaults to {@link Instant#now()} at construction time, reflecting the moment
 * the log record is created. All other fields default to {@code null} / {@code 0} / an empty list
 * and may be set via the corresponding setters.
 * <p>
 * Hint: if you're looking where to look for:
 * <ul>
 *     <li>Fully qualified method name: <code>code.function.name</code></li>
 *     <li>Class name: <code>code.namespace</code> (deprecated attribute)</li>
 *     <li>Logger class name (e.g., Java logger name: <code>InstrumentationScope.name</code>)</li>
 *     <li>Exception message: <code>exception.message</code> in attributes (e.g., "Division by zero")</li>
 *     <li>Exception type: <code>exception.type</code> in attributes (e.g., java.net.ConnectException)</li>
 *     <li>Exception stacktrace: <code>exception.stacktrace</code> in attributes</li>
 * </ul>
 *
 * @author Stefano Reksten
 */
public class LogRecordImpl implements LogRecord {

    private static final Logger LOGGER = Logger.getLogger(LogRecordImpl.class.getName());
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
                handleBundledNonBlocking("timestampMustNotBeNull");
                this.timestamp = Instant.now();
                return;
            }
            this.timestamp = normalizeTimestamp(timestamp, Instant.now());
        } catch (RuntimeException ex) {
            handleNonBlocking("Failed to set timestamp: " + ex.getMessage());
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
            handleNonBlocking("Failed to set observedTimestamp: " + ex.getMessage());
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
            String normalizedTraceId = traceId.trim().toLowerCase(Locale.ROOT);
            if (!TRACE_ID_PATTERN.matcher(normalizedTraceId).matches()) {
                handleNonBlocking(safeBundleFormat(
                        "traceIdInvalid",
                        traceId));
                this.traceId = null;
                return;
            }
            this.traceId = normalizedTraceId;
        } catch (RuntimeException ex) {
            handleNonBlocking("Failed to set traceId: " + ex.getMessage());
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
            String normalizedSpanId = spanId.trim().toLowerCase(Locale.ROOT);
            if (!SPAN_ID_PATTERN.matcher(normalizedSpanId).matches()) {
                handleNonBlocking(safeBundleFormat(
                        "spanIdInvalid",
                        spanId));
                this.spanId = null;
                return;
            }
            this.spanId = normalizedSpanId;
        } catch (RuntimeException ex) {
            handleNonBlocking("Failed to set spanId: " + ex.getMessage());
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
                handleNonBlocking(safeBundleFormat(
                        "traceFlagsOutOfRange",
                        traceFlags));
                this.traceFlags = traceFlags & 0xFF;
                return;
            }
            this.traceFlags = traceFlags;
        } catch (RuntimeException ex) {
            handleNonBlocking("Failed to set traceFlags: " + ex.getMessage());
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
            String normalizedSeverityText = severityText.trim();
            this.severityText = normalizedSeverityText.isEmpty() ? null : normalizedSeverityText;
        } catch (RuntimeException ex) {
            handleNonBlocking("Failed to set severityText: " + ex.getMessage());
            this.severityText = null;
        }
    }

    public void setSeverityNumber(final SeverityNumber severityNumber) {
        try {
            this.severityNumber = severityNumber != null ? severityNumber : SeverityNumber.UNSPECIFIED;
        } catch (RuntimeException ex) {
            handleNonBlocking("Failed to set severityNumber: " + ex.getMessage());
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
                    OpenTelemetryAttributeValidator.copyValidateAndLimitKeyValuesLenient(
                    attributes,
                    safeBundleGet("logRecordAttributesFieldName", "logRecord attributes")
                    );
            this.attributes = validationResult.getAttributes();
            this.droppedAttributesCount = validationResult.getDroppedAttributesCount();
        } catch (RuntimeException ex) {
            handleNonBlocking("Failed to set logRecord attributes: " + ex.getMessage());
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
            String normalizedEventName = eventName.trim();
            this.eventName = normalizedEventName.isEmpty() ? null : normalizedEventName;
        } catch (RuntimeException ex) {
            handleNonBlocking("Failed to set eventName: " + ex.getMessage());
            this.eventName = null;
        }
    }

    private static Instant normalizeTimestamp(final Instant input, final Instant fallback) {
        if (!isUint64NanoTimestamp(input)) {
            handleNonBlocking("Timestamp is outside OTLP uint64 nanoseconds range: " + input);
            return fallback;
        }
        return input;
    }

    private static boolean isUint64NanoTimestamp(final Instant value) {
        BigInteger nanos = BigInteger.valueOf(value.getEpochSecond())
                .multiply(NANOS_PER_SECOND)
                .add(BigInteger.valueOf(value.getNano()));
        return nanos.signum() >= 0 && nanos.compareTo(UINT64_MAX) <= 0;
    }

    private static void handleBundledNonBlocking(final String messageKey) {
        try {
            OpenTelemetryAttributeValidator.handleBundled(messageKey);
        } catch (RuntimeException ex) {
            safeLog(Level.SEVERE, messageKey, ex);
        }
    }

    private static void handleNonBlocking(final String message) {
        try {
            OpenTelemetryAttributeValidator.handle(message);
        } catch (RuntimeException ex) {
            safeLog(Level.SEVERE, message, ex);
        }
    }

    private static String safeBundleFormat(final String key, final Object... args) {
        try {
            return MessageHandlerResourceBundle.format(key, args);
        } catch (RuntimeException ex) {
            return key + " " + Arrays.toString(args);
        }
    }

    private static String safeBundleGet(final String key, final String fallback) {
        try {
            return MessageHandlerResourceBundle.get(key);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static void safeLog(final Level level, final String message, final Throwable throwable) {
        try {
            LOGGER.log(level, message, throwable);
        } catch (RuntimeException ignored) {
            // Intentionally swallow logging failures to keep setters non-blocking.
        }
    }
}
