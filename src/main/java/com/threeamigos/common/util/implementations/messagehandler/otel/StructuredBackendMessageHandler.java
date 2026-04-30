package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.AbstractMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;

/**
 * Bridges a structured LogRecord pipeline to message-only backend handlers.
 *
 * @author Stefano Reksten
 */
final class StructuredBackendMessageHandler extends AbstractMessageHandler {

    private final MessageHandler backend;
    private final LogRecordFactory logRecordFactory;
    private final LogRecordFormatter logRecordFormatter;

    StructuredBackendMessageHandler(final MessageHandler backend,
                                    final LogRecordFactory logRecordFactory,
                                    final LogRecordFormatter logRecordFormatter) {
        this.backend = backend;
        this.logRecordFactory = logRecordFactory;
        this.logRecordFormatter = logRecordFormatter;
    }

    @Override
    public void handleMessage(final @Nonnull SeverityNumber level, final @Nonnull String message) {
        LogRecord logRecord = logRecordFactory.create(level, message);
        backend.handleMessage(level, logRecordFormatter.format(logRecord));
    }

    @Override
    public void handleThrowable(final @Nonnull String message, final @Nonnull Throwable throwable) {
        LogRecord logRecord = logRecordFactory.create(message, throwable);
        String formatted = logRecordFormatter.format(logRecord);
        backend.handleThrowable(formatted, throwable);
    }

    @Override
    public void close() {
        backend.close();
    }
}
