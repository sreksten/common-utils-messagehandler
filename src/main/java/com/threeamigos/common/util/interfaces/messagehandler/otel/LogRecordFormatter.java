package com.threeamigos.common.util.interfaces.messagehandler.otel;

import jakarta.annotation.Nonnull;

/**
 * Formats a log record into a JSON string.
 * @author Stefano Reksten
 */
public interface LogRecordFormatter {

    @Nonnull String format(@Nonnull LogRecord logRecord);

}
