package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("HandlerTracerBinder unit tests")
@Tag("unit")
@Tag("messageHandler")
class HandlerTracerBinderUnitTest extends AbstractOtelValidatorLogTrapUnitTest {

    @Test
    @DisplayName("bindTracer should return handler unchanged when target is not AbstractMessageHandler")
    void bindTracerShouldReturnHandlerUnchangedWhenTargetIsNotAbstractMessageHandler() {
        MessageHandler nonAbstractHandler = mock(MessageHandler.class);
        Tracer tracer = mock(Tracer.class);

        MessageHandler bound = HandlerTracerBinder.bindTracer(nonAbstractHandler, tracer);

        assertSame(nonAbstractHandler, bound);
    }

    @Test
    @DisplayName("bindTracer should no-op when tracer is null")
    void bindTracerShouldNoOpWhenTracerIsNull() {
        InMemoryMessageHandler handler = new InMemoryMessageHandler();

        HandlerTracerBinder.bindTracer(handler, null);

        assertThrows(IllegalStateException.class, () -> handler.startSpan("no-tracer"));
    }

    @Test
    @DisplayName("bindTracer should inject tracer and delegate startSpan")
    void bindTracerShouldInjectTracerAndDelegateStartSpan() {
        InMemoryMessageHandler handler = new InMemoryMessageHandler();
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        when(tracer.createSpan("bound-span")).thenReturn(span);

        HandlerTracerBinder.bindTracer(handler, tracer);

        assertSame(span, handler.startSpan("bound-span"));
    }

}
