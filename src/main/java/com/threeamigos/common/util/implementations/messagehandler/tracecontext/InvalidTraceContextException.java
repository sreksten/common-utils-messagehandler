package com.threeamigos.common.util.implementations.messagehandler.tracecontext;

/**
 * Checked exception thrown when a trace context value violates W3C Trace Context requirements.
 *
 * @author Stefano Reksten
 */
public final class InvalidTraceContextException extends Exception {

    public InvalidTraceContextException(final String message) {
        super(message);
    }
}
