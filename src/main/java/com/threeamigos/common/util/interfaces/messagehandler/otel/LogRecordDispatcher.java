package com.threeamigos.common.util.interfaces.messagehandler.otel;

import jakarta.annotation.Nonnull;

import java.io.IOException;

/**
 * Generic dispatcher contract for exporting {@link LogRecord} instances to a backend.
 * <p>
 * Implementations can decide which wire payload to produce (for example OTLP logs JSON
 * or backend-specific JSON) as long as dispatch preserves the information available in
 * the incoming {@link LogRecord}.
 */
public interface LogRecordDispatcher {

    /**
     * Dispatches a log record using the provided formatter.
     *
     * @param logRecord record to dispatch
     * @param logRecordFormatter formatter to encode the record when needed by the backend
     * @throws IOException when dispatch fails
     */
    void dispatchLogRecord(final @Nonnull LogRecord logRecord,
                           final @Nonnull LogRecordFormatter logRecordFormatter) throws IOException;
}
