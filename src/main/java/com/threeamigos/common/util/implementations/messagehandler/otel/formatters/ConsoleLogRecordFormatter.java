package com.threeamigos.common.util.implementations.messagehandler.otel.formatters;

import com.threeamigos.common.util.implementations.messagehandler.utils.ClassNameReducer;
import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.implementations.messagehandler.otel.OpenTelemetryAttributeValidator;
import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * A {@link LogRecordFormatter} for console-oriented plain text output.
 * <p>
 * Output format:
 * <ul>
 *   <li>Without instrumentation scope name:
 *   {@code <iso-instant> [<severity-6>] [traceId=<traceId> spanId=<spanId>] <message>}</li>
 *   <li>With instrumentation scope name:
 *   {@code <iso-instant> [<severity-6>] [traceId=<traceId> spanId=<spanId>] [<scope-name>] <message>}</li>
 * </ul>
 * <p>
 * The trace/span token is emitted only when at least one of traceId/spanId is present and non-blank,
 * and it is always rendered immediately after the severity token.
 * <p>
 * When {@code LogRecord.getInstrumentationScope().getName()} is present and not blank, it is emitted
 * after severity and trace/span tokens (if present).
 * <p>
 * If {@link #isReduceScopeClassName()} is enabled, the scope name is reduced with
 * {@link ClassNameReducer#reduce(String)}
 * before rendering. This is useful when the scope name is a fully qualified Java class name.
 *
 * @author Stefano Reksten
 */
public class ConsoleLogRecordFormatter implements LogRecordFormatter {

    private static final int SEVERITY_WIDTH = 6;
    private volatile boolean reduceScopeClassName;

    public ConsoleLogRecordFormatter() {
        this(false);
    }

    public ConsoleLogRecordFormatter(final boolean reduceScopeClassName) {
        this.reduceScopeClassName = reduceScopeClassName;
    }

    public boolean isReduceScopeClassName() {
        return reduceScopeClassName;
    }

    public void setReduceScopeClassName(final boolean reduceScopeClassName) {
        this.reduceScopeClassName = reduceScopeClassName;
    }

    @Nonnull
    @Override
    public String format(@Nonnull final LogRecord logRecord) {
        if (logRecord == null) {
            OpenTelemetryAttributeValidator.handleBundled("logRecordMustNotBeNull");
            return "";
        }
        try {
            Instant timestamp = logRecord.getTimestamp();
            if (timestamp == null) {
                OpenTelemetryAttributeValidator.handleBundled("timestampMustNotBeNull");
                timestamp = Instant.now();
            }
            String isoTimestamp = DateTimeFormatter.ISO_INSTANT.format(timestamp);
            String severity = normalizeSeverity(logRecord.getSeverityText(), logRecord.getSeverityNumber());
            String traceAndSpan = resolveTraceAndSpan(logRecord);
            String scopeName = resolveScopeName(logRecord);
            String message = resolveMessage(logRecord);
            StringBuilder out = new StringBuilder(isoTimestamp).append(" [").append(severity).append("]");
            if (!traceAndSpan.isEmpty()) {
                out.append(" [").append(traceAndSpan).append("]");
            }
            if (!scopeName.isEmpty()) {
                out.append(" [").append(scopeName).append("]");
            }
            out.append(' ').append(message);
            return out.toString();
        } catch (RuntimeException ex) {
            OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                    "failedToFormatConsoleLogRecord",
                    ex.getMessage()));
            return "";
        }
    }

    private String resolveScopeName(final LogRecord logRecord) {
        InstrumentationScope instrumentationScope = logRecord.getInstrumentationScope();
        if (instrumentationScope == null || instrumentationScope.getName() == null) {
            return "";
        }
        String scopeName = instrumentationScope.getName().trim();
        if (scopeName.isEmpty()) {
            return "";
        }
        if (reduceScopeClassName) {
            return ClassNameReducer.reduce(scopeName);
        }
        return scopeName;
    }

    private static String resolveTraceAndSpan(final LogRecord logRecord) {
        String traceId = normalizeOptionalToken(logRecord.getTraceId());
        String spanId = normalizeOptionalToken(logRecord.getSpanId());
        if (traceId == null && spanId == null) {
            return "";
        }
        if (traceId == null) {
            return "spanId=" + spanId;
        }
        if (spanId == null) {
            return "traceId=" + traceId;
        }
        return "traceId=" + traceId + " spanId=" + spanId;
    }

    private static String normalizeOptionalToken(final String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeSeverity(final String severityText, final SeverityNumber severityNumber) {
        String raw;
        if (severityText != null && !severityText.trim().isEmpty()) {
            raw = severityText.trim();
        } else if (severityNumber != null && severityNumber != SeverityNumber.UNSPECIFIED) {
            raw = severityNumber.name();
        } else {
            raw = SeverityNumber.UNSPECIFIED.name();
        }

        if (raw.length() > SEVERITY_WIDTH) {
            return raw.substring(0, SEVERITY_WIDTH);
        }
        return String.format("%-" + SEVERITY_WIDTH + "s", raw);
    }

    private static String resolveMessage(final LogRecord logRecord) {
        String stackTrace = resolveExceptionStackTrace(logRecord);
        AnyValue body = logRecord.getBody();
        String baseMessage;
        if (body != null) {
            baseMessage = anyValueToString(body);
        } else {
            String eventName = logRecord.getEventName();
            baseMessage = eventName == null ? "" : eventName;
        }
        if (stackTrace.isEmpty()) {
            return baseMessage;
        }
        if (baseMessage.isEmpty()) {
            return stackTrace;
        }
        return baseMessage + System.lineSeparator() + stackTrace;
    }

    private static String resolveExceptionStackTrace(final LogRecord logRecord) {
        for (KeyValue keyValue : logRecord.getAttributes()) {
            if (keyValue == null || !OTelTags.EXCEPTION_STACKTRACE.getValue().equals(keyValue.getKey())) {
                continue;
            }
            AnyValue value = keyValue.getValue();
            if (value == null || value.getType() != AnyValue.Type.STRING || value.asString() == null) {
                return "";
            }
            return value.asString();
        }
        return "";
    }

    private static String anyValueToString(final AnyValue value) {
        if (value == null) {
            OpenTelemetryAttributeValidator.handleBundled("valueMustNotBeNull");
            return "";
        }
        AnyValue.Type type = value.getType();
        if (type == null) {
            OpenTelemetryAttributeValidator.handleBundled("anyValueTypeMustNotBeNull");
            return "";
        }
        if (type == AnyValue.Type.EMPTY) {
            return "";
        }
        if (type == AnyValue.Type.STRING) {
            String v = value.asString();
            return v == null ? "" : v;
        }
        if (type == AnyValue.Type.BOOL) {
            return String.valueOf(value.asBoolean());
        }
        if (type == AnyValue.Type.INT) {
            return String.valueOf(value.asLong());
        }
        if (type == AnyValue.Type.DOUBLE) {
            return String.valueOf(value.asDouble());
        }
        if (type == AnyValue.Type.BYTES) {
            return Base64.getEncoder().encodeToString(value.asBytes());
        }
        if (type == AnyValue.Type.ARRAY) {
            return String.valueOf(value.asArray());
        }
        return String.valueOf(value.asKvList());
    }
}
