package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.AbstractMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;

/**
 * Internal utility that binds tracer instances to handlers.
 */
public final class HandlerTracerBinder {

    private HandlerTracerBinder() {
    }

    static <T extends MessageHandler> T bindTracer(final T handler, final Tracer tracer) {
        if (!(handler instanceof AbstractMessageHandler) || tracer == null) {
            return handler;
        }
        ((AbstractMessageHandler) handler).bindTracer(tracer);
        return handler;
    }
}
