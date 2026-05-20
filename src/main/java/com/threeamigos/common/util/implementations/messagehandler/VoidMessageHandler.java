package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;

/**
 *
 * @author Stefano Reksten
 */
public class VoidMessageHandler extends AbstractMessageHandler {

    @Override
    public void handleMessage(@Nonnull SeverityNumber level, @Nonnull String message) {
    }

    @Override
    protected void handleExceptionInternal(final @Nonnull String message, final @Nonnull Throwable throwable) {
    }
}
