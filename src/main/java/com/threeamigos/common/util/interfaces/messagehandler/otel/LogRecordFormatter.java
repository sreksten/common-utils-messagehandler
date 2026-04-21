package com.threeamigos.common.util.interfaces.messagehandler.otel;

import jakarta.annotation.Nonnull;

/**
 * Formats a {@link LogRecord} into a string.
 * <p>
 * Implementations decide the output representation for {@link #format(LogRecord)},
 * e.g., a raw log-record JSON object or a full OTLP envelope.
 *
 * @author Stefano Reksten
 */
public interface LogRecordFormatter {

    /**
     * Serializes the provided record according to the implementation-specific format.
     *
     * @param logRecord the log record to serialize; must not be {@code null}.
     * @return a single-line serialized representation of the log record.
     */
    @Nonnull String format(@Nonnull LogRecord logRecord);

}
