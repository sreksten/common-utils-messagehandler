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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
