package com.threeamigos.common.util.interfaces.messagehandler;

import jakarta.annotation.Nonnull;

/**
 * An interface used to handle debug messages.<br/>
 * If the message construction may be expensive, consider using a supplier instead
 * (see {@link DebugSupplierHandler}).
 *
 * @author Stefano Reksten
 */
@FunctionalInterface
public interface DebugWithContextHandler {

    /**
     * Handles a single debug message.
     *
     * @param message a non-null debug message to show to the programmer
     * @param contextInfo  a non-null context in which the debug message was generated
     * @throws NullPointerException if either {@code message} or {@code contextInfo} is {@code null}
     */
    void debug(final @Nonnull String message, final @Nonnull ContextInfo contextInfo);

}
