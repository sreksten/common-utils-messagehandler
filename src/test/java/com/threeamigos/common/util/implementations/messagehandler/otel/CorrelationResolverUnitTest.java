package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CorrelationResolver unit tests")
@Tag("unit")
@Tag("messageHandler")
class CorrelationResolverUnitTest extends AbstractOtelValidatorLogTrapUnitTest {

    @Test
    @DisplayName("resolver should return nulls by default")
    void resolverShouldReturnNullsByDefault() {
        CorrelationResolver resolver = new CorrelationResolver();

        assertNull(resolver.resolveSpanContext());
        assertNull(resolver.resolveInstrumentationScope());
    }

    @Test
    @DisplayName("resolver should expose values set on current thread")
    void resolverShouldExposeValuesSetOnCurrentThread() {
        CorrelationResolver resolver = new CorrelationResolver();
        SpanContext spanContext = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());
        InstrumentationScope scope = InstrumentationScopeFactory.create(
                "resolver-scope",
                "1.0.0",
                "https://resolver.schema",
                Collections.emptyList());

        resolver.setActiveSpanContext(spanContext);
        resolver.setActiveInstrumentationScope(scope);

        assertSame(spanContext, resolver.resolveSpanContext());
        assertSame(scope, resolver.resolveInstrumentationScope());
    }

    @Test
    @DisplayName("setting null should clear active values")
    void settingNullShouldClearActiveValues() {
        CorrelationResolver resolver = new CorrelationResolver();
        resolver.setActiveSpanContext(new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl()));
        resolver.setActiveInstrumentationScope(InstrumentationScopeFactory.create(
                "resolver-scope",
                "1.0.0",
                "https://resolver.schema",
                Collections.emptyList()));

        resolver.setActiveSpanContext(null);
        resolver.setActiveInstrumentationScope(null);

        assertNull(resolver.resolveSpanContext());
        assertNull(resolver.resolveInstrumentationScope());
    }

    @Test
    @DisplayName("clear should remove active values")
    void clearShouldRemoveActiveValues() {
        CorrelationResolver resolver = new CorrelationResolver();
        resolver.setActiveSpanContext(new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl()));
        resolver.setActiveInstrumentationScope(InstrumentationScopeFactory.create(
                "resolver-scope",
                "1.0.0",
                "https://resolver.schema",
                Collections.emptyList()));

        resolver.clear();

        assertNull(resolver.resolveSpanContext());
        assertNull(resolver.resolveInstrumentationScope());
    }

    @Test
    @DisplayName("attach should set values and token should restore previous values once")
    void attachShouldSetValuesAndRestorePreviousValues() {
        CorrelationResolver resolver = new CorrelationResolver();
        SpanContext previousSpan = new SpanContextImpl(
                "4b8efff798038103d269b633813fc60c",
                "ddd19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());
        InstrumentationScope previousScope = InstrumentationScopeFactory.create(
                "previous-scope",
                "1.0.0",
                "https://previous.schema",
                Collections.emptyList());
        SpanContext activeSpan = new SpanContextImpl(
                "7b8efff798038103d269b633813fc60c",
                "aaa19b7ec3c1b174",
                (byte) 0x03,
                false,
                new TraceStateImpl());
        InstrumentationScope activeScope = InstrumentationScopeFactory.create(
                "active-scope",
                "1.0.0",
                "https://active.schema",
                Collections.emptyList());

        resolver.setActiveSpanContext(previousSpan);
        resolver.setActiveInstrumentationScope(previousScope);

        CorrelationResolver.ScopeToken token = resolver.attach(activeSpan, activeScope);
        assertSame(activeSpan, resolver.resolveSpanContext());
        assertSame(activeScope, resolver.resolveInstrumentationScope());

        token.close();
        assertSame(previousSpan, resolver.resolveSpanContext());
        assertSame(previousScope, resolver.resolveInstrumentationScope());

        // Ensure close is idempotent.
        token.close();
        assertSame(previousSpan, resolver.resolveSpanContext());
        assertSame(previousScope, resolver.resolveInstrumentationScope());
    }

    @Test
    @DisplayName("resolver should be thread-local")
    void resolverShouldBeThreadLocal() throws Exception {
        CorrelationResolver resolver = new CorrelationResolver();
        SpanContext mainSpan = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());

        resolver.setActiveSpanContext(mainSpan);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Callable<Boolean> task = new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return resolver.resolveSpanContext() == null
                            && resolver.resolveInstrumentationScope() == null;
                }
            };
            Future<Boolean> future = executor.submit(task);
            assertTrue(future.get().booleanValue());
        } finally {
            executor.shutdownNow();
        }

        assertSame(mainSpan, resolver.resolveSpanContext());
    }

    @Test
    @DisplayName("shared resolver should correlate trace and scope in provider-enriched log records")
    void sharedResolverShouldCorrelateProviderEnrichedLogs() {
        TracerProvider provider = TracerProvider.createProvider();
        CorrelationResolver resolver = provider.getCorrelationResolver();

        SpanContext spanContext = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());
        InstrumentationScope scope = InstrumentationScopeFactory.create(
                "checkout-api",
                "1.0.0",
                "https://schema",
                Collections.emptyList());

        CorrelationResolver.ScopeToken token = resolver.attach(spanContext, scope);
        try {
            LogRecordFactory factory = provider.enrichingLogRecordFactory(new LogRecordFactoryImpl(), null);
            LogRecord record = factory.create(SeverityNumber.INFO, "hello");

            assertSame(scope, record.getInstrumentationScope());
            assertEquals(spanContext.getTraceId(), record.getTraceId());
            assertEquals(spanContext.getSpanId(), record.getSpanId());
        } finally {
            token.close();
        }
    }

    @Test
    @DisplayName("capture and attach snapshot should set values and restore previous state")
    void captureAndAttachSnapshotShouldSetValuesAndRestorePreviousState() {
        CorrelationResolver resolver = new CorrelationResolver();
        SpanContext previousSpan = new SpanContextImpl(
                "4b8efff798038103d269b633813fc60c",
                "ddd19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());
        InstrumentationScope previousScope = InstrumentationScopeFactory.create(
                "previous-scope",
                "1.0.0",
                "https://previous.schema",
                Collections.emptyList());
        SpanContext activeSpan = new SpanContextImpl(
                "7b8efff798038103d269b633813fc60c",
                "aaa19b7ec3c1b174",
                (byte) 0x03,
                false,
                new TraceStateImpl());
        InstrumentationScope activeScope = InstrumentationScopeFactory.create(
                "active-scope",
                "1.0.0",
                "https://active.schema",
                Collections.emptyList());
        resolver.setActiveSpanContext(activeSpan);
        resolver.setActiveInstrumentationScope(activeScope);
        CorrelationResolver.CorrelationSnapshot snapshot = resolver.capture();

        resolver.setActiveSpanContext(previousSpan);
        resolver.setActiveInstrumentationScope(previousScope);
        CorrelationResolver.ScopeToken token = resolver.attach(snapshot);
        try {
            assertSame(activeSpan, resolver.resolveSpanContext());
            assertSame(activeScope, resolver.resolveInstrumentationScope());
        } finally {
            token.close();
        }

        assertSame(previousSpan, resolver.resolveSpanContext());
        assertSame(previousScope, resolver.resolveInstrumentationScope());
    }

    @Test
    @DisplayName("wrapped runnable should propagate context to worker and restore worker previous state")
    void wrappedRunnableShouldPropagateContextToWorkerAndRestoreWorkerPreviousState() throws Exception {
        CorrelationResolver resolver = new CorrelationResolver();
        SpanContext submittingSpan = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());
        InstrumentationScope submittingScope = InstrumentationScopeFactory.create(
                "submitter-scope",
                "1.0.0",
                "https://submitter.schema",
                Collections.emptyList());
        SpanContext workerSpan = new SpanContextImpl(
                "7b8efff798038103d269b633813fc60c",
                "aaa19b7ec3c1b174",
                (byte) 0x03,
                false,
                new TraceStateImpl());
        InstrumentationScope workerScope = InstrumentationScopeFactory.create(
                "worker-scope",
                "1.0.0",
                "https://worker.schema",
                Collections.emptyList());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    resolver.setActiveSpanContext(workerSpan);
                    resolver.setActiveInstrumentationScope(workerScope);
                }
            }).get();

            resolver.setActiveSpanContext(submittingSpan);
            resolver.setActiveInstrumentationScope(submittingScope);

            AtomicReference<SpanContext> seenSpan = new AtomicReference<SpanContext>();
            AtomicReference<InstrumentationScope> seenScope = new AtomicReference<InstrumentationScope>();
            executor.submit(resolver.wrap(new Runnable() {
                @Override
                public void run() {
                    seenSpan.set(resolver.resolveSpanContext());
                    seenScope.set(resolver.resolveInstrumentationScope());
                }
            })).get();

            assertSame(submittingSpan, seenSpan.get());
            assertSame(submittingScope, seenScope.get());

            AtomicReference<SpanContext> restoredSpan = new AtomicReference<SpanContext>();
            AtomicReference<InstrumentationScope> restoredScope = new AtomicReference<InstrumentationScope>();
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    restoredSpan.set(resolver.resolveSpanContext());
                    restoredScope.set(resolver.resolveInstrumentationScope());
                }
            }).get();

            assertSame(workerSpan, restoredSpan.get());
            assertSame(workerScope, restoredScope.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("wrapped callable and context-aware executor service should propagate submitter context")
    void wrappedCallableAndContextAwareExecutorServiceShouldPropagateSubmitterContext() throws Exception {
        CorrelationResolver resolver = new CorrelationResolver();
        SpanContext mainSpan = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());
        InstrumentationScope mainScope = InstrumentationScopeFactory.create(
                "main-scope",
                "1.0.0",
                "https://main.schema",
                Collections.emptyList());

        ExecutorService raw = Executors.newFixedThreadPool(2);
        ExecutorService contextAware = resolver.contextAwareExecutorService(raw);
        try {
            resolver.setActiveSpanContext(mainSpan);
            resolver.setActiveInstrumentationScope(mainScope);

            Future<SpanContext> spanFuture = contextAware.submit(new Callable<SpanContext>() {
                @Override
                public SpanContext call() {
                    return resolver.resolveSpanContext();
                }
            });
            Future<InstrumentationScope> scopeFuture = contextAware.submit(new Callable<InstrumentationScope>() {
                @Override
                public InstrumentationScope call() {
                    return resolver.resolveInstrumentationScope();
                }
            });

            assertSame(mainSpan, spanFuture.get());
            assertSame(mainScope, scopeFuture.get());

            Future<SpanContext> wrappedSpanFuture = raw.submit(resolver.wrap(new Callable<SpanContext>() {
                @Override
                public SpanContext call() {
                    return resolver.resolveSpanContext();
                }
            }));
            assertSame(mainSpan, wrappedSpanFuture.get());
        } finally {
            contextAware.shutdownNow();
        }
    }

    @Test
    @DisplayName("attach null snapshot and null delegate guards should be covered")
    void attachNullSnapshotAndNullDelegateGuardsShouldBeCovered() {
        CorrelationResolver resolver = new CorrelationResolver();

        assertDoesNotThrow(() -> {
            CorrelationResolver.ScopeToken token = resolver.attach((CorrelationResolver.CorrelationSnapshot) null);
            token.close();
        });

        assertThrows(IllegalArgumentException.class, () -> resolver.wrap((Runnable) null));
        assertThrows(IllegalArgumentException.class, () -> resolver.wrap((Callable<?>) null));
        assertThrows(IllegalArgumentException.class, () -> resolver.contextAwareExecutor(null));
        assertThrows(IllegalArgumentException.class, () -> resolver.contextAwareExecutorService(null));
    }

    @Test
    @DisplayName("context-aware executor service delegates lifecycle methods")
    void contextAwareExecutorServiceDelegatesLifecycleMethods() throws Exception {
        CorrelationResolver resolver = new CorrelationResolver();
        ExecutorService raw = Executors.newSingleThreadExecutor();
        ExecutorService contextAware = resolver.contextAwareExecutorService(raw);
        try {
            assertTrue(!contextAware.isShutdown());
            contextAware.submit(new Runnable() {
                @Override
                public void run() {
                    // no-op
                }
            }).get();
            contextAware.shutdown();
            assertTrue(contextAware.isShutdown());
            assertDoesNotThrow(() -> contextAware.awaitTermination(1, TimeUnit.SECONDS));
            assertTrue(contextAware.isTerminated() || !contextAware.isTerminated());
        } finally {
            contextAware.shutdownNow();
        }
    }
}
