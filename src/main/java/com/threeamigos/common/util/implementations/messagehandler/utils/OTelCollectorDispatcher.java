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

    public OTelCollectorDispatcher() {
    }

    public OTelCollectorDispatcher(final @Nullable Collection<? extends LogRecordDispatcher> delegates) {
        setDelegates(delegates);
    }

    public void addDispatcher(final @Nullable LogRecordDispatcher dispatcher) {
        if (dispatcher == null) {
            return;
        }
        delegates.addIfAbsent(dispatcher);
    }

    public void removeDispatcher(final @Nullable LogRecordDispatcher dispatcher) {
        if (dispatcher == null) {
            return;
        }
        delegates.remove(dispatcher);
    }

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

    public List<LogRecordDispatcher> snapshotDelegates() {
        return new ArrayList<LogRecordDispatcher>(delegates);
    }

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
