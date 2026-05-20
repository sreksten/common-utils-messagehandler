package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.AbstractMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;

import java.lang.reflect.Field;

/**
 * Internal utility that binds tracer instances to handlers without exposing tracer mutators
 * on the public handler API.
 */
final class HandlerTracerBinder {

    private static final Field TRACER_FIELD = resolveTracerField(AbstractMessageHandler.class);

    private HandlerTracerBinder() {
    }

    static <T extends MessageHandler> T bindTracer(final T handler, final Tracer tracer) {
        if (!(handler instanceof AbstractMessageHandler) || tracer == null) {
            return handler;
        }
        try {
            // Intentional reflective injection: tracer stays private inside AbstractMessageHandler.
            TRACER_FIELD.set(handler, tracer);
        } catch (IllegalAccessException e) {
            OpenTelemetryAttributeValidator.report("Unable to bind tracer to handler.", e);
        }
        return handler;
    }

    static Field resolveTracerField(final Class<?> targetClass) {
        try {
            Field field = targetClass.getDeclaredField("tracer");
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            OpenTelemetryAttributeValidator.report("Unable to resolve handler tracer binding field.", e);
            return null;
        }
    }
}
