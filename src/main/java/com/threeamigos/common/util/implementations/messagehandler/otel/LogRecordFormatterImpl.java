package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;

import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * A {@link LogRecordFormatter} that serializes a {@link LogRecord} as a single-line NDJSON object
 * following the
 * <a href="https://opentelemetry.io/docs/specs/otel/logs/data-model/">OpenTelemetry Log Data Model</a>
 * and the
 * <a href="https://opentelemetry.io/docs/specs/otlp/#otlphttp-json-encoding">OTLP JSON encoding</a>.
 * <p>
 * Key encoding rules:
 * <ul>
 *   <li>Timestamps are encoded as nanoseconds since Unix epoch in a decimal string, e.g.
 *       {@code "timeUnixNano":"1581452772000000321"}.</li>
 *   <li>The body is an {@link AnyValue} encoded as an OTel AnyValue JSON object.</li>
 *   <li>Attributes (and resource/scope attributes) are encoded as a JSON array of
 *       {@code {"key":"...","value":{...}}} objects, where the value wrapper follows OTel AnyValue
 *       typing: {@code stringValue}, {@code intValue} (decimal string), {@code doubleValue},
 *       {@code boolValue}, {@code arrayValue}, {@code kvlistValue}, or {@code bytesValue}
 *       (base64-encoded string).</li>
 *   <li>{@code intValue} is always encoded as a decimal string per the OTLP JSON spec.</li>
 *   <li>The instrumentation scope is encoded as
 *       {@code "scope":{"name":"...","version":"...","attributes":[...],"droppedAttributesCount":N}}.</li>
 *   <li>The resource is encoded as
 *       {@code "resource":{"attributes":[...],"droppedAttributesCount":N}}.</li>
 *   <li>{@link SeverityNumber#UNSPECIFIED} and zero-value numeric fields are omitted.</li>
 *   <li>Fields that are {@code null} or empty are omitted.</li>
 * </ul>
 * <p>
 * Example output for a fully-populated record:
 * <pre>
 * {"timeUnixNano":"1745056800000000000","observedTimeUnixNano":"1745056800000000000",
 *  "traceId":"5b8efff798038103d269b633813fc60c","spanId":"eee19b7ec3c1b174","flags":1,
 *  "severityText":"INFO","severityNumber":9,
 *  "body":{"stringValue":"User logged in"},
 *  "resource":{"attributes":[{"key":"service.name","value":{"stringValue":"my-service"}}],"droppedAttributesCount":0},
 *  "scope":{"name":"com.example","version":"1.0.0","attributes":[],"droppedAttributesCount":0},
 *  "attributes":[{"key":"userId","value":{"stringValue":"42"}}],
 *  "droppedAttributesCount":0,"eventName":"user.login"}
 * </pre>
 *
 * @author Stefano Reksten
 */
public class LogRecordFormatterImpl implements LogRecordFormatter {

    @Nonnull
    @Override
    public String format(@Nonnull final LogRecord logRecord) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        first = appendTimeNanos(sb, first, "timeUnixNano",         logRecord.getTimestamp());
        first = appendTimeNanos(sb, first, "observedTimeUnixNano", logRecord.getObservedTimestamp());
        first = appendString   (sb, first, "traceId",              logRecord.getTraceId());
        first = appendString   (sb, first, "spanId",               logRecord.getSpanId());
        if (logRecord.getTraceFlags() != 0) {
            first = appendInt  (sb, first, "flags",                logRecord.getTraceFlags());
        }
        first = appendString   (sb, first, "severityText",         logRecord.getSeverityText());
        SeverityNumber sn = logRecord.getSeverityNumber();
        if (sn != null && sn != SeverityNumber.UNSPECIFIED) {
            first = appendInt  (sb, first, "severityNumber",       sn.getValue());
        }
        first = appendBody     (sb, first,                         logRecord.getBody());
        first = appendResource (sb, first,                         logRecord.getResource());
        first = appendScope    (sb, first,                         logRecord.getInstrumentationScope());
        first = appendKeyValueArray(sb, first, "attributes",       logRecord.getAttributes());
        if (logRecord.getDroppedAttributesCount() != 0) {
            first = appendInt  (sb, first, "droppedAttributesCount", logRecord.getDroppedAttributesCount());
        }
              appendString     (sb, first, "eventName",            logRecord.getEventName());
        sb.append('}');
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Field helpers
    // -------------------------------------------------------------------------

    private static boolean appendTimeNanos(final StringBuilder sb, final boolean first,
                                           final String key, final Instant value) {
        if (value == null) {
            return first;
        }
        long nanos = value.getEpochSecond() * 1_000_000_000L + value.getNano();
        separator(sb, first);
        sb.append('"').append(key).append("\":\"").append(nanos).append('"');
        return false;
    }

    private static boolean appendString(final StringBuilder sb, final boolean first,
                                        final String key, final String value) {
        if (value == null) {
            return first;
        }
        separator(sb, first);
        sb.append('"').append(key).append("\":\"").append(escape(value)).append('"');
        return false;
    }

    private static boolean appendInt(final StringBuilder sb, final boolean first,
                                     final String key, final int value) {
        separator(sb, first);
        sb.append('"').append(key).append("\":").append(value);
        return false;
    }

    /** Encodes the body {@link AnyValue} as {@code "body":<anyvalue>}. */
    private static boolean appendBody(final StringBuilder sb, final boolean first,
                                      final AnyValue body) {
        if (body == null) {
            return first;
        }
        separator(sb, first);
        sb.append("\"body\":");
        appendAnyValue(sb, body);
        return false;
    }

    /** Encodes the {@link Resource} as {@code "resource":{"attributes":[...],"droppedAttributesCount":N}}. */
    private static boolean appendResource(final StringBuilder sb, final boolean first,
                                          final Resource resource) {
        if (resource == null) {
            return first;
        }
        separator(sb, first);
        sb.append("\"resource\":{");
        sb.append("\"attributes\":");
        appendKeyValueArrayInline(sb, resource.getAttributes());
        if (resource.getDroppedAttributesCount() != 0) {
            sb.append(",\"droppedAttributesCount\":").append(resource.getDroppedAttributesCount());
        }
        sb.append('}');
        return false;
    }

    /** Encodes the {@link InstrumentationScope}. */
    private static boolean appendScope(final StringBuilder sb, final boolean first,
                                       final InstrumentationScope scope) {
        if (scope == null) {
            return first;
        }
        separator(sb, first);
        sb.append("\"scope\":{");
        boolean scopeFirst = true;
        if (scope.getName() != null) {
            sb.append("\"name\":\"").append(escape(scope.getName())).append('"');
            scopeFirst = false;
        }
        if (scope.getVersion() != null) {
            if (!scopeFirst) sb.append(',');
            sb.append("\"version\":\"").append(escape(scope.getVersion())).append('"');
            scopeFirst = false;
        }
        if (!scope.getAttributes().isEmpty()) {
            if (!scopeFirst) sb.append(',');
            sb.append("\"attributes\":");
            appendKeyValueArrayInline(sb, scope.getAttributes());
            scopeFirst = false;
        }
        if (scope.getDroppedAttributesCount() != 0) {
            if (!scopeFirst) sb.append(',');
            sb.append("\"droppedAttributesCount\":").append(scope.getDroppedAttributesCount());
        }
        sb.append('}');
        return false;
    }

    private static boolean appendKeyValueArray(final StringBuilder sb, final boolean first,
                                               final String fieldName,
                                               final List<KeyValue> attrs) {
        if (attrs == null || attrs.isEmpty()) {
            return first;
        }
        separator(sb, first);
        sb.append('"').append(fieldName).append("\":");
        appendKeyValueArrayInline(sb, attrs);
        return false;
    }

    private static void appendKeyValueArrayInline(final StringBuilder sb,
                                                   final List<KeyValue> attrs) {
        sb.append('[');
        boolean firstEntry = true;
        for (KeyValue kv : attrs) {
            if (!firstEntry) sb.append(',');
            sb.append("{\"key\":\"").append(escape(kv.getKey())).append("\",\"value\":");
            appendAnyValue(sb, kv.getValue());
            sb.append('}');
            firstEntry = false;
        }
        sb.append(']');
    }

    /**
     * Serialises an {@link AnyValue} as an OTel AnyValue JSON object.
     * <ul>
     *   <li>{@link AnyValue.Type#STRING}  → {@code {"stringValue":"..."}}</li>
     *   <li>{@link AnyValue.Type#BOOL}    → {@code {"boolValue":true}}</li>
     *   <li>{@link AnyValue.Type#INT}     → {@code {"intValue":"42"}} (decimal string per spec)</li>
     *   <li>{@link AnyValue.Type#DOUBLE}  → {@code {"doubleValue":3.14}}</li>
     *   <li>{@link AnyValue.Type#ARRAY}   → {@code {"arrayValue":{"values":[...]}}}</li>
     *   <li>{@link AnyValue.Type#KVLIST}  → {@code {"kvlistValue":{"values":[...]}}}</li>
     *   <li>{@link AnyValue.Type#BYTES}   → {@code {"bytesValue":"<base64>"}}</li>
     * </ul>
     */
    private static void appendAnyValue(final StringBuilder sb, final AnyValue value) {
        switch (value.getType()) {
            case STRING:
                sb.append("{\"stringValue\":\"").append(escape(value.asString())).append("\"}");
                break;
            case BOOL:
                sb.append("{\"boolValue\":").append(value.asBoolean()).append('}');
                break;
            case INT:
                sb.append("{\"intValue\":\"").append(value.asLong()).append("\"}");
                break;
            case DOUBLE:
                sb.append("{\"doubleValue\":").append(value.asDouble()).append('}');
                break;
            case ARRAY:
                sb.append("{\"arrayValue\":{\"values\":[");
                boolean firstArr = true;
                for (AnyValue element : value.asArray()) {
                    if (!firstArr) sb.append(',');
                    appendAnyValue(sb, element);
                    firstArr = false;
                }
                sb.append("]}}");
                break;
            case KVLIST:
                sb.append("{\"kvlistValue\":{\"values\":");
                appendKeyValueArrayInline(sb, value.asKvList());
                sb.append("}}");
                break;
            case BYTES:
                sb.append("{\"bytesValue\":\"")
                  .append(Base64.getEncoder().encodeToString(value.asBytes()))
                  .append("\"}");
                break;
            default:
                sb.append("{\"stringValue\":\"\"}");
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private static void separator(final StringBuilder sb, final boolean first) {
        if (!first) {
            sb.append(',');
        }
    }

    private static String escape(final String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
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
}
