package com.threeamigos.common.util.implementations.messagehandler.utils;

import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanData;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OTelCollectorSpanDispatcher unit tests")
@Tag("unit")
@Tag("messageHandler")
class OTelCollectorSpanDispatcherUnitTest {

    @Test
    @DisplayName("should fan out one span to all delegates")
    void shouldFanOutOneSpanToAllDelegates() throws Exception {
        AtomicInteger first = new AtomicInteger(0);
        AtomicInteger second = new AtomicInteger(0);
        SpanDispatcher d1 = spanData -> first.incrementAndGet();
        SpanDispatcher d2 = spanData -> second.incrementAndGet();
        OTelCollectorSpanDispatcher collector = new OTelCollectorSpanDispatcher(Arrays.asList(d1, d2));

        collector.dispatchSpan(fakeSpanData());

        assertEquals(1, first.get());
        assertEquals(1, second.get());
    }

    @Test
    @DisplayName("should aggregate IOException from multiple delegates")
    void shouldAggregateIoExceptionFromMultipleDelegates() {
        SpanDispatcher ok = spanData -> {
        };
        SpanDispatcher failA = spanData -> {
            throw new IOException("A");
        };
        SpanDispatcher failB = spanData -> {
            throw new IOException("B");
        };
        OTelCollectorSpanDispatcher collector = new OTelCollectorSpanDispatcher(Arrays.asList(ok, failA, failB));

        IOException ex = assertThrows(IOException.class, () -> collector.dispatchSpan(fakeSpanData()));
        assertEquals(2, ex.getSuppressed().length);
    }

    @Test
    @DisplayName("delegate management APIs should handle nulls, duplicates and snapshots")
    void delegateManagementApisShouldHandleNullsDuplicatesAndSnapshots() throws Exception {
        SpanDispatcher first = spanData -> {
        };
        SpanDispatcher second = spanData -> {
        };
        OTelCollectorSpanDispatcher collector = new OTelCollectorSpanDispatcher();

        collector.addDispatcher(null);
        assertEquals(0, collector.snapshotDelegates().size());

        collector.addDispatcher(first);
        collector.addDispatcher(first);
        assertEquals(1, collector.snapshotDelegates().size());

        collector.setDelegates(null);
        assertEquals(0, collector.snapshotDelegates().size());
        collector.setDelegates(Collections.<SpanDispatcher>emptyList());
        assertEquals(0, collector.snapshotDelegates().size());

        collector.setDelegates(Arrays.asList(first, null, first, second));
        List<SpanDispatcher> snapshot = collector.snapshotDelegates();
        assertEquals(2, snapshot.size());
        assertTrue(snapshot.contains(first));
        assertTrue(snapshot.contains(second));

        snapshot.clear();
        assertEquals(2, collector.snapshotDelegates().size(), "snapshot must be a detached copy");

        collector.setDelegates(Collections.<SpanDispatcher>emptyList());
        collector.dispatchSpan(fakeSpanData());
    }

    private static SpanData fakeSpanData() {
        return new SpanData() {
            @Override
            public String getName() {
                return "s";
            }

            @Override
            public com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext getSpanContext() {
                return new com.threeamigos.common.util.implementations.messagehandler.otel.SpanContextImpl(
                        "5b8efff798038103d269b633813fc60c",
                        "eee19b7ec3c1b174",
                        (byte) 1,
                        false,
                        new com.threeamigos.common.util.implementations.messagehandler.otel.TraceStateImpl());
            }

            @Override
            public String getParentSpanId() {
                return null;
            }

            @Override
            public com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope getInstrumentationScope() {
                return null;
            }

            @Override
            public Instant getStartTimestamp() {
                return Instant.now();
            }

            @Override
            public Instant getEndTimestamp() {
                return Instant.now();
            }

            @Override
            public com.threeamigos.common.util.interfaces.messagehandler.otel.StatusCode getStatusCode() {
                return com.threeamigos.common.util.interfaces.messagehandler.otel.StatusCode.UNSET;
            }

            @Override
            public String getStatusDescription() {
                return "";
            }

            @Override
            public java.util.List<com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue> getAttributes() {
                return Collections.emptyList();
            }

            @Override
            public java.util.List<com.threeamigos.common.util.interfaces.messagehandler.otel.Event> getEvents() {
                return Collections.emptyList();
            }

            @Override
            public java.util.List<com.threeamigos.common.util.interfaces.messagehandler.otel.Link> getLinks() {
                return Collections.emptyList();
            }
        };
    }
}
