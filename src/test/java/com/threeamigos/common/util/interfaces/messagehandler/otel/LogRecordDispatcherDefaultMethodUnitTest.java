package com.threeamigos.common.util.interfaces.messagehandler.otel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LogRecordDispatcher default method unit tests")
@Tag("unit")
@Tag("messageHandler")
class LogRecordDispatcherDefaultMethodUnitTest {

    /**
     * Minimal implementation that does NOT override dispatchLogRecords, so the default
     * interface method is exercised.
     */
    private static final class CollectingDispatcher implements LogRecordDispatcher {
        final List<LogRecord> dispatched = new ArrayList<LogRecord>();

        @Override
        public void dispatchLogRecord(final LogRecord logRecord,
                                      final LogRecordFormatter logRecordFormatter) throws IOException {
            dispatched.add(logRecord);
        }
    }

    private static LogRecord stubRecord() {
        return new LogRecord() {
            @Override public java.time.Instant getTimestamp() { return null; }
            @Override public java.time.Instant getObservedTimestamp() { return null; }
            @Override public String getTraceId() { return null; }
            @Override public String getSpanId() { return null; }
            @Override public int getTraceFlags() { return 0; }
            @Override public String getSeverityText() { return null; }
            @Override public SeverityNumber getSeverityNumber() { return SeverityNumber.UNSPECIFIED; }
            @Override public AnyValue getBody() { return null; }
            @Override public Resource getResource() { return null; }
            @Override public InstrumentationScope getInstrumentationScope() { return null; }
            @Override public List<KeyValue> getAttributes() { return Collections.emptyList(); }
            @Override public String getEventName() { return null; }
        };
    }

    private static final LogRecordFormatter STUB_FORMATTER = record -> "{}";

    @Test
    @DisplayName("default dispatchLogRecords should throw NullPointerException for null logRecords")
    void defaultDispatchLogRecordsShouldThrowForNullLogRecords() {
        CollectingDispatcher dispatcher = new CollectingDispatcher();
        assertThrows(NullPointerException.class,
                () -> dispatcher.dispatchLogRecords(null, STUB_FORMATTER));
    }

    @Test
    @DisplayName("default dispatchLogRecords should throw NullPointerException for null formatter")
    void defaultDispatchLogRecordsShouldThrowForNullFormatter() {
        CollectingDispatcher dispatcher = new CollectingDispatcher();
        assertThrows(NullPointerException.class,
                () -> dispatcher.dispatchLogRecords(Collections.<LogRecord>emptyList(), null));
    }

    @Test
    @DisplayName("default dispatchLogRecords should return early for empty list")
    void defaultDispatchLogRecordsShouldReturnEarlyForEmptyList() throws IOException {
        CollectingDispatcher dispatcher = new CollectingDispatcher();
        dispatcher.dispatchLogRecords(Collections.<LogRecord>emptyList(), STUB_FORMATTER);
        assertTrue(dispatcher.dispatched.isEmpty());
    }

    @Test
    @DisplayName("default dispatchLogRecords should dispatch non-null records and skip nulls")
    void defaultDispatchLogRecordsShouldSkipNullRecords() throws IOException {
        CollectingDispatcher dispatcher = new CollectingDispatcher();
        LogRecord r1 = stubRecord();
        LogRecord r2 = stubRecord();
        dispatcher.dispatchLogRecords(Arrays.asList(r1, null, r2), STUB_FORMATTER);
        assertEquals(2, dispatcher.dispatched.size());
        assertEquals(r1, dispatcher.dispatched.get(0));
        assertEquals(r2, dispatcher.dispatched.get(1));
    }
}
