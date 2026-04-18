package com.threeamigos.common.util.interfaces.messagehandler;

import jakarta.annotation.Nonnull;

/**
 * An interface used to handle warning messages.<br/>
 * If the message construction may be expensive, consider using a supplier instead
 * (see {@link WarnSupplierHandler}).
 *
 * @author Stefano Reksten
 */
@FunctionalInterface
public interface WarnWithContextHandler {

    /**
     * Handles a single warning message.
     *
     * @param message     a non-null warning message to show to the user
     * @param contextInfo a non-null context in which the warning message was generated
     * @throws NullPointerException if either {@code message} or {@code contextInfo} is {@code null}
     */
    void warn(final @Nonnull String message, final @Nonnull ContextInfo contextInfo);

}
