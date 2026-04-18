package com.threeamigos.common.util.interfaces.messagehandler;

import jakarta.annotation.Nonnull;

/**
 * Strategy interface for formatting log output lines.
 * <p>
 * Implementations decide the exact layout of each log entry — plain text, JSON, CSV, etc.
 * The two built-in implementations are:
 * <ul>
 *   <li>{@code PlainTextLogFormatter} — human-readable, ISO-timestamp prefixed lines (default)</li>
 *   <li>{@code JsonLogFormatter} — NDJSON (one JSON object per line) for log aggregation systems</li>
 * </ul>
 * <p>
 * A formatter is set on any {@code AbstractOutputMessageHandler} subclass via
 * {@code setFormatter(LogFormatter)}.
 *
 * @author Stefano Reksten
 */
public interface LogFormatter {

    /**
     * Formats a single log line for the given level and message.
     *
     * @param level   the log level
     * @param message the pre-resolved, non-null message string
     * @return the complete formatted line, ready to be written to the output destination
     */
    @Nonnull String format(@Nonnull LogLevelEnum level, @Nonnull String message, @Nonnull ContextInfo contextInfo);

    /**
     * Formats an exception event — including the full stack trace — as a single, possibly
     * multi-line, string.
     *
     * @param exception the non-null exception to format
     * @return the complete formatted output for this exception event
     */
    @Nonnull String formatException(@Nonnull Exception exception, @Nonnull ContextInfo contextInfo);

    /**
     * Formats a prefixed exception event — including the full stack trace — as a single, possibly
     * multi-line, string.
     *
     * @param prefix    a human-readable context string prepended to the exception detail
     * @param exception the non-null exception to format
     * @return the complete formatted output for this exception event
     */
    @Nonnull String formatException(@Nonnull String prefix, @Nonnull Exception exception, @Nonnull ContextInfo contextInfo);
}
