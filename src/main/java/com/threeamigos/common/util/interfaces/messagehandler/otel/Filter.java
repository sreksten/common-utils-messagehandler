package com.threeamigos.common.util.interfaces.messagehandler.otel;

import jakarta.annotation.Nullable;

/**
 * A chaining capable filter for LogRecords.
 * If the filter returns null, the LogRecord is meant to be discarded.
 * LogFilter in input may be null as it could be the result of another filter.
 *
 * @author Stefano Reksten
 */
@FunctionalInterface
public interface Filter {

    @Nullable LogRecord filter(@Nullable LogRecord logRecord);
}
