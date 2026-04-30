package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.CorrelationResolver;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Meter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("MetricsProviderImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class MetricsProviderImplUnitTest extends AbstractOtelValidatorLogTrapUnitTest {

    @Test
    @DisplayName("getGlobal should return singleton provider")
    void getGlobalShouldReturnSingletonProvider() {
        MetricsProviderImpl first = MetricsProviderImpl.getGlobal();
        MetricsProviderImpl second = MetricsProviderImpl.getGlobal();

        assertNotNull(first);
        assertSame(first, second);
    }

    @Test
    @DisplayName("createProvider should return a distinct provider instance")
    void createProviderShouldReturnDistinctProviderInstance() {
        MetricsProviderImpl global = MetricsProviderImpl.getGlobal();
        MetricsProviderImpl created = MetricsProviderImpl.createProvider();

        assertNotNull(created);
        assertNotSame(global, created);
    }

    @Test
    @DisplayName("getMeter should cache meters by instrumentation scope identity")
    void getMeterShouldCacheByInstrumentationScopeIdentity() {
        MetricsProviderImpl provider = MetricsProviderImpl.createProvider();

        Meter first = provider.getMeter("orders", "1.0.0", "https://schema");
        Meter second = provider.getMeter("orders", "1.0.0", "https://schema");
        Meter third = provider.getMeter("orders", "1.1.0", "https://schema");

        assertSame(first, second);
        assertNotSame(first, third);
    }

    @Test
    @DisplayName("three-argument getMeter overload should resolve to empty attributes")
    void threeArgumentGetMeterOverloadShouldResolveToEmptyAttributes() {
        MetricsProviderImpl provider = MetricsProviderImpl.createProvider();

        Meter fromThreeArgs = provider.getMeter("orders", "1.0.0", "https://schema");
        Meter fromFourArgs = provider.getMeter("orders", "1.0.0", "https://schema", new com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue[0]);

        assertSame(fromFourArgs, fromThreeArgs);
    }

    @Test
    @DisplayName("default schema URL should be applied when schema argument is absent")
    void defaultSchemaUrlShouldBeAppliedWhenSchemaArgumentIsAbsent() {
        MetricsProviderImpl provider = MetricsProviderImpl.createProvider();
        provider.setDefaultSchemaUrl("https://provider-schema");

        Meter meter = provider.getMeter("orders", "1.0.0", null);

        assertNotNull(meter.getInstrumentationScope());
        assertEquals("https://provider-schema", meter.getInstrumentationScope().getSchemaUrl());
    }

    @Test
    @DisplayName("correlation resolver should be configurable")
    void correlationResolverShouldBeConfigurable() {
        MetricsProviderImpl provider = MetricsProviderImpl.createProvider();
        CorrelationResolver resolver = new SharedCorrelationResolver();

        assertNull(provider.getCorrelationResolver());
        provider.setCorrelationResolver(resolver);
        assertSame(resolver, provider.getCorrelationResolver());
    }

    @Test
    @DisplayName("getMeter should return non-null meter for null instrumentation name")
    void getMeterShouldReturnNonNullMeterForNullInstrumentationName() {
        MetricsProviderImpl provider = MetricsProviderImpl.createProvider();

        Meter meter = provider.getMeter(null, "1.0.0", null);
        assertNotNull(meter);
        assertNotNull(meter.getInstrumentationScope());
        assertNull(meter.getInstrumentationScope().getName());
    }

    @Test
    @DisplayName("blank schema should normalize to null and null attributes should be accepted")
    void blankSchemaShouldNormalizeToNullAndNullAttributesShouldBeAccepted() {
        MetricsProviderImpl provider = MetricsProviderImpl.createProvider();
        provider.setDefaultSchemaUrl("   ");
        assertNull(provider.getDefaultSchemaUrl());

        Meter meter = provider.getMeter("orders", "1.0.0", "   ", (com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue[]) null);
        assertNotNull(meter);
        assertNotNull(meter.getInstrumentationScope());
        assertNull(meter.getInstrumentationScope().getSchemaUrl());
    }

    @Test
    @DisplayName("getMeter should compute cache signatures for all AnyValue types")
    void getMeterShouldComputeCacheSignaturesForAllAnyValueTypes() {
        MetricsProviderImpl provider = MetricsProviderImpl.createProvider();

        Meter empty = provider.getMeter(
                "orders",
                "1.0.0",
                "https://schema",
                KeyValueFactory.of("k", AnyValueFactory.empty()));
        Meter string = provider.getMeter(
                "orders",
                "1.0.0",
                "https://schema",
                KeyValueFactory.of("k", AnyValueFactory.ofString("v")));
        Meter bool = provider.getMeter(
                "orders",
                "1.0.0",
                "https://schema",
                KeyValueFactory.of("k", AnyValueFactory.ofBoolean(true)));
        Meter integer = provider.getMeter(
                "orders",
                "1.0.0",
                "https://schema",
                KeyValueFactory.of("k", AnyValueFactory.ofLong(7L)));
        Meter decimal = provider.getMeter(
                "orders",
                "1.0.0",
                "https://schema",
                KeyValueFactory.of("k", AnyValueFactory.ofDouble(1.25d)));
        Meter bytes = provider.getMeter(
                "orders",
                "1.0.0",
                "https://schema",
                KeyValueFactory.of("k", AnyValueFactory.ofBytes(new byte[]{1, 2, 3})));
        Meter array = provider.getMeter(
                "orders",
                "1.0.0",
                "https://schema",
                KeyValueFactory.of("k", AnyValueFactory.ofArray(Collections.singletonList(AnyValueFactory.ofString("x")))));
        Meter kvlist = provider.getMeter(
                "orders",
                "1.0.0",
                "https://schema",
                KeyValueFactory.of("k", AnyValueFactory.ofKvList(Collections.singletonList(
                        KeyValueFactory.of("inner", AnyValueFactory.ofString("value"))))));
        Meter nullType = provider.getMeter(
                "orders",
                "1.0.0",
                "https://schema",
                KeyValueFactory.of("k", new com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue() {
                    @Override
                    public Type getType() {
                        return null;
                    }

                    @Override
                    public String asString() {
                        return null;
                    }

                    @Override
                    public boolean asBoolean() {
                        return false;
                    }

                    @Override
                    public long asLong() {
                        return 0;
                    }

                    @Override
                    public double asDouble() {
                        return 0;
                    }

                    @Override
                    public List<com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue> asArray() {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue> asKvList() {
                        return Collections.emptyList();
                    }

                    @Override
                    public byte[] asBytes() {
                        return new byte[0];
                    }
                }));

        assertNotSame(empty, string);
        assertNotSame(string, bool);
        assertNotSame(bool, integer);
        assertNotSame(integer, decimal);
        assertNotSame(decimal, bytes);
        assertNotSame(bytes, array);
        assertNotSame(array, kvlist);
        assertNotSame(kvlist, nullType);

        Meter emptyAgain = provider.getMeter(
                "orders",
                "1.0.0",
                "https://schema",
                KeyValueFactory.of("k", AnyValueFactory.empty()));
        assertSame(empty, emptyAgain);
    }
}
