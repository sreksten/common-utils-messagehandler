package com.threeamigos.common.util.interfaces.messagehandler;

import jakarta.annotation.Nonnull;

/**
 * Contract for handling an exception without additional context.
 * <p>
 * Use {@link ExceptionWithMessageHandler} instead when the call site can supply a
 * contextual message that is not present in the exception itself.
 *
 * @author Stefano Reksten
 * @see ExceptionWithMessageHandler
 */
@FunctionalInterface
public interface ExceptionHandler {

    /**
     * Handles a single exception.
     *
     * @param exception the non-null exception to handle
     */
    void handleException(final @Nonnull Exception exception);

}
