package com.threeamigos.common.util.implementations.messagehandler.durability;

import com.threeamigos.common.util.implementations.messagehandler.utils.ParametersValidator;
import com.threeamigos.common.util.interfaces.messagehandler.durability.HttpDispatchDurabilityStore;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Process-local in-memory durability store.
 * <p>
 * This policy survives retries within the same JVM process but does not persist across restarts.
 *
 * @author Stefano Reksten
 */
public class InMemoryHttpDispatchDurabilityStore implements HttpDispatchDurabilityStore {

    private final Object lock = new Object();
    private final List<DurableLogRecordEntry> pendingEntries = new LinkedList<>();

    @Override
    @Nonnull
    public String store(final @Nonnull LogRecord logRecord) {
        ParametersValidator.validateNotNull(logRecord, "logRecord");
        String entryId = UUID.randomUUID().toString();
        DurableLogRecordEntry durableEntry = new DurableLogRecordEntry(entryId, System.currentTimeMillis(), logRecord);
        synchronized (lock) {
            pendingEntries.add(durableEntry);
        }
        return entryId;
    }

    @Override
    @Nonnull
    public List<DurableLogRecordEntry> retrievePending() {
        synchronized (lock) {
            return new ArrayList<>(pendingEntries);
        }
    }

    @Override
    public void remove(final @Nonnull List<String> entryIds) throws IOException {
        ParametersValidator.validateNotNull(entryIds, "entryIds");
        if (entryIds.isEmpty()) {
            return;
        }
        Set<String> idsToRemove = new HashSet<>(entryIds);
        synchronized (lock) {
            pendingEntries.removeIf(durableEntry -> idsToRemove.contains(durableEntry.getEntryId()));
        }
    }
}
