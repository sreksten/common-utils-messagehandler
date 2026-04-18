package com.threeamigos.common.util.interfaces.messagehandler;

import jakarta.annotation.Nonnull;

/**
 * Contract for handling an exception, together with contextual information about where it occurred.
 * <p>
 * Use {@link ExceptionWithMessageAndContextHandler} instead when the call site can also supply a
 * descriptive message that is not present in the exception itself.
 *
 * @author Stefano Reksten
 * @see ExceptionWithMessageAndContextHandler
 */
@FunctionalInterface
public interface ExceptionWithContextHandler {

    /**
     * Handles a single exception.
     *
     * @param exception   a non-null exception to handle
     * @param contextInfo a non-null context in which the exception was generated
     * @throws NullPointerException if either {@code exception} or {@code contextInfo} is {@code null}
     */
    void exception(final @Nonnull Exception exception, final @Nonnull ContextInfo contextInfo);

}
