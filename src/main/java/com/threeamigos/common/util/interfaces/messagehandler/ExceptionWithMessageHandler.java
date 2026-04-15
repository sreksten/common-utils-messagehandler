package com.threeamigos.common.util.interfaces.messagehandler;

import jakarta.annotation.Nonnull;

/**
 * Contract for handling an exception together with a contextual message.
 *
 * @author Stefano Reksten
 */
@FunctionalInterface
public interface ExceptionWithMessageHandler {

    /**
     * Handles a single exception with a contextual message.
     *
     * @param message contextual message associated with the exception
     * @param exception an exception to handle
     */
    void handleException(final @Nonnull String message, final @Nonnull Exception exception);

}
