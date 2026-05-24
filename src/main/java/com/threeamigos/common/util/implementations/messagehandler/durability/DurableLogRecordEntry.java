package com.threeamigos.common.util.implementations.messagehandler.durability;

import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import jakarta.annotation.Nonnull;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable durable queue entry for one pending {@link LogRecord}.
 *
 * @author Stefano Reksten
 */
public final class DurableLogRecordEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String entryId;
    private final long createdAtEpochMillis;
    private final LogRecord logRecord;

    public DurableLogRecordEntry(final @Nonnull String entryId,
                                 final long createdAtEpochMillis,
                                 final @Nonnull LogRecord logRecord) {
        this.entryId = Objects.requireNonNull(entryId, "entryId must not be null");
        this.logRecord = Objects.requireNonNull(logRecord, "logRecord must not be null");
        this.createdAtEpochMillis = createdAtEpochMillis;
    }

    @Nonnull
    public String getEntryId() {
        return entryId;
    }

    public long getCreatedAtEpochMillis() {
        return createdAtEpochMillis;
    }

    @Nonnull
    public LogRecord getLogRecord() {
        return logRecord;
    }
}
