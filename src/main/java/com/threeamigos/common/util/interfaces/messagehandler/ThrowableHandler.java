package com.threeamigos.common.util.interfaces.messagehandler;

import jakarta.annotation.Nonnull;

/**
 * Contract for handling a Throwable without additional context.
 * <p>
 * Use {@link ThrowableWithMessageHandler} instead when the call site can supply a
 * contextual message that is not present in the Throwable itself.
 *
 * @author Stefano Reksten
 * @see ThrowableWithMessageHandler
 */
@FunctionalInterface
public interface ThrowableHandler {

    /**
     * Handles a single Throwable.
     * <p>
     * Implementations based on {@code AbstractMessageHandler} treat
     * {@code null} values as a no-op and return immediately.
     *
     * @param throwable the non-null Throwable to handle
     */
    void exception(final @Nonnull Throwable throwable);

}
