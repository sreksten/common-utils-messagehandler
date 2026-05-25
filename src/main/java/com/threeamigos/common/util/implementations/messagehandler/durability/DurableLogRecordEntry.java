package com.threeamigos.common.util.implementations.messagehandler.durability;

import com.threeamigos.common.util.implementations.messagehandler.utils.ParametersValidator;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import jakarta.annotation.Nonnull;

import java.io.Serializable;

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
        this.entryId = ParametersValidator.validateNotNull(entryId, "entryId");
        this.createdAtEpochMillis = createdAtEpochMillis;
        this.logRecord = ParametersValidator.validateNotNull(logRecord, "logRecord");
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
