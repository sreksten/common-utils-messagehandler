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
 * A {@link LogRecordFormatter} that serializes a {@link LogRecord} following the
 * <a href="https://opentelemetry.io/docs/specs/otel/logs/data-model/">OpenTelemetry Log Data Model</a>
 * and the
 * <a href="https://opentelemetry.io/docs/specs/otlp/#otlphttp-json-encoding">OTLP JSON</a> encoding.
 * <p>
 * {@link #formatRecord(LogRecord)} produces the naked log record JSON object {@code {...}}.
 * {@link #format(LogRecord)} wraps that output in the full {@code ExportLogsServiceRequest} envelope,
 * delegating to {@link #formatRecord(LogRecord)} for the inner content.
 * <p>
 * The output structure is:
 * <pre>
 * {"resourceLogs":[{
 *   "resource":{"attributes":[...]},
 *   "schemaUrl":"...",              ← Resource.getSchemaUrl(), omitted if null
 *   "scopeLogs":[{
 *     "scope":{"name":"...","version":"...","attributes":[...]},
 *     "schemaUrl":"...",            ← InstrumentationScope.getSchemaUrl(), omitted if null
 *     "logRecords":[{ ... }]
 *   }]
 * }]}
 * </pre>
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
 *   <li>{@code schemaUrl} for the resource is emitted at the {@code ResourceLogs} level,
 *       not inside the {@code resource} object (per OTLP proto layout).</li>
 *   <li>{@code schemaUrl} for the scope is emitted at the {@code ScopeLogs} level,
 *       not inside the {@code scope} object (per OTLP proto layout).</li>
 *   <li>{@link SeverityNumber#UNSPECIFIED} and zero-value numeric fields are omitted.</li>
 *   <li>Fields that are {@code null} or empty are omitted.</li>
 * </ul>
 * <p>
 * Example output for a fully populated record:
 * <pre>
 * {"resourceLogs":[{"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"my-service"}}]},
 *  "schemaUrl":"https://opentelemetry.io/schemas/1.25.0",
 *  "scopeLogs":[{"scope":{"name":"com.example","version":"1.0.0"},
 *  "schemaUrl":"https://opentelemetry.io/schemas/1.25.0",
 *  "logRecords":[{"timeUnixNano":"1745056800000000000","observedTimeUnixNano":"1745056800000000000",
 *  "traceId":"5b8efff798038103d269b633813fc60c","spanId":"eee19b7ec3c1b174","flags":1,
 *  "severityText":"INFO","severityNumber":9,"body":{"stringValue":"User logged in"},
 *  "attributes":[{"key":"userId","value":{"stringValue":"42"}}],
 *  "eventName":"user.login"}]}]}]}
 * </pre>
 *
 * @author Stefano Reksten
 */
public class LogRecordFormatterImpl implements LogRecordFormatter {

    // -------------------------------------------------------------------------
    // OTLP JSON field name constants
    // Each constant holds the lowerCamelCase JSON key produced by the standard
    // proto3 → JSON name mapping (snake_case → lowerCamelCase).
    // Proto source: opentelemetry/proto/logs/v1/logs.proto
    //               opentelemetry/proto/common/v1/common.proto
    //               opentelemetry/proto/resource/v1/resource.proto
    // -------------------------------------------------------------------------

    // --- ExportLogsServiceRequest / ResourceLogs / ScopeLogs envelope --------

    /** Top-level field of {@code ExportLogsServiceRequest}. Proto: {@code resource_logs}. */
    private static final String F_RESOURCE_LOGS = "resourceLogs";

    /** Field of {@code ResourceLogs}. Proto: {@code resource}. */
    private static final String F_RESOURCE = "resource";

    /** Field of {@code ResourceLogs} and {@code ScopeLogs}. Proto: {@code schema_url}.
     *  Placed at the parent level, NOT inside the resource/scope object. */
    private static final String F_SCHEMA_URL = "schemaUrl";

    /** Field of {@code ResourceLogs}. Proto: {@code scope_logs}. */
    private static final String F_SCOPE_LOGS = "scopeLogs";

    /** Field of {@code ScopeLogs}. Proto: {@code scope}. */
    private static final String F_SCOPE = "scope";

    /** Field of {@code ScopeLogs}. Proto: {@code log_records}. */
    private static final String F_LOG_RECORDS = "logRecords";

    // --- LogRecord fields (opentelemetry/proto/logs/v1/logs.proto) -----------

    /** Proto field 1: {@code time_unix_nano}. Nanoseconds since Unix epoch, decimal string. */
    private static final String F_TIME_UNIX_NANO = "timeUnixNano";

    /** Proto field 11: {@code observed_time_unix_nano}. Nanoseconds since Unix epoch, decimal string. */
    private static final String F_OBSERVED_TIME_UNIX_NANO = "observedTimeUnixNano";

    /** Proto field 2: {@code severity_number}. Integer 0–24. */
    private static final String F_SEVERITY_NUMBER = "severityNumber";

    /** Proto field 3: {@code severity_text}. Free-form string; SHOULD match the canonical
     *  short name of {@link SeverityNumber}. */
    private static final String F_SEVERITY_TEXT = "severityText";

    /** Proto field 5: {@code body}. Encoded as an AnyValue JSON object. */
    private static final String F_BODY = "body";

    /** Proto field 6 (LogRecord) / field 1 (Resource) / field 1 (InstrumentationScope):
     *  {@code attributes}. Array of {@code {"key":...,"value":...}} objects. */
    private static final String F_ATTRIBUTES = "attributes";

    /** Proto field 7 (LogRecord) / field 2 (Resource) / field 4 (InstrumentationScope):
     *  {@code dropped_attributes_count}. Omitted when zero. */
    private static final String F_DROPPED_ATTRIBUTES_COUNT = "droppedAttributesCount";

    /** Proto field 8: {@code flags}. W3C TraceFlags byte value (0x00–0xFF). Omitted when zero. */
    private static final String F_FLAGS = "flags";

    /** Proto field 9: {@code trace_id}. 32 lowercase hex characters. */
    private static final String F_TRACE_ID = "traceId";

    /** Proto field 10: {@code span_id}. 16 lowercase hex characters. */
    private static final String F_SPAN_ID = "spanId";

    /** Proto field 20: {@code event_name}. Identifies the class/type of event. */
    private static final String F_EVENT_NAME = "eventName";

    // --- InstrumentationScope fields (opentelemetry/proto/common/v1/common.proto) ---

    /** Proto field 1: {@code name}. Name of the instrumentation scope (e.g. library name). */
    private static final String F_NAME = "name";

    /** Proto field 2: {@code version}. Version of the instrumentation scope. */
    private static final String F_VERSION = "version";

    // --- KeyValue fields (opentelemetry/proto/common/v1/common.proto) ---------

    /** Proto field 1 of {@code KeyValue}: {@code key}. */
    private static final String F_KEY = "key";

    /** Proto field 2 of {@code KeyValue}: {@code value}. AnyValue JSON object. */
    private static final String F_VALUE = "value";

    // --- AnyValue type-wrapper fields (opentelemetry/proto/common/v1/common.proto) ---

    /** AnyValue oneof field: {@code string_value}. */
    private static final String F_STRING_VALUE = "stringValue";

    /** AnyValue oneof field: {@code bool_value}. */
    private static final String F_BOOL_VALUE = "boolValue";

    /** AnyValue oneof field: {@code int_value}. Always a decimal string per OTLP JSON spec. */
    private static final String F_INT_VALUE = "intValue";

    /** AnyValue oneof field: {@code double_value}. */
    private static final String F_DOUBLE_VALUE = "doubleValue";

    /** AnyValue oneof field: {@code array_value}. Wraps an {@code ArrayValue} object. */
    private static final String F_ARRAY_VALUE = "arrayValue";

    /** AnyValue oneof field: {@code kvlist_value}. Wraps a {@code KeyValueList} object. */
    private static final String F_KVLIST_VALUE = "kvlistValue";

    /** AnyValue oneof field: {@code bytes_value}. Base64-encoded string. */
    private static final String F_BYTES_VALUE = "bytesValue";

    /** Field of {@code ArrayValue} and {@code KeyValueList}: {@code values}. */
    private static final String F_VALUES = "values";

    // -------------------------------------------------------------------------

    @Nonnull
    @Override
    public String formatRecord(@Nonnull final LogRecord logRecord) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        first = appendTimeNanos(sb, first, F_TIME_UNIX_NANO,           logRecord.getTimestamp());
        first = appendTimeNanos(sb, first, F_OBSERVED_TIME_UNIX_NANO, logRecord.getObservedTimestamp());
        first = appendString   (sb, first, F_TRACE_ID,                logRecord.getTraceId());
        first = appendString   (sb, first, F_SPAN_ID,                 logRecord.getSpanId());
        if (logRecord.getTraceFlags() != 0) {
            first = appendInt  (sb, first, F_FLAGS,                   logRecord.getTraceFlags());
        }
        first = appendString   (sb, first, F_SEVERITY_TEXT,           logRecord.getSeverityText());
        SeverityNumber sn = logRecord.getSeverityNumber();
        if (sn != null && sn != SeverityNumber.UNSPECIFIED) {
            first = appendInt  (sb, first, F_SEVERITY_NUMBER,         sn.getValue());
        }
        first = appendBody     (sb, first,                            logRecord.getBody());
        first = appendKeyValueArray(sb, first, F_ATTRIBUTES,          logRecord.getAttributes());
        if (logRecord.getDroppedAttributesCount() != 0) {
            first = appendInt  (sb, first, F_DROPPED_ATTRIBUTES_COUNT, logRecord.getDroppedAttributesCount());
        }
              appendString     (sb, first, F_EVENT_NAME,              logRecord.getEventName());
        sb.append('}');
        return sb.toString();
    }

    @Nonnull
    @Override
    public String format(@Nonnull final LogRecord logRecord) {
        StringBuilder sb = new StringBuilder("{\"").append(F_RESOURCE_LOGS).append("\":[{");
        appendResourceBlock(sb, logRecord.getResource());
        sb.append(",\"").append(F_SCOPE_LOGS).append("\":[{");
        appendScopeBlock(sb, logRecord.getInstrumentationScope());
        sb.append(",\"").append(F_LOG_RECORDS).append("\":[");
        sb.append(formatRecord(logRecord));
        sb.append("]}]}]}");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // OTLP envelope blocks
    // -------------------------------------------------------------------------

    /**
     * Emits {@code "resource":{...}} at the current position, then optionally
     * {@code ,"schemaUrl":"..."} at the {@code ResourceLogs} level (per OTLP proto layout).
     */
    private static void appendResourceBlock(final StringBuilder sb, final Resource resource) {
        sb.append('"').append(F_RESOURCE).append("\":{\"").append(F_ATTRIBUTES).append("\":");
        if (resource != null) {
            appendKeyValueArrayInline(sb, resource.getAttributes());
            if (resource.getDroppedAttributesCount() != 0) {
                sb.append(",\"").append(F_DROPPED_ATTRIBUTES_COUNT).append("\":").append(resource.getDroppedAttributesCount());
            }
        } else {
            sb.append("[]");
        }
        sb.append('}');
        if (resource != null && resource.getSchemaUrl() != null) {
            sb.append(",\"").append(F_SCHEMA_URL).append("\":\"").append(escape(resource.getSchemaUrl())).append('"');
        }
    }

    /**
     * Emits {@code "scope":{...}} at the current position, then optionally
     * {@code ,"schemaUrl":"..."} at the {@code ScopeLogs} level (per OTLP proto layout).
     */
    private static void appendScopeBlock(final StringBuilder sb, final InstrumentationScope scope) {
        sb.append('"').append(F_SCOPE).append("\":{");
        if (scope != null) {
            boolean scopeFirst = true;
            if (scope.getName() != null) {
                sb.append('"').append(F_NAME).append("\":\"").append(escape(scope.getName())).append('"');
                scopeFirst = false;
            }
            if (scope.getVersion() != null) {
                if (!scopeFirst) sb.append(',');
                sb.append('"').append(F_VERSION).append("\":\"").append(escape(scope.getVersion())).append('"');
                scopeFirst = false;
            }
            if (!scope.getAttributes().isEmpty()) {
                if (!scopeFirst) sb.append(',');
                sb.append('"').append(F_ATTRIBUTES).append("\":");
                appendKeyValueArrayInline(sb, scope.getAttributes());
                scopeFirst = false;
            }
            if (scope.getDroppedAttributesCount() != 0) {
                if (!scopeFirst) sb.append(',');
                sb.append('"').append(F_DROPPED_ATTRIBUTES_COUNT).append("\":").append(scope.getDroppedAttributesCount());
            }
        }
        sb.append('}');
        if (scope != null && scope.getSchemaUrl() != null) {
            sb.append(",\"").append(F_SCHEMA_URL).append("\":\"").append(escape(scope.getSchemaUrl())).append('"');
        }
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
        sb.append('"').append(F_BODY).append("\":");
        appendAnyValue(sb, body);
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
            sb.append("{\"").append(F_KEY).append("\":\"").append(escape(kv.getKey()))
              .append("\",\"").append(F_VALUE).append("\":");
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
                sb.append("{\"").append(F_STRING_VALUE).append("\":\"").append(escape(value.asString())).append("\"}");
                break;
            case BOOL:
                sb.append("{\"").append(F_BOOL_VALUE).append("\":").append(value.asBoolean()).append('}');
                break;
            case INT:
                sb.append("{\"").append(F_INT_VALUE).append("\":\"").append(value.asLong()).append("\"}");
                break;
            case DOUBLE:
                sb.append("{\"").append(F_DOUBLE_VALUE).append("\":").append(value.asDouble()).append('}');
                break;
            case ARRAY:
                sb.append("{\"").append(F_ARRAY_VALUE).append("\":{\"").append(F_VALUES).append("\":[");
                boolean firstArr = true;
                for (AnyValue element : value.asArray()) {
                    if (!firstArr) sb.append(',');
                    appendAnyValue(sb, element);
                    firstArr = false;
                }
                sb.append("]}}");
                break;
            case KVLIST:
                sb.append("{\"").append(F_KVLIST_VALUE).append("\":{\"").append(F_VALUES).append("\":");
                appendKeyValueArrayInline(sb, value.asKvList());
                sb.append("}}");
                break;
            case BYTES:
                sb.append("{\"").append(F_BYTES_VALUE).append("\":\"")
                  .append(Base64.getEncoder().encodeToString(value.asBytes()))
                  .append("\"}");
                break;
            default:
                sb.append("{\"").append(F_STRING_VALUE).append("\":\"\"}");
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
