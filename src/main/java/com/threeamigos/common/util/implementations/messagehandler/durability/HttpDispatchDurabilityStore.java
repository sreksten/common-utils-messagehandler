package com.threeamigos.common.util.implementations.messagehandler.durability;

import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.util.List;

/**
 * Durability hook for HTTP-oriented handlers.
 * <p>
 * Implementations keep pending records until transport dispatch acknowledges success.
 * Typical implementations include:
 * <ul>
 *   <li>in-memory queue (process-local, non-persistent)</li>
 *   <li>file-backed queue (local durable storage)</li>
 *   <li>Redis-backed queue (external durable storage)</li>
 * </ul>
 *
 * @author Stefano Reksten
 */
public interface HttpDispatchDurabilityStore extends AutoCloseable {

    /**
     * Stores one record as pending and returns its durable entry id.
     *
     * @param logRecord record to store
     * @return durable entry id
     * @throws IOException when storing fails
     */
    @Nonnull
    String store(@Nonnull LogRecord logRecord) throws IOException;

    /**
     * Retrieves all currently pending records in dispatch order.
     *
     * @return pending records snapshot
     * @throws IOException when retrieval fails
     */
    @Nonnull
    List<DurableLogRecordEntry> retrievePending() throws IOException;

    /**
     * Removes acknowledged entries from durable storage.
     *
     * @param entryIds entry ids to remove
     * @throws IOException when removal fails
     */
    void remove(@Nonnull List<String> entryIds) throws IOException;

    /**
     * Returns a short policy name useful in diagnostics.
     *
     * @return policy name
     */
    @Nonnull
    default String getPolicyName() {
        return getClass().getSimpleName();
    }

    /**
     * Releases policy resources.
     *
     * @throws IOException when close fails
     */
    @Override
    default void close() throws IOException {
        // default no-op
    }
}
