package com.threeamigos.common.util.implementations.messagehandler;

/**
 * Typed signal emitted when a dispatch call is attempted on a closed handler.
 * <p>
 * Using a dedicated exception type avoids control-flow checks based on localized message text.
 */
final class HandlerClosedException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    HandlerClosedException() {
        super(MessageHandlerResourceBundle.getOrDefault("handlerIsClosed", "Handler is closed"));
    }
}

