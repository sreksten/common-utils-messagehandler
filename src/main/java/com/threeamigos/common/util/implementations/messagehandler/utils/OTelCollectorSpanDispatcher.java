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
 * <p>
 * Failure policy mirrors {@link OTelCollectorDispatcher}: every delegate is attempted, and
 * delegate-side {@link IOException} / {@link RuntimeException} failures are aggregated into one
 * {@link IOException} with suppressed causes.
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

    /**
     * Dispatches one span to every registered delegate.
     * <p>
     * Delegate {@link IOException} and {@link RuntimeException} failures are aggregated so that
     * all delegates are still attempted before failing the call.
     *
     * @throws IOException aggregated failure when one or more delegates fail
     */
    @Override
    public void dispatchSpan(final @Nonnull SpanData spanData) throws IOException {
        IOException aggregated = null;
        for (SpanDispatcher delegate : delegates) {
            try {
                delegate.dispatchSpan(spanData);
            } catch (IOException ex) {
                aggregated = appendFailure(aggregated, ex);
            } catch (RuntimeException ex) {
                aggregated = appendFailure(aggregated, ex);
            }
        }
        if (aggregated != null) {
            throw aggregated;
        }
    }

    private static IOException appendFailure(final IOException aggregated, final Throwable failure) {
        IOException updated = aggregated;
        if (updated == null) {
            updated = new IOException("One or more span collector delegates failed");
        }
        updated.addSuppressed(failure);
        return updated;
    }
}
