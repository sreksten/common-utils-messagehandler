package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanData;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("TracerProvider span dispatcher unit tests")
@Tag("unit")
@Tag("messageHandler")
class TracerProviderSpanDispatcherUnitTest {

    @Test
    @DisplayName("provider default span dispatcher should receive ended spans")
    void providerDefaultSpanDispatcherShouldReceiveEndedSpans() {
        TracerProvider provider = TracerProvider.createProvider();
        AtomicReference<SpanData> captured = new AtomicReference<SpanData>();
        provider.setDefaultSpanDispatcher(captured::set);

        Tracer tracer = provider.getTracer("orders", "1.0.0");
        Span span = tracer.createSpan("checkout");
        span.end();

        SpanData snapshot = captured.get();
        assertNotNull(snapshot);
        assertEquals("checkout", snapshot.getName());
        assertEquals("orders", snapshot.getInstrumentationScope().getName());
    }
}
