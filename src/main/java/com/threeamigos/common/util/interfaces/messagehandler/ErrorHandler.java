package com.threeamigos.common.util.interfaces.messagehandler;

import jakarta.annotation.Nonnull;

/**
 * An interface used to handle error messages.<br/>
 * If the message construction may be expensive, consider using a supplier instead
 * (see {@link ErrorSupplierHandler}).
 *
 * @author Stefano Reksten
 */
@FunctionalInterface
public interface ErrorHandler {

    /**
     * Handles a single error message.
     *
     * @param message a non-null error message to show to the user
     * @throws NullPointerException if {@code message} is {@code null}
     */
    void error(final @Nonnull String message);

}
