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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SharedCorrelationResolver unit tests")
@Tag("unit")
@Tag("messageHandler")
class SharedCorrelationResolverUnitTest extends AbstractOtelValidatorLogTrapUnitTest {

    @Test
    @DisplayName("resolver should return nulls by default")
    void resolverShouldReturnNullsByDefault() {
        SharedCorrelationResolver resolver = new SharedCorrelationResolver();

        assertNull(resolver.resolveSpanContext());
        assertNull(resolver.resolveInstrumentationScope());
    }

    @Test
    @DisplayName("resolver should expose values set on current thread")
    void resolverShouldExposeValuesSetOnCurrentThread() {
        SharedCorrelationResolver resolver = new SharedCorrelationResolver();
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
        SharedCorrelationResolver resolver = new SharedCorrelationResolver();
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
        SharedCorrelationResolver resolver = new SharedCorrelationResolver();
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
        SharedCorrelationResolver resolver = new SharedCorrelationResolver();
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

        SharedCorrelationResolver.ScopeToken token = resolver.attach(activeSpan, activeScope);
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
        SharedCorrelationResolver resolver = new SharedCorrelationResolver();
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
        SharedCorrelationResolver resolver = new SharedCorrelationResolver();
        provider.setCorrelationResolver(resolver);

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

        SharedCorrelationResolver.ScopeToken token = resolver.attach(spanContext, scope);
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
}
