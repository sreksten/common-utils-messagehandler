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
public interface FatalHandler {

    /**
     * Handles a single fatal message.
     *
     * @param message a non-null fatal message to show to the user
     * @throws NullPointerException if {@code message} is {@code null}
     */
    void fatal(final @Nonnull String message);

}
