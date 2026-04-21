package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ConsoleLogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("ConcoleLogRecordFormatter unit tests")
@Tag("unit")
@Tag("messageHandler")
class ConsoleLogRecordFormatterUnitTest {

    private final ConsoleLogRecordFormatter formatter = new ConsoleLogRecordFormatter();

    @Test
    @DisplayName("format() should reject null log records")
    void formatShouldRejectNullLogRecord() {
        assertThrows(NullPointerException.class, () -> formatter.format(null));
    }

    @Test
    @DisplayName("format() should produce ISO timestamp, 6-char severity and message")
    void formatShouldProduceExpectedLayout() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(Instant.parse("2026-04-21T08:30:00Z"));
        record.setSeverityText("INFO");
        record.setBody(AnyValueImpl.ofString("hello"));

        String result = formatter.format(record);

        assertEquals("2026-04-21T08:30:00Z [INFO  ] hello", result);
    }

    @Test
    @DisplayName("format() should truncate long severity names to 6 characters")
    void formatShouldTruncateLongSeverityNames() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(Instant.parse("2026-04-21T08:30:00Z"));
        record.setSeverityText("UNSPECIFIED");
        record.setBody(AnyValueImpl.ofString("hello"));

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
        record.setBody(AnyValueImpl.ofString("hello"));

        String result = formatter.format(record);

        assertEquals("2026-04-21T08:30:00Z [WARN2 ] hello", result);
    }

    @Test
    @DisplayName("format() should emit InstrumentationScope name after severity")
    void formatShouldEmitInstrumentationScopeNameAfterSeverity() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(Instant.parse("2026-04-21T08:30:00Z"));
        record.setSeverityText("INFO");
        record.setBody(AnyValueImpl.ofString("hello"));
        InstrumentationScopeImpl scope = new InstrumentationScopeImpl();
        scope.setName("com.example.Foo");
        record.setInstrumentationScope(scope);

        String result = formatter.format(record);

        assertEquals("2026-04-21T08:30:00Z [INFO  ] [com.example.Foo] hello", result);
    }

    @Test
    @DisplayName("format() should reduce InstrumentationScope class name when configured")
    void formatShouldReduceInstrumentationScopeClassNameWhenConfigured() {
        ConsoleLogRecordFormatter reducedFormatter = new ConsoleLogRecordFormatter(true);
        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(Instant.parse("2026-04-21T08:30:00Z"));
        record.setSeverityText("INFO");
        record.setBody(AnyValueImpl.ofString("hello"));
        InstrumentationScopeImpl scope = new InstrumentationScopeImpl();
        scope.setName("com.example.Foo");
        record.setInstrumentationScope(scope);

        String result = reducedFormatter.format(record);

        assertEquals("2026-04-21T08:30:00Z [INFO  ] [c.e.Foo] hello", result);
    }
}
