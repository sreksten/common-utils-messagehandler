package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.CorrelationResolver;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import jakarta.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared thread-local correlation resolver for Trace/Logs/Metrics integration.
 * <p>
 * This component can be reused by multiple providers so that emitted telemetry resolves the same
 * active span context and instrumentation scope on the current thread.
 * <p>
 * Correlation values are thread-bound and are not propagated automatically across threads.
 *
 * @author Stefano Reksten
 */
public class SharedCorrelationResolver implements CorrelationResolver {

    private final ThreadLocal<SpanContext> activeSpanContext = new ThreadLocal<>();
    private final ThreadLocal<InstrumentationScope> activeInstrumentationScope = new ThreadLocal<>();

    @Override
    public @Nullable SpanContext resolveSpanContext() {
        return activeSpanContext.get();
    }

    @Override
    public @Nullable InstrumentationScope resolveInstrumentationScope() {
        return activeInstrumentationScope.get();
    }

    public void setActiveSpanContext(final @Nullable SpanContext spanContext) {
        if (spanContext == null) {
            activeSpanContext.remove();
            return;
        }
        activeSpanContext.set(spanContext);
    }

    public void setActiveInstrumentationScope(final @Nullable InstrumentationScope instrumentationScope) {
        if (instrumentationScope == null) {
            activeInstrumentationScope.remove();
            return;
        }
        activeInstrumentationScope.set(instrumentationScope);
    }

    public void clear() {
        activeSpanContext.remove();
        activeInstrumentationScope.remove();
    }

    /**
     * Attaches correlation values on the current thread and returns a token that restores previous values.
     *
     * @param spanContext span context to set for the current thread (nullable)
     * @param instrumentationScope instrumentation scope to set for the current thread (nullable)
     * @return closeable token that restores previous values once
     */
    public ScopeToken attach(final @Nullable SpanContext spanContext,
                             final @Nullable InstrumentationScope instrumentationScope) {
        SpanContext previousSpanContext = activeSpanContext.get();
        InstrumentationScope previousInstrumentationScope = activeInstrumentationScope.get();
        setActiveSpanContext(spanContext);
        setActiveInstrumentationScope(instrumentationScope);
        return new ScopeToken(this, previousSpanContext, previousInstrumentationScope);
    }

    public static final class ScopeToken implements AutoCloseable {
        private final SharedCorrelationResolver owner;
        private final SpanContext previousSpanContext;
        private final InstrumentationScope previousInstrumentationScope;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private ScopeToken(final SharedCorrelationResolver owner,
                           final SpanContext previousSpanContext,
                           final InstrumentationScope previousInstrumentationScope) {
            this.owner = owner;
            this.previousSpanContext = previousSpanContext;
            this.previousInstrumentationScope = previousInstrumentationScope;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            owner.setActiveSpanContext(previousSpanContext);
            owner.setActiveInstrumentationScope(previousInstrumentationScope);
        }
    }
}
