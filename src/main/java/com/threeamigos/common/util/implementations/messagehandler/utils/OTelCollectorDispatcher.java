package com.threeamigos.common.util.implementations.messagehandler.utils;

import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordDispatcher;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-process collector dispatcher that fans out one log record to multiple backend dispatchers.
 * <p>
 * This class is useful when the same telemetry should be exported to multiple targets
 * (for example Jaeger-oriented and Grafana-oriented dispatchers) from the same handler call.
 */
public class OTelCollectorDispatcher implements LogRecordDispatcher {

    private final CopyOnWriteArrayList<LogRecordDispatcher> delegates = new CopyOnWriteArrayList<LogRecordDispatcher>();

    /**
     * Creates an empty collector dispatcher with no delegates.
     * Delegates can be added later via {@link #addDispatcher(LogRecordDispatcher)}.
     */
    public OTelCollectorDispatcher() {
    }

    /**
     * Creates a collector dispatcher pre-populated with the given delegates.
     *
     * @param delegates initial set of delegates; {@code null} or empty is allowed
     */
    public OTelCollectorDispatcher(final @Nullable Collection<? extends LogRecordDispatcher> delegates) {
        setDelegates(delegates);
    }

    /**
     * Adds a delegate dispatcher if it is not already present.
     * {@code null} is silently ignored.
     *
     * @param dispatcher delegate to add
     */
    public void addDispatcher(final @Nullable LogRecordDispatcher dispatcher) {
        if (dispatcher == null) {
            return;
        }
        delegates.addIfAbsent(dispatcher);
    }

    /**
     * Removes a delegate dispatcher.
     * {@code null} is silently ignored.
     *
     * @param dispatcher delegate to remove
     */
    public void removeDispatcher(final @Nullable LogRecordDispatcher dispatcher) {
        if (dispatcher == null) {
            return;
        }
        delegates.remove(dispatcher);
    }

    /**
     * Replaces the current delegate list with the supplied collection.
     * Existing delegates are cleared first. {@code null} entries within the collection
     * and {@code null} collection itself are silently skipped.
     *
     * @param newDelegates new set of delegates; may be {@code null} or empty
     */
    public void setDelegates(final @Nullable Collection<? extends LogRecordDispatcher> newDelegates) {
        delegates.clear();
        if (newDelegates == null || newDelegates.isEmpty()) {
            return;
        }
        for (LogRecordDispatcher delegate : newDelegates) {
            if (delegate != null) {
                delegates.addIfAbsent(delegate);
            }
        }
    }

    /**
     * Returns a point-in-time snapshot of the current delegate list.
     * The returned list is an independent copy; subsequent changes to this dispatcher
     * do not affect it.
     *
     * @return immutable snapshot of the delegate list (may be empty, never {@code null})
     */
    public List<LogRecordDispatcher> snapshotDelegates() {
        return new ArrayList<LogRecordDispatcher>(delegates);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Iterates over all registered delegates and calls
     * {@link LogRecordDispatcher#dispatchLogRecord dispatchLogRecord} on each.
     * If one or more delegates throw {@link IOException}, their exceptions are aggregated and
     * re-thrown as a single {@code IOException} with suppressed causes; all other delegates
     * are still invoked even if earlier ones fail.
     *
     * @throws IOException if at least one delegate fails; suppressed exceptions contain individual failures
     */
    @Override
    public void dispatchLogRecord(final @Nonnull LogRecord logRecord,
                                  final @Nonnull LogRecordFormatter logRecordFormatter) throws IOException {
        IOException aggregated = null;
        for (LogRecordDispatcher delegate : delegates) {
            try {
                delegate.dispatchLogRecord(logRecord, logRecordFormatter);
            } catch (IOException ex) {
                if (aggregated == null) {
                    aggregated = new IOException("One or more collector delegates failed");
                }
                aggregated.addSuppressed(ex);
            }
        }
        if (aggregated != null) {
            throw aggregated;
        }
    }
}
