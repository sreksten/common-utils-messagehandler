package com.threeamigos.common.util.interfaces.messagehandler;

import jakarta.annotation.Nonnull;

/**
 * An interface used to handle fatal messages.<br/>
 * If the message construction may be expensive, consider using a supplier instead
 * (see {@link FatalSupplierHandler}).
 *
 * @author Stefano Reksten
 */
@FunctionalInterface
public interface FatalWithContextHandler {

    /**
     * Handles a single fatal message.
     *
     * @param message a non-null fatal message to show to the user
     * @param contextInfo  a non-null context in which the fatal message was generated
     * @throws NullPointerException if either {@code message}, its production, or {@code contextInfo} is {@code null}
     */
    void fatal(final @Nonnull String message, final @Nonnull ContextInfo contextInfo);

}
