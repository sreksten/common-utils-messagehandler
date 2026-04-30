package com.threeamigos.common.util.interfaces.messagehandler.otel;

/**
 * OpenTelemetry-like logger API.
 * <p>
 * Specification references:
 * <ul>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/logs/api/">OpenTelemetry Logs API</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/logs/api/#logger">OpenTelemetry Logs API: Logger</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/logs/api/#emit-a-logrecord">OpenTelemetry Logs API:
 *   Emit a LogRecord</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/logs/api/#enabled">OpenTelemetry Logs API: Enabled</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/logs/api/#concurrency-requirements">OpenTelemetry Logs
 *   API: Concurrency requirements</a></li>
 * </ul>
 * Implementations are expected to be safe for concurrent use by default.
 *
 * @author Stefano Reksten
 */
public interface Logger {

    /**
     * Emits the provided {@link LogRecord}.
     *
     * @param logRecord record to emit
     */
    void emit(LogRecord logRecord);

    /**
     * Returns whether this logger is enabled with default arguments.
     *
     * @return {@code true} if enabled
     */
    default boolean isEnabled() {
        return isEnabled(null, null, null);
    }

    /**
     * Returns whether this logger is enabled for the given severity.
     *
     * @param severityNumber severity number
     * @return {@code true} if enabled
     */
    default boolean isEnabled(final SeverityNumber severityNumber) {
        return isEnabled(null, severityNumber, null);
    }

    /**
     * Returns whether this logger is enabled for the given severity and event name.
     *
     * @param severityNumber severity number
     * @param eventName event name
     * @return {@code true} if enabled
     */
    default boolean isEnabled(final SeverityNumber severityNumber, final String eventName) {
        return isEnabled(null, severityNumber, eventName);
    }

    /**
     * Returns whether this logger is enabled for the given inputs.
     * <p>
     * The return value may change over time; callers should evaluate this for each emit operation.
     *
     * @param context context to associate with the record (optional)
     * @param severityNumber severity number (optional)
     * @param eventName event name (optional)
     * @return {@code true} if enabled
     */
    boolean isEnabled(Context context, SeverityNumber severityNumber, String eventName);
}

