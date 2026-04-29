package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TracerProvider unit tests")
@Tag("unit")
@Tag("messageHandler")
class TracerProviderUnitTest {

    @AfterEach
    void cleanupLenient() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(false);
    }

    @Test
    @DisplayName("getGlobal should return singleton provider")
    void getGlobalShouldReturnSingletonProvider() {
        TracerProvider first = TracerProvider.getGlobal();
        TracerProvider second = TracerProvider.getGlobal();

        assertNotNull(first);
        assertSame(first, second);
    }

    @Test
    @DisplayName("createProvider should return a distinct provider instance")
    void createProviderShouldReturnDistinctProviderInstance() {
        TracerProvider global = TracerProvider.getGlobal();
        TracerProvider created = TracerProvider.createProvider();

        assertNotNull(created);
        assertNotSame(global, created);
    }

    @Test
    @DisplayName("getTracer should return non-null tracers for null and non-null instrumentation names")
    void getTracerShouldReturnNonNullForNullAndNonNullInstrumentationNames() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        TracerProvider provider = TracerProvider.createProvider();

        Tracer withNullName = provider.getTracer(null, "1.0.0", "schema", Collections.emptyList());
        Tracer withName = provider.getTracer("orders", "1.0.0", "schema", Collections.emptyList());

        assertNotNull(withNullName);
        assertNotNull(withName);
    }

    @Test
    @DisplayName("getTracer should cache tracers by instrumentation scope identity")
    void getTracerShouldCacheByInstrumentationScopeIdentity() {
        TracerProvider provider = TracerProvider.createProvider();

        Tracer first = provider.getTracer("orders", "1.0.0", "schema", Collections.emptyList());
        Tracer second = provider.getTracer("orders", "1.0.0", "schema", Collections.emptyList());
        Tracer third = provider.getTracer("orders", "1.1.0", "schema", Collections.emptyList());

        assertSame(first, second);
        assertNotSame(first, third);
    }

    @Test
    @DisplayName("two-argument getTracer overload should resolve to null schema and empty attributes")
    void twoArgumentGetTracerOverloadShouldResolveToNullSchemaAndEmptyAttributes() {
        TracerProvider provider = TracerProvider.createProvider();

        Tracer fromTwoArgs = provider.getTracer("orders", "1.0.0");
        Tracer fromFourArgs = provider.getTracer("orders", "1.0.0", null, Collections.emptyList());

        assertNotNull(fromTwoArgs);
        assertSame(fromFourArgs, fromTwoArgs);
    }

    @Test
    @DisplayName("getTracer should support direct InstrumentationScope lookup")
    void getTracerShouldSupportInstrumentationScopeOverload() {
        TracerProvider provider = TracerProvider.createProvider();
        InstrumentationScope scope = InstrumentationScopeFactory.create(
                "orders",
                "1.0.0",
                "https://schema",
                Collections.emptyList());

        Tracer fromScope = provider.getTracer(scope);
        Tracer fromFields = provider.getTracer("orders", "1.0.0", "https://schema", Collections.emptyList());

        assertSame(fromScope, fromFields);
    }

    @Test
    @DisplayName("default schema URL should be applied when schema argument is absent")
    void defaultSchemaUrlShouldBeAppliedWhenSchemaAbsent() {
        TracerProvider provider = TracerProvider.createProvider();
        provider.setDefaultSchemaUrl("https://provider-schema");

        Tracer tracer = provider.getTracer("orders", "1.0.0", null, Collections.emptyList());

        assertNotNull(tracer.getInstrumentationScope());
        assertEquals("https://provider-schema", tracer.getInstrumentationScope().getSchemaUrl());
    }

    @Test
    @DisplayName("provider default resource and common attributes should be configurable")
    void defaultResourceAndCommonAttributesShouldBeConfigurable() {
        TracerProvider provider = TracerProvider.createProvider();
        provider.setDefaultResource(ResourceFactory.create(null, null, null));
        provider.setDefaultCommonAttributes(List.of(
                KeyValueFactory.of("env", AnyValueFactory.ofString("test"))));

        assertNotNull(provider.getDefaultResource());
        assertEquals(1, provider.getDefaultCommonAttributes().size());
        assertEquals("env", provider.getDefaultCommonAttributes().get(0).getKey());

        provider.setDefaultSchemaUrl("   ");
        assertNull(provider.getDefaultSchemaUrl());
    }

    @Test
    @DisplayName("enriching factory should apply provider defaults and resolver correlation")
    void enrichingFactoryShouldApplyProviderDefaultsAndResolverCorrelation() {
        TracerProvider provider = TracerProvider.createProvider();
        provider.setDefaultResource(ResourceFactory.create("https://provider.schema", null, null));
        provider.setDefaultCommonAttributes(List.of(
                KeyValueFactory.of("env", AnyValueFactory.ofString("test"))));
        SpanContext resolverContext = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());
        InstrumentationScope resolverScope = InstrumentationScopeFactory.create(
                "resolver-scope",
                "1.0.0",
                "https://resolver.schema",
                Collections.emptyList());
        provider.setCorrelationResolver(new TracerProvider.CorrelationResolver() {
            @Override
            public SpanContext resolveSpanContext() {
                return resolverContext;
            }

            @Override
            public InstrumentationScope resolveInstrumentationScope() {
                return resolverScope;
            }
        });

        LogRecordFactory enrichedFactory =
                provider.enrichingLogRecordFactory(new LogRecordFactoryImpl(), null);
        LogRecord record = enrichedFactory.create(SeverityNumber.INFO, "hello");

        assertNotNull(record.getResource());
        assertNotNull(record.getInstrumentationScope());
        assertEquals("resolver-scope", record.getInstrumentationScope().getName());
        assertEquals(resolverContext.getTraceId(), record.getTraceId());
        assertEquals(resolverContext.getSpanId(), record.getSpanId());
        assertEquals(0x01, record.getTraceFlags());
        assertTrue(record.getAttributes().stream().map(kv -> kv.getKey()).collect(Collectors.toList()).contains("env"));
    }

    @Test
    @DisplayName("explicit span context should override resolver span context in provider enricher")
    void explicitSpanContextShouldOverrideResolverSpanContextInProviderEnricher() {
        TracerProvider provider = TracerProvider.createProvider();
        SpanContext resolverContext = new SpanContextImpl(
                "4b8efff798038103d269b633813fc60c",
                "ddd19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());
        SpanContext explicitContext = new SpanContextImpl(
                "7b8efff798038103d269b633813fc60c",
                "aaa19b7ec3c1b174",
                (byte) 0x03,
                false,
                new TraceStateImpl());
        provider.setCorrelationResolver(() -> resolverContext);

        LogRecordFactory enrichedFactory = provider.enrichingLogRecordFactory(
                new LogRecordFactoryImpl(),
                null,
                explicitContext);
        LogRecord record = enrichedFactory.create();

        assertEquals(explicitContext.getTraceId(), record.getTraceId());
        assertEquals(explicitContext.getSpanId(), record.getSpanId());
        assertEquals(0x03, record.getTraceFlags());
    }

    @Test
    @DisplayName("explicit scope should override resolver scope in provider enricher")
    void explicitScopeShouldOverrideResolverScopeInProviderEnricher() {
        TracerProvider provider = TracerProvider.createProvider();
        InstrumentationScope explicitScope = InstrumentationScopeFactory.create(
                "explicit-scope",
                "1.0.0",
                "https://explicit.schema",
                Collections.emptyList());
        provider.setCorrelationResolver(new TracerProvider.CorrelationResolver() {
            @Override
            public SpanContext resolveSpanContext() {
                return null;
            }

            @Override
            public InstrumentationScope resolveInstrumentationScope() {
                return InstrumentationScopeFactory.create(
                        "resolver-scope",
                        "1.0.0",
                        "https://resolver.schema",
                        Collections.emptyList());
            }
        });

        LogRecordFactory enrichedFactory = provider.enrichingLogRecordFactory(
                new LogRecordFactoryImpl(),
                explicitScope);
        LogRecord record = enrichedFactory.create();

        assertNotNull(record.getInstrumentationScope());
        assertSame(explicitScope, record.getInstrumentationScope());
    }
}
