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
public interface DebugHandler {

    /**
     * Handles a single debug message.
     *
     * @param message a non-null debug message to show to the programmer
     * @throws NullPointerException if {@code message} is {@code null}
     */
    void debug(final @Nonnull String message);

}
