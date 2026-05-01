package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared thread-local correlation resolver for Trace/Logs/Metrics integration.
 * <p>
 * This component can be reused by multiple providers so that emitted telemetry resolves the same
 * active span context and instrumentation scope on the current thread.
 * <p>
 * Correlation values are thread-bound. For multi-thread execution, use {@link #wrap(Runnable)},
 * {@link #wrap(Callable)}, or {@link #contextAwareExecutorService(ExecutorService)}.
 *
 * @author Stefano Reksten
 */
public class CorrelationResolver {

    private final ThreadLocal<SpanContext> activeSpanContext = new ThreadLocal<>();
    private final ThreadLocal<InstrumentationScope> activeInstrumentationScope = new ThreadLocal<>();

    public @Nullable SpanContext resolveSpanContext() {
        return activeSpanContext.get();
    }

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

    /**
     * Captures the correlation values currently bound to this thread.
     *
     * @return immutable snapshot of current correlation values
     */
    public CorrelationSnapshot capture() {
        return new CorrelationSnapshot(activeSpanContext.get(), activeInstrumentationScope.get());
    }

    /**
     * Attaches a captured snapshot on the current thread and returns a restoration token.
     *
     * @param snapshot snapshot to attach. If null, clears both values for current thread.
     * @return closeable token that restores previous values once
     */
    public ScopeToken attach(final @Nullable CorrelationSnapshot snapshot) {
        if (snapshot == null) {
            return attach(null, null);
        }
        return attach(snapshot.getSpanContext(), snapshot.getInstrumentationScope());
    }

    /**
     * Wraps a runnable so it runs with the caller thread correlation context.
     *
     * @param delegate task to wrap
     * @return wrapped task that propagates and restores correlation context
     */
    public Runnable wrap(final Runnable delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate runnable must not be null");
        }
        final CorrelationSnapshot snapshot = capture();
        return new Runnable() {
            @Override
            public void run() {
                ScopeToken token = attach(snapshot);
                try {
                    delegate.run();
                } finally {
                    token.close();
                }
            }
        };
    }

    /**
     * Wraps a callable so it runs with the caller thread correlation context.
     *
     * @param delegate task to wrap
     * @param <V> result type
     * @return wrapped task that propagates and restores correlation context
     */
    public <V> Callable<V> wrap(final Callable<V> delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate callable must not be null");
        }
        final CorrelationSnapshot snapshot = capture();
        return new Callable<V>() {
            @Override
            public V call() throws Exception {
                ScopeToken token = attach(snapshot);
                try {
                    return delegate.call();
                } finally {
                    token.close();
                }
            }
        };
    }

    /**
     * Creates an executor that propagates caller correlation for each execute call.
     *
     * @param delegate target executor
     * @return context-aware executor
     */
    public Executor contextAwareExecutor(final Executor delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate executor must not be null");
        }
        return new Executor() {
            @Override
            public void execute(final Runnable command) {
                delegate.execute(wrap(command));
            }
        };
    }

    /**
     * Creates an executor service that propagates caller correlation for each task submission.
     *
     * @param delegate target executor service
     * @return context-aware executor service
     */
    public ExecutorService contextAwareExecutorService(final ExecutorService delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate executor service must not be null");
        }
        return new ContextAwareExecutorService(delegate, this);
    }

    public static final class CorrelationSnapshot {
        private final SpanContext spanContext;
        private final InstrumentationScope instrumentationScope;

        private CorrelationSnapshot(final SpanContext spanContext,
                                    final InstrumentationScope instrumentationScope) {
            this.spanContext = spanContext;
            this.instrumentationScope = instrumentationScope;
        }

        public @Nullable SpanContext getSpanContext() {
            return spanContext;
        }

        public @Nullable InstrumentationScope getInstrumentationScope() {
            return instrumentationScope;
        }
    }

    private static final class ContextAwareExecutorService extends AbstractExecutorService {
        private final ExecutorService delegate;
        private final CorrelationResolver resolver;

        private ContextAwareExecutorService(final ExecutorService delegate,
                                            final CorrelationResolver resolver) {
            this.delegate = delegate;
            this.resolver = resolver;
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @Override
        public void execute(final Runnable command) {
            delegate.execute(resolver.wrap(command));
        }
    }

    public static final class ScopeToken implements AutoCloseable {
        private final CorrelationResolver owner;
        private final SpanContext previousSpanContext;
        private final InstrumentationScope previousInstrumentationScope;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private ScopeToken(final CorrelationResolver owner,
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
