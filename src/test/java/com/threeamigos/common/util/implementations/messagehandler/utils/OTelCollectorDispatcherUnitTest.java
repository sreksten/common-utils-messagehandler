package com.threeamigos.common.util.implementations.messagehandler.utils;

import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordDispatcher;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordFactoryImpl;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OTelCollectorDispatcher unit tests")
@Tag("unit")
@Tag("messageHandler")
class OTelCollectorDispatcherUnitTest {

    @Test
    @DisplayName("should fan out one record to all delegates")
    void shouldFanOutOneRecordToAllDelegates() throws Exception {
        AtomicInteger first = new AtomicInteger(0);
        AtomicInteger second = new AtomicInteger(0);
        LogRecordDispatcher d1 = (logRecord, formatter) -> first.incrementAndGet();
        LogRecordDispatcher d2 = (logRecord, formatter) -> second.incrementAndGet();
        OTelCollectorDispatcher collector = new OTelCollectorDispatcher(Arrays.asList(d1, d2));

        LogRecord logRecord = new LogRecordFactoryImpl().create(SeverityNumber.INFO, "hello");
        LogRecordFormatter formatter = lr -> "{}";
        collector.dispatchLogRecord(logRecord, formatter);

        assertEquals(1, first.get());
        assertEquals(1, second.get());
    }

    @Test
    @DisplayName("should aggregate IOException from multiple delegates")
    void shouldAggregateIoExceptionFromMultipleDelegates() {
        LogRecordDispatcher ok = (logRecord, formatter) -> {
        };
        LogRecordDispatcher failA = (logRecord, formatter) -> {
            throw new IOException("A");
        };
        LogRecordDispatcher failB = (logRecord, formatter) -> {
            throw new IOException("B");
        };
        OTelCollectorDispatcher collector = new OTelCollectorDispatcher(Arrays.asList(ok, failA, failB));

        LogRecord logRecord = new LogRecordFactoryImpl().create(SeverityNumber.INFO, "hello");
        LogRecordFormatter formatter = lr -> "{}";
        IOException ex = assertThrows(IOException.class, () -> collector.dispatchLogRecord(logRecord, formatter));
        assertEquals(2, ex.getSuppressed().length);
    }

    @Test
    @DisplayName("delegate management APIs should handle nulls, duplicates and snapshots")
    void delegateManagementApisShouldHandleNullsDuplicatesAndSnapshots() throws Exception {
        LogRecordDispatcher first = (logRecord, formatter) -> {
        };
        LogRecordDispatcher second = (logRecord, formatter) -> {
        };

        OTelCollectorDispatcher collector = new OTelCollectorDispatcher();

        collector.addDispatcher(null);
        assertEquals(0, collector.snapshotDelegates().size());

        collector.addDispatcher(first);
        collector.addDispatcher(first);
        assertEquals(1, collector.snapshotDelegates().size());

        collector.removeDispatcher(null);
        collector.removeDispatcher(second);
        assertEquals(1, collector.snapshotDelegates().size());

        collector.removeDispatcher(first);
        assertEquals(0, collector.snapshotDelegates().size());

        collector.setDelegates(null);
        assertEquals(0, collector.snapshotDelegates().size());
        collector.setDelegates(Collections.<LogRecordDispatcher>emptyList());
        assertEquals(0, collector.snapshotDelegates().size());

        collector.setDelegates(Arrays.asList(first, null, first, second));
        List<LogRecordDispatcher> snapshot = collector.snapshotDelegates();
        assertEquals(2, snapshot.size());
        assertTrue(snapshot.contains(first));
        assertTrue(snapshot.contains(second));

        snapshot.clear();
        assertEquals(2, collector.snapshotDelegates().size(), "snapshot must be a detached copy");

        LogRecord logRecord = new LogRecordFactoryImpl().create(SeverityNumber.INFO, "x");
        collector.setDelegates(Collections.<LogRecordDispatcher>emptyList());
        collector.dispatchLogRecord(logRecord, lr -> "{}");
    }
}
