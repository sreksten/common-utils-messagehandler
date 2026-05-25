package com.threeamigos.common.util.interfaces.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.utils.ParametersValidator;
import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.util.List;

/**
 * Generic dispatcher contract for exporting {@link LogRecord} instances to a backend.
 * <p>
 * Implementations can decide which wire payload to produce (for example, OTLP logs JSON
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

    /**
     * Dispatches multiple log records using the provided formatter.
     * <p>
     * The default implementation dispatches each record individually in list order.
     * Implementations can override this to use backend-native batch payloads.
     *
     * @param logRecords records to dispatch
     * @param logRecordFormatter formatter to encode records when needed by the backend
     * @throws IOException when dispatch fails
     */
    default void dispatchLogRecords(final @Nonnull List<LogRecord> logRecords,
                                    final @Nonnull LogRecordFormatter logRecordFormatter) throws IOException {
        ParametersValidator.validateNotNull(logRecords, "logRecords");
        ParametersValidator.validateNotNull(logRecordFormatter, "logRecordFormatter");
        if (logRecords.isEmpty()) {
            return;
        }
        for (LogRecord logRecord : logRecords) {
            if (logRecord == null) {
                continue;
            }
            dispatchLogRecord(logRecord, logRecordFormatter);
        }
    }
}
