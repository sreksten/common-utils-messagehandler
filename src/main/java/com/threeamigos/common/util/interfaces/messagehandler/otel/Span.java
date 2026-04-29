package com.threeamigos.common.util.interfaces.messagehandler.otel;

/**
 *
 * @author Stefano Reksten
 */
public interface Span {

    void end();

    void addAttribute(String key, AnyValue value);

    void addEvent(Event event);

    void addLink(Link link);

}
