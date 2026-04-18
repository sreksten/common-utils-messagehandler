package com.threeamigos.common.util.interfaces.messagehandler;


import jakarta.annotation.Nonnull;

/**
 * An interface used to handle trace messages.<br/>
 * If the message construction may be expensive, consider using a supplier instead
 * (see {@link TraceSupplierHandler}).
 *
 * @author Stefano Reksten
 */
@FunctionalInterface
public interface TraceHandler {

    /**
     * Handles a single trace message.
     *
     * @param message a non-null trace message to show to the programmer
     * @throws NullPointerException if {@code message} is {@code null}
     */
    void trace(final @Nonnull String message);

}
