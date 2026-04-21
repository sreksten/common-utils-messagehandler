package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ExportLogsServiceRequestLogRecordFormatter;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.RawJsonRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LogRecordFormatterImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class ExportLogsServiceRequestLogRecordFormatterUnitTest {

    private static final Instant FIXED_TS = Instant.parse("2026-04-19T21:30:00.000000123Z");
    private static final Instant FIXED_OBSERVED_TS = Instant.parse("2026-04-19T21:30:01.000000456Z");
    private static final String TRACE_ID = "5b8efff798038103d269b633813fc60c";
    private static final String SPAN_ID = "eee19b7ec3c1b174";

    private final ExportLogsServiceRequestLogRecordFormatter formatter = new ExportLogsServiceRequestLogRecordFormatter();
    private final RawJsonRecordFormatter rawFormatter = new RawJsonRecordFormatter();

    @Test
    @DisplayName("format() should reject null log records")
    void formatRecordShouldRejectNull() {
        assertThrows(NullPointerException.class, () -> rawFormatter.format(null));
    }

    @Test
    @DisplayName("format() should reject null log records")
    void formatShouldRejectNull() {
        assertThrows(NullPointerException.class, () -> formatter.format(null));
    }

    @Test
    @DisplayName("format() should serialize eventName when it is the first emitted field")
    void formatRecordShouldSerializeEventNameAsFirstField() {
        StubLogRecord record = new StubLogRecord();
        record.timestamp = null;
        record.eventName = "evt";

        assertEquals("{\"eventName\":\"evt\"}", rawFormatter.format(record));
    }

    @Test
    @DisplayName("format() should omit resource and scope when absent")
    void formatShouldOmitResourceAndScopeWhenAbsent() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(FIXED_TS);
        String result = formatter.format(record);

        assertTrue(result.startsWith("{\"resourceLogs\":[{"));
        assertFalse(result.contains("\"resource\":"));
        assertFalse(result.contains("\"scope\":"));
        assertTrue(result.contains("\"scopeLogs\":[{\"logRecords\":["));
    }

    @Test
    @DisplayName("format() should serialize empty resource and scope objects when present")
    void formatShouldSerializeEmptyResourceAndScopeObjectsWhenPresent() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(FIXED_TS);
        record.setResource(new ResourceImpl());
        record.setInstrumentationScope(new InstrumentationScopeImpl());

        String result = formatter.format(record);
        assertTrue(result.contains("\"resource\":{}"));
        assertTrue(result.contains("\"scope\":{}"));
    }

    @Test
    @DisplayName("format() should serialize resource droppedAttributesCount without leading comma when attributes are absent")
    void formatShouldSerializeResourceDroppedCountWithoutLeadingComma() {
        ResourceImpl resource = new ResourceImpl();
        resource.setDroppedAttributesCount(5);

        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(FIXED_TS);
        record.setResource(resource);

        String result = formatter.format(record);
        assertTrue(result.contains("\"resource\":{\"droppedAttributesCount\":5}"));
        assertFalse(result.contains("\"resource\":{,\"droppedAttributesCount\""));
    }

    @Test
    @DisplayName("format() should serialize scope version without leading comma when name is absent")
    void formatShouldSerializeScopeVersionWithoutLeadingComma() {
        InstrumentationScopeImpl scope = new InstrumentationScopeImpl();
        scope.setVersion("2.1.0");

        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(FIXED_TS);
        record.setInstrumentationScope(scope);

        String result = formatter.format(record);
        assertTrue(result.contains("\"scope\":{\"version\":\"2.1.0\"}"));
        assertFalse(result.contains("\"scope\":{,\"version\""));
    }

    @Test
    @DisplayName("format() should serialize scope attributes without leading comma when name/version are absent")
    void formatShouldSerializeScopeAttributesWithoutLeadingComma() {
        InstrumentationScopeImpl scope = new InstrumentationScopeImpl();
        scope.setAttributes(Collections.singletonList(new KeyValueImpl("k", AnyValueImpl.ofString("v"))));

        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(FIXED_TS);
        record.setInstrumentationScope(scope);

        String result = formatter.format(record);
        assertTrue(result.contains("\"scope\":{\"attributes\":[{\"key\":\"k\""));
        assertFalse(result.contains("\"scope\":{,\"attributes\""));
    }

    @Test
    @DisplayName("format() should serialize scope droppedAttributesCount without leading comma when scope has no other fields")
    void formatShouldSerializeScopeDroppedCountWithoutLeadingComma() {
        InstrumentationScopeImpl scope = new InstrumentationScopeImpl();
        scope.setDroppedAttributesCount(4);

        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(FIXED_TS);
        record.setInstrumentationScope(scope);

        String result = formatter.format(record);
        assertTrue(result.contains("\"scope\":{\"droppedAttributesCount\":4}"));
        assertFalse(result.contains("\"scope\":{,\"droppedAttributesCount\""));
    }

    @Test
    @DisplayName("format() should serialize resource/scope blocks with schema URLs at parent level")
    void formatShouldSerializeResourceAndScopeWithSchemaUrls() {
        ResourceImpl resource = new ResourceImpl();
        resource.setAttributes(Collections.singletonList(new KeyValueImpl("service.name", AnyValueImpl.ofString("svc"))));
        resource.setDroppedAttributesCount(1);
        resource.setSchemaUrl("https://opentelemetry.io/schemas/1.26.0");

        InstrumentationScopeImpl scope = new InstrumentationScopeImpl();
        scope.setName("com.example.lib");
        scope.setVersion("1.0.0");
        scope.setAttributes(Collections.singletonList(new KeyValueImpl("scope.attr", AnyValueImpl.ofString("x"))));
        scope.setDroppedAttributesCount(2);
        scope.setSchemaUrl("https://opentelemetry.io/schemas/1.27.0");

        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(FIXED_TS);
        record.setResource(resource);
        record.setInstrumentationScope(scope);

        String result = formatter.format(record);
        assertTrue(result.contains("\"resource\":{\"attributes\":[{\"key\":\"service.name\""));
        assertTrue(result.contains("\"droppedAttributesCount\":1}"));
        assertTrue(result.contains("},\"schemaUrl\":\"https://opentelemetry.io/schemas/1.26.0\",\"scopeLogs\""));
        assertTrue(result.contains("\"scope\":{\"name\":\"com.example.lib\",\"version\":\"1.0.0\",\"attributes\":[{\"key\":\"scope.attr\""));
        assertTrue(result.contains("\"droppedAttributesCount\":2}"));
        assertTrue(result.contains("},\"schemaUrl\":\"https://opentelemetry.io/schemas/1.27.0\",\"logRecords\""));
        assertFalse(result.contains("\"resource\":{\"schemaUrl\""));
        assertFalse(result.contains("\"scope\":{\"schemaUrl\""));
    }

    @Test
    @DisplayName("format() should serialize scalar fields and omit default-valued fields")
    void formatRecordShouldSerializeScalarFieldsAndOmitDefaults() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(FIXED_TS);
        record.setObservedTimestamp(FIXED_OBSERVED_TS);
        record.setTraceId(TRACE_ID);
        record.setSpanId(SPAN_ID);
        record.setTraceFlags(1);
        record.setSeverityText("NOTICE");
        record.setSeverityNumber(SeverityNumber.INFO2);
        record.setAttributes(Arrays.asList(
                new KeyValueImpl("a", AnyValueImpl.ofString("x")),
                new KeyValueImpl("b", AnyValueImpl.ofLong(7))
        ));
        record.setDroppedAttributesCount(3);
        record.setEventName("evt");

        String result = rawFormatter.format(record);
        assertTrue(result.contains("\"timeUnixNano\":\"1776634200000000123\""));
        assertTrue(result.contains("\"observedTimeUnixNano\":\"1776634201000000456\""));
        assertTrue(result.contains("\"traceId\":\"" + TRACE_ID + "\""));
        assertTrue(result.contains("\"spanId\":\"" + SPAN_ID + "\""));
        assertTrue(result.contains("\"flags\":1"));
        assertTrue(result.contains("\"severityText\":\"NOTICE\""));
        assertTrue(result.contains("\"severityNumber\":10"));
        assertTrue(result.contains("\"attributes\":[{\"key\":\"a\""));
        assertTrue(result.contains("{\"key\":\"b\",\"value\":{\"intValue\":\"7\"}}"));
        assertTrue(result.contains("\"droppedAttributesCount\":3"));
        assertTrue(result.contains("\"eventName\":\"evt\""));
    }

    @Test
    @DisplayName("format() should handle null severityNumber and null attributes")
    void formatRecordShouldHandleNullSeverityNumberAndNullAttributes() {
        StubLogRecord record = new StubLogRecord();
        record.timestamp = FIXED_TS;
        record.severityNumber = null;
        record.severityText = null;
        record.attributes = null;

        String result = rawFormatter.format(record);
        assertTrue(result.contains("\"timeUnixNano\":\"1776634200000000123\""));
        assertFalse(result.contains("\"severityNumber\""));
        assertFalse(result.contains("\"severityText\""));
        assertFalse(result.contains("\"attributes\""));
    }

    @Test
    @DisplayName("format() should encode EMPTY AnyValue as empty object")
    void formatRecordShouldEncodeEmptyAnyValue() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(FIXED_TS);
        record.setBody(AnyValueImpl.empty());
        assertTrue(rawFormatter.format(record).contains("\"body\":{}"));
    }

    @Test
    @DisplayName("format() should encode primitive and complex AnyValue types")
    void formatRecordShouldEncodeAllAnyValueTypes() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(FIXED_TS);
        record.setBody(AnyValueImpl.ofArray(Arrays.asList(
                AnyValueImpl.ofString("s"),
                AnyValueImpl.ofBoolean(true),
                AnyValueImpl.ofLong(9),
                AnyValueImpl.ofDouble(1.25),
                AnyValueImpl.ofBytes(new byte[] {1, 2, 3}),
                AnyValueImpl.ofKvList(Arrays.asList(
                        new KeyValueImpl("k1", AnyValueImpl.ofString("v1")),
                        new KeyValueImpl("k2", AnyValueImpl.ofLong(2))
                ))
        )));

        String result = rawFormatter.format(record);
        assertTrue(result.contains("\"stringValue\":\"s\""));
        assertTrue(result.contains("\"boolValue\":true"));
        assertTrue(result.contains("\"intValue\":\"9\""));
        assertTrue(result.contains("\"doubleValue\":1.25"));
        assertTrue(result.contains("\"bytesValue\":\"AQID\""));
        assertTrue(result.contains("\"kvlistValue\":{\"values\":[{\"key\":\"k1\""));
    }

    @Test
    @DisplayName("format() should encode finite and special double values per proto3 JSON")
    void formatRecordShouldEncodeSpecialDoubleValues() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(FIXED_TS);

        record.setBody(AnyValueImpl.ofDouble(Double.NaN));
        assertTrue(rawFormatter.format(record).contains("\"doubleValue\":\"NaN\""));

        record.setBody(AnyValueImpl.ofDouble(Double.POSITIVE_INFINITY));
        assertTrue(rawFormatter.format(record).contains("\"doubleValue\":\"Infinity\""));

        record.setBody(AnyValueImpl.ofDouble(Double.NEGATIVE_INFINITY));
        assertTrue(rawFormatter.format(record).contains("\"doubleValue\":\"-Infinity\""));
    }

    @Test
    @DisplayName("format() should handle empty array/kvlist AnyValue values")
    void formatRecordShouldHandleEmptyArrayAndKvList() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(FIXED_TS);

        record.setBody(AnyValueImpl.ofArray(Collections.emptyList()));
        assertTrue(rawFormatter.format(record).contains("\"arrayValue\":{\"values\":[]}"));

        record.setBody(AnyValueImpl.ofKvList(Collections.emptyList()));
        assertTrue(rawFormatter.format(record).contains("\"kvlistValue\":{\"values\":[]}"));
    }

    @Test
    @DisplayName("format() should JSON-escape strings, keys and control characters")
    void formatRecordShouldEscapeStringsAndControlCharacters() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(FIXED_TS);
        record.setBody(AnyValueImpl.ofString("q\" b\\ n\n r\r t\t c\u0001"));
        record.setAttributes(Collections.singletonList(new KeyValueImpl("k\"\\\n", AnyValueImpl.ofString("v\"\\\u0002"))));

        String result = rawFormatter.format(record);
        assertTrue(result.contains("q\\\" b\\\\ n\\n r\\r t\\t c\\u0001"));
        assertTrue(result.contains("\"key\":\"k\\\"\\\\\\n\""));
        assertTrue(result.contains("\"stringValue\":\"v\\\"\\\\\\u0002\""));
    }

    @Test
    @DisplayName("format() should reject negative timestamps")
    void formatRecordShouldRejectNegativeTimestamps() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(Instant.parse("1969-12-31T23:59:59.999999999Z"));
        assertThrows(IllegalArgumentException.class, () -> rawFormatter.format(record));
    }

    @Test
    @DisplayName("format() should reject timestamps that overflow uint64 nanoseconds")
    void formatRecordShouldRejectTimestampsBeyondUint64() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(Instant.ofEpochSecond(18_446_744_074L));
        assertThrows(IllegalArgumentException.class, () -> rawFormatter.format(record));
    }

    @Test
    @DisplayName("format() should fail fast when AnyValue type is null")
    void formatRecordShouldFailFastOnNullAnyValueType() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(FIXED_TS);
        record.setBody(new AnyValue() {
            @Override
            public Type getType() {
                return null;
            }

            @Override
            public String asString() {
                return null;
            }

            @Override
            public boolean asBoolean() {
                return false;
            }

            @Override
            public long asLong() {
                return 0;
            }

            @Override
            public double asDouble() {
                return 0;
            }

            @Override
            public List<AnyValue> asArray() {
                return Collections.emptyList();
            }

            @Override
            public List<KeyValue> asKvList() {
                return Collections.emptyList();
            }

            @Override
            public byte[] asBytes() {
                return new byte[0];
            }
        });

        assertThrows(IllegalArgumentException.class, () -> rawFormatter.format(record));
    }

    @Test
    @DisplayName("format() should allow maximum uint64 nanoseconds timestamp")
    void formatRecordShouldAllowMaxUint64NanosTimestamp() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(Instant.ofEpochSecond(18_446_744_073L, 709_551_615));
        String result = rawFormatter.format(record);
        assertTrue(result.contains("\"timeUnixNano\":\"18446744073709551615\""));
    }

    private static final class StubLogRecord implements LogRecord {
        private Instant timestamp = FIXED_TS;
        private Instant observedTimestamp;
        private String traceId;
        private String spanId;
        private int traceFlags;
        private String severityText;
        private SeverityNumber severityNumber = SeverityNumber.UNSPECIFIED;
        private AnyValue body;
        private Resource resource;
        private InstrumentationScope instrumentationScope;
        private List<KeyValue> attributes = Collections.emptyList();
        private int droppedAttributesCount;
        private String eventName;

        @Override
        public Instant getTimestamp() {
            return timestamp;
        }

        @Override
        public Instant getObservedTimestamp() {
            return observedTimestamp;
        }

        @Override
        public String getTraceId() {
            return traceId;
        }

        @Override
        public String getSpanId() {
            return spanId;
        }

        @Override
        public int getTraceFlags() {
            return traceFlags;
        }

        @Override
        public String getSeverityText() {
            return severityText;
        }

        @Override
        public SeverityNumber getSeverityNumber() {
            return severityNumber;
        }

        @Override
        public AnyValue getBody() {
            return body;
        }

        @Override
        public Resource getResource() {
            return resource;
        }

        @Override
        public InstrumentationScope getInstrumentationScope() {
            return instrumentationScope;
        }

        @Override
        public List<KeyValue> getAttributes() {
            return attributes;
        }

        @Override
        public int getDroppedAttributesCount() {
            return droppedAttributesCount;
        }

        @Override
        public String getEventName() {
            return eventName;
        }
    }
}
