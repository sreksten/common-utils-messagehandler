package com.threeamigos.common.util.implementations.messagehandler.durability;

import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
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
    private final List<DurableLogRecordEntry> pendingEntries = new LinkedList<DurableLogRecordEntry>();

    @Override
    @Nonnull
    public String store(final @Nonnull LogRecord logRecord) {
        Objects.requireNonNull(logRecord, "logRecord must not be null");
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
            return new ArrayList<DurableLogRecordEntry>(pendingEntries);
        }
    }

    @Override
    public void remove(final @Nonnull List<String> entryIds) throws IOException {
        Objects.requireNonNull(entryIds, "entryIds must not be null");
        if (entryIds.isEmpty()) {
            return;
        }
        Set<String> idsToRemove = new HashSet<String>(entryIds);
        synchronized (lock) {
            Iterator<DurableLogRecordEntry> iterator = pendingEntries.iterator();
            while (iterator.hasNext()) {
                DurableLogRecordEntry durableEntry = iterator.next();
                if (idsToRemove.contains(durableEntry.getEntryId())) {
                    iterator.remove();
                }
            }
        }
    }
}
