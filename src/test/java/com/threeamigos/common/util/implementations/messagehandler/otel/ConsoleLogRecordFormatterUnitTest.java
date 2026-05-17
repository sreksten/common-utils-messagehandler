package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ConsoleLogRecordFormatter;
import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ConsoleLogRecordFormatter unit tests")
@Tag("unit")
@Tag("messageHandler")
class ConsoleLogRecordFormatterUnitTest {

    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";
    private static final String SPAN_ID = "89abcdef01234567";

    private final ConsoleLogRecordFormatter formatter = new ConsoleLogRecordFormatter();

    @BeforeEach
    void forceStrictValidatorMode() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(false);
        OpenTelemetryAttributeValidator.setLogTrapForTests(null);
    }

    @AfterEach
    void resetValidatorMode() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(false);
        OpenTelemetryAttributeValidator.setLogTrapForTests(null);
    }

    @Test
    @DisplayName("format() should reject null log records")
    void formatShouldRejectNullLogRecord() {
        assertThrows(IllegalArgumentException.class, () -> formatter.format(null));
    }

    @Test
    @DisplayName("format() should produce ISO timestamp, 6-char severity and message")
    void formatShouldProduceExpectedLayout() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(Instant.parse("2026-04-21T08:30:00Z"));
        record.setSeverityText("INFO");
        record.setBody(AnyValueFactory.ofString("hello"));

        String result = formatter.format(record);

        assertEquals("2026-04-21T08:30:00Z [INFO  ] hello", result);
    }

    @Test
    @DisplayName("format() should truncate long severity names to 6 characters")
    void formatShouldTruncateLongSeverityNames() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(Instant.parse("2026-04-21T08:30:00Z"));
        record.setSeverityText("UNSPECIFIED");
        record.setBody(AnyValueFactory.ofString("hello"));

        String result = formatter.format(record);

        assertEquals("2026-04-21T08:30:00Z [UNSPEC] hello", result);
    }

    @Test
    @DisplayName("format() should fallback to severityNumber when severityText is blank")
    void formatShouldFallbackToSeverityNumberWhenSeverityTextBlank() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(Instant.parse("2026-04-21T08:30:00Z"));
        record.setSeverityText("   ");
        record.setSeverityNumber(SeverityNumber.WARN2);
        record.setBody(AnyValueFactory.ofString("hello"));

        String result = formatter.format(record);

        assertEquals("2026-04-21T08:30:00Z [WARN2 ] hello", result);
    }

    @Test
    @DisplayName("format() should emit InstrumentationScope name after severity")
    void formatShouldEmitInstrumentationScopeNameAfterSeverity() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(Instant.parse("2026-04-21T08:30:00Z"));
        record.setSeverityText("INFO");
        record.setBody(AnyValueFactory.ofString("hello"));
        InstrumentationScope scope = InstrumentationScopeFactory.create(
                "com.example.Foo", "1.0.0", null, null);
        record.setInstrumentationScope(scope);

        String result = formatter.format(record);

        assertEquals("2026-04-21T08:30:00Z [INFO  ] [com.example.Foo] hello", result);
    }

    @Test
    @DisplayName("format() should emit trace_id, span_id and trace_flags immediately after severity")
    void formatShouldEmitTraceIdAndSpanIdImmediatelyAfterSeverity() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(Instant.parse("2026-04-21T08:30:00Z"));
        record.setSeverityText("INFO");
        record.setTraceId(TRACE_ID);
        record.setSpanId(SPAN_ID);
        record.setBody(AnyValueFactory.ofString("hello"));

        String result = formatter.format(record);

        assertEquals(
                "2026-04-21T08:30:00Z [INFO  ] [trace_id=" + TRACE_ID + " span_id=" + SPAN_ID + " trace_flags=00] hello",
                result);
    }

    @Test
    @DisplayName("format() should render trace_flags as two lowercase hex digits")
    void formatShouldRenderTraceFlagsAsTwoLowercaseHexDigits() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(Instant.parse("2026-04-21T08:30:00Z"));
        record.setSeverityText("INFO");
        record.setTraceId(TRACE_ID);
        record.setSpanId(SPAN_ID);
        record.setTraceFlags(255);
        record.setBody(AnyValueFactory.ofString("hello"));

        String result = formatter.format(record);

        assertEquals(
                "2026-04-21T08:30:00Z [INFO  ] [trace_id=" + TRACE_ID + " span_id=" + SPAN_ID + " trace_flags=ff] hello",
                result);
    }

    @Test
    @DisplayName("format() should keep trace/span token before instrumentation scope")
    void formatShouldKeepTraceSpanTokenBeforeInstrumentationScope() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(Instant.parse("2026-04-21T08:30:00Z"));
        record.setSeverityText("INFO");
        record.setTraceId(TRACE_ID);
        record.setSpanId(SPAN_ID);
        record.setBody(AnyValueFactory.ofString("hello"));
        InstrumentationScope scope = InstrumentationScopeFactory.create(
                "com.example.Foo", "1.0.0", null, null);
        record.setInstrumentationScope(scope);

        String result = formatter.format(record);

        assertEquals(
                "2026-04-21T08:30:00Z [INFO  ] [trace_id=" + TRACE_ID + " span_id=" + SPAN_ID + " trace_flags=00] [com.example.Foo] hello",
                result);
    }

    @Test
    @DisplayName("format() should reduce InstrumentationScope class name when configured")
    void formatShouldReduceInstrumentationScopeClassNameWhenConfigured() {
        ConsoleLogRecordFormatter reducedFormatter = new ConsoleLogRecordFormatter(true);
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(Instant.parse("2026-04-21T08:30:00Z"));
        record.setSeverityText("INFO");
        record.setBody(AnyValueFactory.ofString("hello"));
        InstrumentationScope scope = InstrumentationScopeFactory.create(
                "com.example.Foo", "1.0.0", null, null);
        record.setInstrumentationScope(scope);

        String result = reducedFormatter.format(record);

        assertEquals("2026-04-21T08:30:00Z [INFO  ] [c.e.Foo] hello", result);
    }

    @Test
    @DisplayName("constructor and setter should control reduceScopeClassName flag")
    void constructorAndSetterShouldControlReductionFlag() {
        ConsoleLogRecordFormatter defaultFormatter = new ConsoleLogRecordFormatter();
        assertFalse(defaultFormatter.isReduceScopeClassName());

        defaultFormatter.setReduceScopeClassName(true);
        assertTrue(defaultFormatter.isReduceScopeClassName());

        ConsoleLogRecordFormatter configuredFormatter = new ConsoleLogRecordFormatter(true);
        assertTrue(configuredFormatter.isReduceScopeClassName());
    }

    @Test
    @DisplayName("format() should ignore instrumentation scope when name is null or blank")
    void formatShouldIgnoreInstrumentationScopeWhenNameNullOrBlank() {
        LogRecordImpl withNullName = new LogRecordImpl();
        withNullName.setTimestamp(Instant.parse("2026-04-21T08:30:00Z"));
        withNullName.setSeverityText("INFO");
        withNullName.setBody(AnyValueFactory.ofString("hello"));
        withNullName.setInstrumentationScope(new InstrumentationScope() {
            @Override
            public String getName() {
                return null;
            }

            @Override
            public String getVersion() {
                return null;
            }

            @Override
            public String getSchemaUrl() {
                return null;
            }

            @Override
            public List<KeyValue> getAttributes() {
                return Collections.emptyList();
            }

            @Override
            public int getDroppedAttributesCount() {
                return 0;
            }
        });

        String nullNameResult = formatter.format(withNullName);
        assertEquals("2026-04-21T08:30:00Z [INFO  ] hello", nullNameResult);

        LogRecordImpl withBlankName = new LogRecordImpl();
        withBlankName.setTimestamp(Instant.parse("2026-04-21T08:30:00Z"));
        withBlankName.setSeverityText("INFO");
        withBlankName.setBody(AnyValueFactory.ofString("hello"));
        InstrumentationScope blankScope = new InstrumentationScope() {
            @Override
            public String getName() {
                return "   ";
            }

            @Override
            public String getVersion() {
                return null;
            }

            @Override
            public String getSchemaUrl() {
                return null;
            }

            @Override
            public List<KeyValue> getAttributes() {
                return Collections.emptyList();
            }

            @Override
            public int getDroppedAttributesCount() {
                return 0;
            }
        };
        withBlankName.setInstrumentationScope(blankScope);

        String blankNameResult = formatter.format(withBlankName);
        assertEquals("2026-04-21T08:30:00Z [INFO  ] hello", blankNameResult);
    }

    @Test
    @DisplayName("format() should use eventName when body is absent and empty when both are absent")
    void formatShouldUseEventNameFallbacks() {
        LogRecordImpl withEventName = new LogRecordImpl();
        withEventName.setTimestamp(Instant.parse("2026-04-21T08:30:00Z"));
        withEventName.setSeverityText("INFO");
        withEventName.setBody(null);
        withEventName.setEventName("evt");
        assertEquals("2026-04-21T08:30:00Z [INFO  ] evt", formatter.format(withEventName));

        LogRecordImpl withNoBodyNoEvent = new LogRecordImpl();
        withNoBodyNoEvent.setTimestamp(Instant.parse("2026-04-21T08:30:00Z"));
        withNoBodyNoEvent.setSeverityText("INFO");
        withNoBodyNoEvent.setBody(null);
        withNoBodyNoEvent.setEventName(null);
        assertEquals("2026-04-21T08:30:00Z [INFO  ] ", formatter.format(withNoBodyNoEvent));
    }

    @Test
    @DisplayName("format() should append exception stacktrace attribute when present")
    void formatShouldAppendExceptionStacktraceWhenPresent() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(Instant.parse("2026-04-21T08:30:00Z"));
        record.setSeverityText("ERROR");
        record.setBody(AnyValueFactory.ofString("Failure while processing checkout"));
        record.setAttributes(Collections.singletonList(
                new KeyValueImpl(OTelTags.EXCEPTION_STACKTRACE.getValue(), AnyValueFactory.ofString("stack-line-1\nstack-line-2"))));

        String result = formatter.format(record);

        assertTrue(result.contains("Failure while processing checkout"));
        assertTrue(result.contains("stack-line-1"));
        assertTrue(result.contains("stack-line-2"));
    }

    @Test
    @DisplayName("format() should fallback to UNSPECIFIED when severity text is null/blank")
    void formatShouldFallbackToUnspecifiedWhenSeverityMissing() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(Instant.parse("2026-04-21T08:30:00Z"));
        record.setSeverityText(null);
        record.setSeverityNumber(SeverityNumber.UNSPECIFIED);
        record.setBody(AnyValueFactory.ofString("hello"));

        assertEquals("2026-04-21T08:30:00Z [UNSPEC] hello", formatter.format(record));
    }

    @Test
    @DisplayName("format() should render non-string AnyValue body types")
    void formatShouldRenderNonStringAnyValueBodyTypes() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(Instant.parse("2026-04-21T08:30:00Z"));
        record.setSeverityText("INFO");

        record.setBody(AnyValueFactory.ofBoolean(true));
        assertEquals("2026-04-21T08:30:00Z [INFO  ] true", formatter.format(record));

        record.setBody(AnyValueFactory.ofLong(7));
        assertEquals("2026-04-21T08:30:00Z [INFO  ] 7", formatter.format(record));

        record.setBody(AnyValueFactory.ofDouble(1.5));
        assertEquals("2026-04-21T08:30:00Z [INFO  ] 1.5", formatter.format(record));

        record.setBody(AnyValueFactory.ofBytes(new byte[]{1, 2, 3}));
        assertEquals("2026-04-21T08:30:00Z [INFO  ] AQID", formatter.format(record));

        record.setBody(AnyValueFactory.ofArray(Collections.singletonList(AnyValueFactory.ofString("x"))));
        assertTrue(formatter.format(record).startsWith("2026-04-21T08:30:00Z [INFO  ] ["));

        record.setBody(AnyValueFactory.ofKvList(Collections.singletonList(new KeyValueImpl("k", AnyValueFactory.ofString("v")))));
        assertTrue(formatter.format(record).startsWith("2026-04-21T08:30:00Z [INFO  ] ["));

        record.setBody(AnyValueFactory.empty());
        assertEquals("2026-04-21T08:30:00Z [INFO  ] ", formatter.format(record));
    }

    @Test
    @DisplayName("format() should handle string AnyValue with null payload and reject null AnyValue type")
    void formatShouldHandleStringNullPayloadAndRejectNullType() {
        LogRecordImpl withNullString = new LogRecordImpl();
        withNullString.setTimestamp(Instant.parse("2026-04-21T08:30:00Z"));
        withNullString.setSeverityText("INFO");
        withNullString.setBody(new AnyValue() {
            @Override
            public Type getType() { return Type.STRING; }
            @Override
            public String asString() { return null; }
            @Override
            public boolean asBoolean() { return false; }
            @Override
            public long asLong() { return 0; }
            @Override
            public double asDouble() { return 0; }
            @Override
            public List<AnyValue> asArray() { return Collections.emptyList(); }
            @Override
            public List<KeyValue> asKvList() { return Collections.emptyList(); }
            @Override
            public byte[] asBytes() { return new byte[0]; }
        });
        assertEquals("2026-04-21T08:30:00Z [INFO  ] ", formatter.format(withNullString));

        LogRecordImpl withNullType = new LogRecordImpl();
        withNullType.setTimestamp(Instant.parse("2026-04-21T08:30:00Z"));
        withNullType.setSeverityText("INFO");
        withNullType.setBody(new AnyValue() {
            @Override
            public Type getType() { return null; }
            @Override
            public String asString() { return null; }
            @Override
            public boolean asBoolean() { return false; }
            @Override
            public long asLong() { return 0; }
            @Override
            public double asDouble() { return 0; }
            @Override
            public List<AnyValue> asArray() { return Collections.emptyList(); }
            @Override
            public List<KeyValue> asKvList() { return Collections.emptyList(); }
            @Override
            public byte[] asBytes() { return new byte[0]; }
        });

        assertThrows(IllegalArgumentException.class, () -> formatter.format(withNullType));
    }

    @Test
    @DisplayName("format() should reject null timestamp")
    void formatShouldRejectNullTimestamp() {
        LogRecord record = new LogRecord() {
            @Override
            public Instant getTimestamp() { return null; }
            @Override
            public Instant getObservedTimestamp() { return null; }
            @Override
            public String getTraceId() { return null; }
            @Override
            public String getSpanId() { return null; }
            @Override
            public int getTraceFlags() { return 0; }
            @Override
            public String getSeverityText() { return "INFO"; }
            @Override
            public SeverityNumber getSeverityNumber() { return SeverityNumber.INFO; }
            @Override
            public AnyValue getBody() { return AnyValueFactory.ofString("hello"); }
            @Override
            public com.threeamigos.common.util.interfaces.messagehandler.otel.Resource getResource() { return null; }
            @Override
            public InstrumentationScope getInstrumentationScope() { return null; }
            @Override
            public List<KeyValue> getAttributes() { return Collections.emptyList(); }
            @Override
            public String getEventName() { return null; }
        };

        assertThrows(IllegalArgumentException.class, () -> formatter.format(record));
    }

    @Test
    @DisplayName("format() should support null severityNumber via custom LogRecord implementation")
    void formatShouldSupportNullSeverityNumberViaCustomLogRecord() {
        LogRecord record = new LogRecord() {
            @Override
            public Instant getTimestamp() { return Instant.parse("2026-04-21T08:30:00Z"); }
            @Override
            public Instant getObservedTimestamp() { return null; }
            @Override
            public String getTraceId() { return null; }
            @Override
            public String getSpanId() { return null; }
            @Override
            public int getTraceFlags() { return 0; }
            @Override
            public String getSeverityText() { return "   "; }
            @Override
            public SeverityNumber getSeverityNumber() { return null; }
            @Override
            public AnyValue getBody() { return AnyValueFactory.ofString("hello"); }
            @Override
            public com.threeamigos.common.util.interfaces.messagehandler.otel.Resource getResource() { return null; }
            @Override
            public InstrumentationScope getInstrumentationScope() { return null; }
            @Override
            public List<KeyValue> getAttributes() { return Collections.emptyList(); }
            @Override
            public String getEventName() { return null; }
        };

        assertEquals("2026-04-21T08:30:00Z [UNSPEC] hello", formatter.format(record));
    }
}
