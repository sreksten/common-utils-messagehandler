package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.PlainTextLogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.ContextInfo;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PlainTextLogRecordFormatter unit tests")
@Tag("unit")
@Tag("messageHandler")
class PlainTextLogRecordFormatterUnitTest {

    private static final Instant FIXED_TS = Instant.parse("2026-04-20T08:30:00.123456789Z");

    private final PlainTextLogRecordFormatter formatter = new PlainTextLogRecordFormatter();

    @Test
    @DisplayName("format() should reject null log records")
    void formatRecordShouldRejectNull() {
        assertThrows(NullPointerException.class, () -> formatter.format(null));
    }

    @Test
    @DisplayName("format() should serialize plain-text records")
    void formatShouldSerializePlainTextRecord() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(FIXED_TS);
        record.setSeverityText("INFO");
        record.setBody(AnyValueImpl.ofString("hello"));

        String result = formatter.format(record);
        assertTrue(result.contains("[INFO ] hello"));
    }

    @Test
    @DisplayName("format() should mirror plain-text layout with class name and context")
    void formatRecordShouldMirrorPlainTextLayoutWithClassNameAndContext() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(FIXED_TS);
        record.setSeverityText("INFO");
        record.setBody(AnyValueImpl.ofString("hello"));
        record.setAttributes(Arrays.asList(
                new KeyValueImpl(ContextInfo.CLASS_NAME, AnyValueImpl.ofString("com.example.Foo")),
                new KeyValueImpl("userId", AnyValueImpl.ofLong(42))
        ));

        String expectedTs = ZonedDateTime.ofInstant(FIXED_TS, ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String expected = String.format("[%s] [com.example.Foo] [INFO ] hello {userId=42}", expectedTs);

        assertEquals(expected, formatter.format(record));
    }

    @Test
    @DisplayName("format() should not emit context suffix when CLASS_NAME is the only attribute")
    void formatRecordShouldNotEmitContextSuffixWhenOnlyClassName() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(FIXED_TS);
        record.setSeverityText("INFO");
        record.setBody(AnyValueImpl.ofString("hello"));
        record.setAttributes(Collections.singletonList(
                new KeyValueImpl(ContextInfo.CLASS_NAME, AnyValueImpl.ofString("com.example.Foo"))
        ));

        String result = formatter.format(record);
        assertTrue(result.contains("[com.example.Foo]"));
        assertFalse(result.contains("{"));
    }

    @Test
    @DisplayName("setAbbreviateClassName(true) should abbreviate className in output")
    void shouldAbbreviateClassNameWhenEnabled() {
        assertFalse(formatter.isAbbreviateClassName());
        formatter.setAbbreviateClassName(true);
        assertTrue(formatter.isAbbreviateClassName());

        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(FIXED_TS);
        record.setSeverityText("INFO");
        record.setBody(AnyValueImpl.ofString("hello"));
        record.setAttributes(Collections.singletonList(
                new KeyValueImpl(ContextInfo.CLASS_NAME, AnyValueImpl.ofString("com.example.Foo"))
        ));

        String result = formatter.format(record);
        assertTrue(result.contains("[c.e.Foo]"));
        assertFalse(result.contains("[com.example.Foo]"));
    }

    @Test
    @DisplayName("format() should fall back to severityNumber and eventName")
    void formatRecordShouldFallbackToSeverityNumberAndEventName() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(FIXED_TS);
        record.setSeverityText(null);
        record.setSeverityNumber(SeverityNumber.WARN2);
        record.setBody(AnyValueImpl.empty());
        record.setEventName("evt");

        String result = formatter.format(record);
        assertTrue(result.contains("[WARN2] evt"));
    }

    @Test
    @DisplayName("format() should use UNSPECIFIED when severityText and severityNumber are absent")
    void formatRecordShouldUseUnspecifiedWhenNoSeverityProvided() {
        StubLogRecord record = new StubLogRecord();
        record.timestamp = FIXED_TS;
        record.severityText = null;
        record.severityNumber = SeverityNumber.UNSPECIFIED;
        record.body = AnyValueImpl.ofString("msg");

        String result = formatter.format(record);
        assertTrue(result.contains("[UNSPECIFIED] msg"));
    }

    @Test
    @DisplayName("format() should use UNSPECIFIED when severityText is blank and severityNumber is null")
    void formatRecordShouldUseUnspecifiedWhenSeverityTextBlankAndSeverityNumberNull() {
        StubLogRecord record = new StubLogRecord();
        record.timestamp = FIXED_TS;
        record.severityText = "   ";
        record.severityNumber = null;
        record.body = AnyValueImpl.ofString("msg");

        String result = formatter.format(record);
        assertTrue(result.contains("[UNSPECIFIED] msg"));
    }

    @Test
    @DisplayName("format() should support null timestamp and null attributes")
    void formatRecordShouldSupportNullTimestampAndNullAttributes() {
        StubLogRecord record = new StubLogRecord();
        record.timestamp = null;
        record.attributes = null;
        record.severityText = "INFO";
        record.body = AnyValueImpl.ofString("msg");

        String result = formatter.format(record);
        assertTrue(result.matches("^\\[[^\\]]+\\] \\[INFO \\] msg$"));
    }

    @Test
    @DisplayName("format() should return empty message when body and eventName are both absent")
    void formatRecordShouldReturnEmptyMessageWhenBodyAndEventNameAreAbsent() {
        StubLogRecord record = new StubLogRecord();
        record.timestamp = FIXED_TS;
        record.severityText = "INFO";
        record.body = null;
        record.eventName = null;

        String result = formatter.format(record);
        assertTrue(result.endsWith("] "));
    }

    @Test
    @DisplayName("format() should stringify all AnyValue body variants")
    void formatRecordShouldStringifyAllAnyValueVariants() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(FIXED_TS);
        record.setSeverityText("INFO");

        record.setBody(AnyValueImpl.ofBoolean(true));
        assertTrue(formatter.format(record).contains("] true"));

        record.setBody(AnyValueImpl.ofLong(7));
        assertTrue(formatter.format(record).contains("] 7"));

        record.setBody(AnyValueImpl.ofDouble(1.5));
        assertTrue(formatter.format(record).contains("] 1.5"));

        record.setBody(AnyValueImpl.ofBytes(new byte[] {1, 2, 3}));
        assertTrue(formatter.format(record).contains("] AQID"));

        record.setBody(AnyValueImpl.ofArray(Arrays.asList(
                AnyValueImpl.ofString("x"),
                AnyValueImpl.ofLong(3)
        )));
        assertTrue(formatter.format(record).contains("] [x, 3]"));

        record.setBody(AnyValueImpl.ofKvList(Collections.singletonList(
                new KeyValueImpl("k", AnyValueImpl.ofString("v"))
        )));
        assertTrue(formatter.format(record).contains("] {k=v}"));
    }

    @Test
    @DisplayName("format() should support non-string CLASS_NAME attributes")
    void formatRecordShouldSupportNonStringClassNameAttributes() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(FIXED_TS);
        record.setSeverityText("INFO");
        record.setBody(AnyValueImpl.ofString("hello"));
        record.setAttributes(Collections.singletonList(
                new KeyValueImpl(ContextInfo.CLASS_NAME, AnyValueImpl.ofLong(7))
        ));

        String result = formatter.format(record);
        assertTrue(result.contains("[7]"));
    }

    @Test
    @DisplayName("format() should tolerate null attributes and non-class entries while extracting className/context")
    void formatRecordShouldTolerateNullAndNonClassAttributes() {
        StubLogRecord record = new StubLogRecord();
        record.timestamp = FIXED_TS;
        record.severityText = "INFO";
        record.body = AnyValueImpl.ofString("msg");
        record.attributes = Arrays.asList(
                null,
                new NullableKeyValue("other", AnyValueImpl.ofString("v")),
                new NullableKeyValue("nullable", null),
                new NullableKeyValue(ContextInfo.CLASS_NAME, null)
        );

        String result = formatter.format(record);
        assertFalse(result.contains("[" + ContextInfo.CLASS_NAME + "]"));
        assertTrue(result.contains("other=v"));
        assertTrue(result.contains("nullable=null"));
    }

    @Test
    @DisplayName("format() should handle context path where no entries are emitted after pre-scan")
    void formatRecordShouldHandleNoContextEntriesAfterPrescan() {
        StubLogRecord record = new StubLogRecord();
        record.timestamp = FIXED_TS;
        record.severityText = "INFO";
        record.body = AnyValueImpl.ofString("msg");
        record.attributes = Collections.<KeyValue>singletonList(new PreScanNoEntriesKeyValue());

        String result = formatter.format(record);
        assertFalse(result.contains("{"));
    }

    @Test
    @DisplayName("format() should reject AnyValue with null type")
    void formatRecordShouldRejectAnyValueWithNullType() {
        StubLogRecord record = new StubLogRecord();
        record.timestamp = FIXED_TS;
        record.severityText = "INFO";
        record.body = new NullTypeAnyValue();

        assertThrows(IllegalArgumentException.class, () -> formatter.format(record));
    }

    @Test
    @DisplayName("format() should handle custom ARRAY/KVLIST AnyValue with null list payloads")
    void formatRecordShouldHandleNullArrayAndKvListPayloads() {
        StubLogRecord arrayRecord = new StubLogRecord();
        arrayRecord.timestamp = FIXED_TS;
        arrayRecord.severityText = "INFO";
        arrayRecord.body = new NullArrayAnyValue();
        assertTrue(formatter.format(arrayRecord).contains("] []"));

        StubLogRecord kvRecord = new StubLogRecord();
        kvRecord.timestamp = FIXED_TS;
        kvRecord.severityText = "INFO";
        kvRecord.body = new NullKvListAnyValue();
        assertTrue(formatter.format(kvRecord).contains("] {}"));
    }

    @Test
    @DisplayName("format() should render null elements in custom ARRAY/KVLIST payloads")
    void formatRecordShouldRenderNullElementsInCustomArrayAndKvListPayloads() {
        StubLogRecord arrayRecord = new StubLogRecord();
        arrayRecord.timestamp = FIXED_TS;
        arrayRecord.severityText = "INFO";
        arrayRecord.body = new NullElementArrayAnyValue();
        assertTrue(formatter.format(arrayRecord).contains("] [null, x]"));

        StubLogRecord kvRecord = new StubLogRecord();
        kvRecord.timestamp = FIXED_TS;
        kvRecord.severityText = "INFO";
        kvRecord.body = new NullElementKvListAnyValue();
        String result = formatter.format(kvRecord);
        assertTrue(result.contains("null"));
        assertTrue(result.contains("k=null"));
    }

    @Test
    @DisplayName("abbreviate() should return unchanged value when no package segments exist")
    void abbreviateShouldReturnUnchangedValueWhenNoDot() {
        assertEquals("Foo", PlainTextLogRecordFormatter.abbreviate("Foo"));
    }

    private static final class StubLogRecord implements LogRecord {
        private Instant timestamp = FIXED_TS;
        private Instant observedTimestamp;
        private String traceId;
        private String spanId;
        private int traceFlags;
        private String severityText = "INFO";
        private SeverityNumber severityNumber = SeverityNumber.UNSPECIFIED;
        private AnyValue body = AnyValueImpl.ofString("msg");
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

    private static final class NullTypeAnyValue implements AnyValue {
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
            return null;
        }

        @Override
        public List<KeyValue> asKvList() {
            return null;
        }

        @Override
        public byte[] asBytes() {
            return new byte[0];
        }
    }

    private static final class NullArrayAnyValue implements AnyValue {
        @Override
        public Type getType() {
            return Type.ARRAY;
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
            return null;
        }

        @Override
        public List<KeyValue> asKvList() {
            return null;
        }

        @Override
        public byte[] asBytes() {
            return new byte[0];
        }
    }

    private static final class NullKvListAnyValue implements AnyValue {
        @Override
        public Type getType() {
            return Type.KVLIST;
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
            return null;
        }

        @Override
        public List<KeyValue> asKvList() {
            return null;
        }

        @Override
        public byte[] asBytes() {
            return new byte[0];
        }
    }

    private static final class NullElementArrayAnyValue implements AnyValue {
        @Override
        public Type getType() {
            return Type.ARRAY;
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
            return Arrays.<AnyValue>asList(null, AnyValueImpl.ofString("x"));
        }

        @Override
        public List<KeyValue> asKvList() {
            return null;
        }

        @Override
        public byte[] asBytes() {
            return new byte[0];
        }
    }

    private static final class NullElementKvListAnyValue implements AnyValue {
        @Override
        public Type getType() {
            return Type.KVLIST;
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
            return null;
        }

        @Override
        public List<KeyValue> asKvList() {
            return Arrays.<KeyValue>asList(null, new NullableKeyValue("k", null));
        }

        @Override
        public byte[] asBytes() {
            return new byte[0];
        }
    }

    private static final class NullableKeyValue implements KeyValue {
        private final String key;
        private final AnyValue value;

        private NullableKeyValue(final String key, final AnyValue value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public AnyValue getValue() {
            return value;
        }
    }

    private static final class PreScanNoEntriesKeyValue implements KeyValue {
        private int calls;

        @Override
        public String getKey() {
            calls++;
            if (calls == 1) {
                return ContextInfo.CLASS_NAME;
            }
            if (calls == 2) {
                return "other";
            }
            return ContextInfo.CLASS_NAME;
        }

        @Override
        public AnyValue getValue() {
            return AnyValueImpl.ofString("v");
        }
    }
}
