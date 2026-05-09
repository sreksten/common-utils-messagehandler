package com.threeamigos.common.util.implementations.messagehandler.utils;

import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanData;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanDispatcher;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-process collector dispatcher that fans out one completed span to multiple span dispatchers.
 */
public class OTelCollectorSpanDispatcher implements SpanDispatcher {

    private final CopyOnWriteArrayList<SpanDispatcher> delegates = new CopyOnWriteArrayList<SpanDispatcher>();

    public OTelCollectorSpanDispatcher() {
    }

    public OTelCollectorSpanDispatcher(final @Nullable Collection<? extends SpanDispatcher> delegates) {
        setDelegates(delegates);
    }

    public void addDispatcher(final @Nullable SpanDispatcher dispatcher) {
        if (dispatcher == null) {
            return;
        }
        delegates.addIfAbsent(dispatcher);
    }

    public void setDelegates(final @Nullable Collection<? extends SpanDispatcher> newDelegates) {
        delegates.clear();
        if (newDelegates == null || newDelegates.isEmpty()) {
            return;
        }
        for (SpanDispatcher delegate : newDelegates) {
            if (delegate != null) {
                delegates.addIfAbsent(delegate);
            }
        }
    }

    public List<SpanDispatcher> snapshotDelegates() {
        return new ArrayList<SpanDispatcher>(delegates);
    }

    @Override
    public void dispatchSpan(final @Nonnull SpanData spanData) throws IOException {
        IOException aggregated = null;
        for (SpanDispatcher delegate : delegates) {
            try {
                delegate.dispatchSpan(spanData);
            } catch (IOException ex) {
                if (aggregated == null) {
                    aggregated = new IOException("One or more span collector delegates failed");
                }
                aggregated.addSuppressed(ex);
            }
        }
        if (aggregated != null) {
            throw aggregated;
        }
    }
}
