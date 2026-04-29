package com.threeamigos.common.util.implementations.messagehandler.otel.formatters;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.implementations.messagehandler.otel.AnyValueFactory;
import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordImpl;
import com.threeamigos.common.util.implementations.messagehandler.otel.OpenTelemetryAttributeValidator;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * A {@link LogRecordFormatter} that serializes a {@link LogRecord} as a naked JSON object
 * ({@code {...}}), without OTLP {@code ResourceLogs / ScopeLogs} envelope fields.
 *
 * @author Stefano Reksten
 */
public class RawJsonRecordFormatter implements LogRecordFormatter {

    private static final String F_TIME_UNIX_NANO = "timeUnixNano";
    private static final String F_OBSERVED_TIME_UNIX_NANO = "observedTimeUnixNano";
    private static final String F_SEVERITY_NUMBER = "severityNumber";
    private static final String F_SEVERITY_TEXT = "severityText";
    private static final String F_BODY = "body";
    private static final String F_ATTRIBUTES = "attributes";
    private static final String F_DROPPED_ATTRIBUTES_COUNT = "droppedAttributesCount";
    private static final String F_FLAGS = "flags";
    private static final String F_TRACE_ID = "traceId";
    private static final String F_SPAN_ID = "spanId";
    private static final String F_EVENT_NAME = "eventName";

    private static final String F_KEY = "key";
    private static final String F_VALUE = "value";

    private static final String F_STRING_VALUE = "stringValue";
    private static final String F_BOOL_VALUE = "boolValue";
    private static final String F_INT_VALUE = "intValue";
    private static final String F_DOUBLE_VALUE = "doubleValue";
    private static final String F_ARRAY_VALUE = "arrayValue";
    private static final String F_KVLIST_VALUE = "kvlistValue";
    private static final String F_BYTES_VALUE = "bytesValue";
    private static final String F_VALUES = "values";

    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger UINT64_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

    @Nonnull
    @Override
    public String format(@Nonnull final LogRecord logRecord) {
        if (logRecord == null) {
            OpenTelemetryAttributeValidator.handleBundled("logRecordMustNotBeNull");
            return "{}";
        }
        try {
            StringBuilder sb = new StringBuilder("{");
            boolean first;
            first = appendTimeNanos(sb, true, F_TIME_UNIX_NANO, logRecord.getTimestamp());
            first = appendTimeNanos(sb, first, F_OBSERVED_TIME_UNIX_NANO, logRecord.getObservedTimestamp());
            first = appendString(sb, first, F_TRACE_ID, logRecord.getTraceId());
            first = appendString(sb, first, F_SPAN_ID, logRecord.getSpanId());
            if (logRecord.getTraceFlags() != 0) {
                first = appendInt(sb, first, F_FLAGS, logRecord.getTraceFlags());
            }
            first = appendString(sb, first, F_SEVERITY_TEXT, logRecord.getSeverityText());
            SeverityNumber severityNumber = logRecord.getSeverityNumber();
            if (severityNumber != null && severityNumber != SeverityNumber.UNSPECIFIED) {
                first = appendInt(sb, first, F_SEVERITY_NUMBER, severityNumber.getValue());
            }
            first = appendBody(sb, first, logRecord.getBody());
            first = appendKeyValueArray(sb, first, logRecord.getAttributes());
            int droppedAttributesCount = droppedAttributesCount(logRecord);
            if (droppedAttributesCount != 0) {
                first = appendInt(sb, first, F_DROPPED_ATTRIBUTES_COUNT, droppedAttributesCount);
            }
            appendString(sb, first, F_EVENT_NAME, logRecord.getEventName());
            sb.append('}');
            return sb.toString();
        } catch (RuntimeException ex) {
            OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                    "failedToFormatRawJsonLogRecord",
                    ex.getMessage()));
            return "{}";
        }
    }

    static void appendKeyValueArrayInline(final StringBuilder sb, final List<KeyValue> attrs) {
        if (attrs == null) {
            OpenTelemetryAttributeValidator.handleBundled("nullAttributeListProvidedToFormatter");
            sb.append("[]");
            return;
        }
        sb.append('[');
        boolean firstEntry = true;
        for (KeyValue keyValue : attrs) {
            if (keyValue == null) {
                OpenTelemetryAttributeValidator.handleBundled("nullKeyValueEntryProvidedToFormatter");
                continue;
            }
            if (!firstEntry) {
                sb.append(',');
            }
            String key = keyValue.getKey();
            if (key == null) {
                OpenTelemetryAttributeValidator.handleBundled("nullKeyValueKeyProvidedToFormatter");
                key = "unknown";
            }
            AnyValue value = keyValue.getValue();
            if (value == null) {
                OpenTelemetryAttributeValidator.handleBundled("nullKeyValueValueProvidedToFormatter");
                value = AnyValueFactory.empty();
            }
            sb.append("{\"").append(F_KEY).append("\":\"").append(escape(key))
                    .append("\",\"").append(F_VALUE).append("\":");
            appendAnyValue(sb, value);
            sb.append('}');
            firstEntry = false;
        }
        sb.append(']');
    }

    static String escape(final String value) {
        if (value == null) {
            OpenTelemetryAttributeValidator.handleBundled("valueMustNotBeNull");
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static boolean appendTimeNanos(final StringBuilder sb, final boolean first, final String key, final Instant value) {
        if (value == null) {
            return first;
        }
        String nanos = toUnixNanosDecimalString(value);
        if (nanos == null) {
            return first;
        }
        separator(sb, first);
        sb.append('"').append(key).append("\":\"").append(nanos).append('"');
        return false;
    }

    private static String toUnixNanosDecimalString(final Instant value) {
        BigInteger nanos = BigInteger.valueOf(value.getEpochSecond())
                .multiply(NANOS_PER_SECOND)
                .add(BigInteger.valueOf(value.getNano()));
        if (nanos.signum() < 0) {
            OpenTelemetryAttributeValidator.handleBundled("otlpTimestampMustBeUnsigned");
            return null;
        }
        if (nanos.compareTo(UINT64_MAX) > 0) {
            OpenTelemetryAttributeValidator.handleBundled("otlpTimestampMustFitUint64");
            return null;
        }
        return nanos.toString();
    }

    private static boolean appendString(final StringBuilder sb, final boolean first, final String key, final String value) {
        if (value == null) {
            return first;
        }
        separator(sb, first);
        sb.append('"').append(key).append("\":\"").append(escape(value)).append('"');
        return false;
    }

    private static boolean appendInt(final StringBuilder sb, final boolean first, final String key, final int value) {
        separator(sb, first);
        sb.append('"').append(key).append("\":").append(value);
        return false;
    }

    private static boolean appendBody(final StringBuilder sb, final boolean first, final AnyValue body) {
        if (body == null) {
            return first;
        }
        separator(sb, first);
        sb.append('"').append(F_BODY).append("\":");
        appendAnyValue(sb, body);
        return false;
    }

    private static int droppedAttributesCount(final LogRecord logRecord) {
        if (logRecord instanceof LogRecordImpl) {
            return ((LogRecordImpl) logRecord).getDroppedAttributesCount();
        }
        return 0;
    }

    private static boolean appendKeyValueArray(final StringBuilder sb, final boolean first, final List<KeyValue> attrs) {
        if (attrs == null || attrs.isEmpty()) {
            return first;
        }
        separator(sb, first);
        sb.append('"').append(F_ATTRIBUTES).append("\":");
        appendKeyValueArrayInline(sb, attrs);
        return false;
    }

    private static void appendAnyValue(final StringBuilder sb, final AnyValue value) {
        if (value == null) {
            OpenTelemetryAttributeValidator.handleBundled("valueMustNotBeNull");
            sb.append("{}");
            return;
        }
        AnyValue.Type type = value.getType();
        if (type == null) {
            OpenTelemetryAttributeValidator.handleBundled("anyValueTypeMustNotBeNull");
            sb.append("{}");
            return;
        }

        if (type == AnyValue.Type.EMPTY) {
            sb.append("{}");
        } else if (type == AnyValue.Type.STRING) {
            sb.append("{\"").append(F_STRING_VALUE).append("\":\"").append(escape(value.asString())).append("\"}");
        } else if (type == AnyValue.Type.BOOL) {
            sb.append("{\"").append(F_BOOL_VALUE).append("\":").append(value.asBoolean()).append('}');
        } else if (type == AnyValue.Type.INT) {
            sb.append("{\"").append(F_INT_VALUE).append("\":\"").append(value.asLong()).append("\"}");
        } else if (type == AnyValue.Type.DOUBLE) {
            sb.append("{\"").append(F_DOUBLE_VALUE).append("\":");
            double d = value.asDouble();
            if (Double.isNaN(d)) {
                sb.append("\"NaN\"");
            } else if (d == Double.POSITIVE_INFINITY) {
                sb.append("\"Infinity\"");
            } else if (d == Double.NEGATIVE_INFINITY) {
                sb.append("\"-Infinity\"");
            } else {
                sb.append(d);
            }
            sb.append('}');
        } else if (type == AnyValue.Type.ARRAY) {
            sb.append("{\"").append(F_ARRAY_VALUE).append("\":{\"").append(F_VALUES).append("\":[");
            boolean firstArr = true;
            for (AnyValue element : value.asArray()) {
                if (!firstArr) {
                    sb.append(',');
                }
                appendAnyValue(sb, element);
                firstArr = false;
            }
            sb.append("]}}");
        } else if (type == AnyValue.Type.KVLIST) {
            sb.append("{\"").append(F_KVLIST_VALUE).append("\":{\"").append(F_VALUES).append("\":");
            appendKeyValueArrayInline(sb, value.asKvList());
            sb.append("}}");
        } else {
            sb.append("{\"").append(F_BYTES_VALUE).append("\":\"")
                    .append(Base64.getEncoder().encodeToString(value.asBytes()))
                    .append("\"}");
        }
    }

    private static void separator(final StringBuilder sb, final boolean first) {
        if (!first) {
            sb.append(',');
        }
    }
}
