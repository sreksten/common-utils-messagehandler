package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Handler span-start unit tests")
@Tag("unit")
@Tag("messageHandler")
class HandlerSpanStartUnitTest {

    @Test
    @DisplayName("standalone handlers should throw localized IllegalStateException on startSpan")
    void standaloneHandlersShouldThrowLocalizedIllegalStateExceptionOnStartSpan() {
        MessageHandler handler = new ConsoleMessageHandler();

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> handler.startSpan("op"));
        assertEquals(
                MessageHandlerResourceBundle.get("cannotStartSpanNoTracerBound"),
                exception.getMessage());
    }

    @Test
    @DisplayName("tracer-created handlers should start spans and caller should end them")
    void tracerCreatedHandlersShouldStartSpansAndCallerShouldEndThem() {
        TracerProvider provider = TracerProvider.createProvider();
        MessageHandler handler = provider.getTracer("orders-service", "1.0.0").getInMemoryMessageHandler();

        Span span = handler.startSpan("handler-span");

        assertNotNull(span);
        assertTrue(span.isRecording());
        span.end();
        assertFalse(span.isRecording());
    }

    @Test
    @DisplayName("package-private tracer getter should return null standalone and non-null when tracer-bound")
    void packagePrivateTracerGetterShouldReturnNullStandaloneAndNonNullWhenTracerBound() {
        InMemoryMessageHandler standalone = new InMemoryMessageHandler();
        InMemoryMessageHandler tracerCreated = TracerProvider.createProvider()
                .getTracer("inventory-service", "1.0.0")
                .getInMemoryMessageHandler();

        assertNull(standalone.getBoundTracerForTests());
        assertNotNull(tracerCreated.getBoundTracerForTests());
    }
}
