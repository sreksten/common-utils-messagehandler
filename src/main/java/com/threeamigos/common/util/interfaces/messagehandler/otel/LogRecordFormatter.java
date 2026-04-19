package com.threeamigos.common.util.interfaces.messagehandler.otel;

import jakarta.annotation.Nonnull;

/**
 * Formats a {@link LogRecord} into a JSON string.
 * <p>
 * Two representations are provided:
 * <ul>
 *   <li>{@link #formatRecord(LogRecord)} — the raw log record object {@code {...}}, suitable for
 *       embedding in custom envelopes or NDJSON log files.</li>
 *   <li>{@link #format(LogRecord)} — the full
 *       <a href="https://opentelemetry.io/docs/specs/otlp/#otlphttp-json-encoding">OTLP JSON</a>
 *       {@code ExportLogsServiceRequest} envelope
 *       {@code {"resourceLogs":[{"resource":{...},"scopeLogs":[{"scope":{...},"logRecords":[{...}]}]}]}}.</li>
 * </ul>
 *
 * @author Stefano Reksten
 */
public interface LogRecordFormatter {

    /**
     * Serializes the log record fields as a naked JSON object {@code {...}}, without the OTLP
     * {@code ResourceLogs / ScopeLogs} envelope. Resource and instrumentation scope are
     * included as inline fields {@code "resource"} and {@code "scope"}.
     *
     * @param logRecord the log record to serialize; must not be {@code null}.
     * @return a single-line JSON object string.
     */
    @Nonnull String formatRecord(@Nonnull LogRecord logRecord);

    /**
     * Serializes the log record as a full OTLP {@code ExportLogsServiceRequest} JSON envelope.
     * The implementation delegates to {@link #formatRecord(LogRecord)} for the inner log record
     * content.
     *
     * @param logRecord the log record to serialize; must not be {@code null}.
     * @return a single-line JSON string ready to be POSTed to an OTLP HTTP endpoint.
     */
    @Nonnull String format(@Nonnull LogRecord logRecord);

}
