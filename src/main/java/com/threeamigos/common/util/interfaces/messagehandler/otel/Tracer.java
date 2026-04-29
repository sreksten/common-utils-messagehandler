package com.threeamigos.common.util.interfaces.messagehandler.otel;

/**
 *
 * @author Stefano Reksten
 */
public interface Tracer {

    Span createSpan(String name);
}
