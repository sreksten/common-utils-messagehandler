package com.threeamigos.common.util.interfaces.messagehandler;

import jakarta.annotation.Nonnull;

/**
 * Contract for handling a Throwable together with a caller-supplied contextual message.
 * <p>
 * Use this interface instead of {@link ThrowableHandler} when the call site can provide
 * additional context that is not present in the Throwable itself — for example, the operation
 * that was being attempted, the resource that could not be opened, or any other information
 * that makes the problem easier to diagnose.
 * <p>
 * Implementations are expected to present both pieces of information together. The conventional
 * format is {@code "<message>: <throwable detail>"}, where the detail is
 * {@link Throwable#getMessage()} when non-null, or {@link Throwable#toString()} as a fallback.
 *
 * @author Stefano Reksten
 * @see ThrowableHandler
 */
@FunctionalInterface
public interface ThrowableWithMessageHandler {

    /**
     * Handles a Throwable together with a caller-supplied contextual message.
     *
     * @param message   a non-null string describing the context in which the Throwable occurred
     *                  (e.g., the operation attempted or the resource involved)
     * @param throwable a non-null Throwable to handle
     * @throws NullPointerException if either {@code message} or {@code throwable} is {@code null}
     */
    void exception(final @Nonnull String message, final @Nonnull Throwable throwable);

}
