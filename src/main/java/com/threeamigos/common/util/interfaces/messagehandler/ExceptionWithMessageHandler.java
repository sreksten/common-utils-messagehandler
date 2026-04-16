package com.threeamigos.common.util.interfaces.messagehandler;

import jakarta.annotation.Nonnull;

/**
 * Contract for handling an exception together with a caller-supplied contextual message.
 * <p>
 * Use this interface instead of {@link ExceptionHandler} when the call site can provide
 * additional context that is not present in the exception itself — for example, the operation
 * that was being attempted, the resource that could not be opened, or any other information
 * that makes the error easier to diagnose.
 * <p>
 * Implementations are expected to present both pieces of information together. The conventional
 * format is {@code "<message>: <exception detail>"}, where the exception detail is
 * {@link Exception#getMessage()} when non-null, or {@link Exception#toString()} as a fallback.
 *
 * @author Stefano Reksten
 * @see ExceptionHandler
 */
@FunctionalInterface
public interface ExceptionWithMessageHandler {

    /**
     * Handles an exception together with a caller-supplied contextual message.
     *
     * @param message   a non-null string describing the context in which the exception occurred
     *                  (e.g., the operation attempted or the resource involved)
     * @param exception the non-null exception to handle
     */
    void handleException(final @Nonnull String message, final @Nonnull Exception exception);

}
