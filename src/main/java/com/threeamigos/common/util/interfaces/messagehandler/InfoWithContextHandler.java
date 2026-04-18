package com.threeamigos.common.util.interfaces.messagehandler;

import jakarta.annotation.Nonnull;

/**
 * An interface used to handle information messages.<br/>
 * If the message construction may be expensive, consider using a supplier instead
 * (see {@link InfoSupplierHandler}).
 *
 * @author Stefano Reksten
 */
@FunctionalInterface
public interface InfoWithContextHandler {

    /**
     * Handles a single information message.
     *
     * @param message a non-null info message to show to the user
     * @param contextInfo a non-null context in which the info message was generated
     * @throws NullPointerException if either {@code message} or {@code contextInfo} is {@code null}
     */
    void info(final @Nonnull String message, final @Nonnull ContextInfo contextInfo);

}
