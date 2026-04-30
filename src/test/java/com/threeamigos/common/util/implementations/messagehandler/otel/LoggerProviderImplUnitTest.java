package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Logger;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LoggerProviderImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class LoggerProviderImplUnitTest extends AbstractOtelValidatorLogTrapUnitTest {

    @AfterEach
    void cleanupLenient() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(false);
    }

    @Test
    @DisplayName("getGlobal should return singleton provider")
    void getGlobalShouldReturnSingletonProvider() {
        LoggerProviderImpl first = LoggerProviderImpl.getGlobal();
        LoggerProviderImpl second = LoggerProviderImpl.getGlobal();

        assertNotNull(first);
        assertSame(first, second);
    }

    @Test
    @DisplayName("createProvider should return a distinct provider instance")
    void createProviderShouldReturnDistinctProviderInstance() {
        LoggerProviderImpl global = LoggerProviderImpl.getGlobal();
        LoggerProviderImpl created = LoggerProviderImpl.createProvider();

        assertNotNull(created);
        assertNotSame(global, created);
    }

    @Test
    @DisplayName("getLogger should cache loggers by instrumentation scope identity")
    void getLoggerShouldCacheByInstrumentationScopeIdentity() {
        LoggerProviderImpl provider = LoggerProviderImpl.createProvider();

        Logger first = provider.getLogger("orders", "1.0.0", "https://schema");
        Logger second = provider.getLogger("orders", "1.0.0", "https://schema");
        Logger third = provider.getLogger("orders", "1.1.0", "https://schema");

        assertSame(first, second);
        assertNotSame(first, third);
    }

    @Test
    @DisplayName("default schema URL should be applied when schema argument is absent")
    void defaultSchemaUrlShouldBeAppliedWhenSchemaArgumentIsAbsent() {
        LoggerProviderImpl provider = LoggerProviderImpl.createProvider();
        provider.setDefaultSchemaUrl("https://provider-schema");
        List<LogRecord> emitted = new ArrayList<>();
        provider.setLogRecordConsumer(emitted::add);

        Logger logger = provider.getLogger("orders", "1.0.0", null);
        logger.emit(new LogRecordImpl());

        assertEquals(1, emitted.size());
        LogRecord record = emitted.get(0);
        assertNotNull(record.getInstrumentationScope());
        assertEquals("https://provider-schema", record.getInstrumentationScope().getSchemaUrl());
    }

    @Test
    @DisplayName("emit should enrich with provider defaults and shared resolver correlation")
    void emitShouldEnrichWithProviderDefaultsAndSharedResolverCorrelation() {
        LoggerProviderImpl provider = LoggerProviderImpl.createProvider();
        provider.setDefaultResource(ResourceFactory.create("https://resource.schema", null, null));
        provider.setDefaultCommonAttributes(Collections.singletonList(
                KeyValueFactory.of("env", AnyValueFactory.ofString("test"))));
        List<LogRecord> emitted = new ArrayList<>();
        provider.setLogRecordConsumer(emitted::add);

        SharedCorrelationResolver resolver = new SharedCorrelationResolver();
        provider.setCorrelationResolver(resolver);
        SpanContext spanContext = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());

        Logger logger = provider.getLogger("checkout-api", "1.0.0", null);
        SharedCorrelationResolver.ScopeToken token = resolver.attach(spanContext, null);
        try {
            logger.emit(new LogRecordImpl());
        } finally {
            token.close();
        }

        assertEquals(1, emitted.size());
        LogRecord record = emitted.get(0);
        assertNotNull(record.getResource());
        assertNotNull(record.getInstrumentationScope());
        assertEquals("checkout-api", record.getInstrumentationScope().getName());
        assertEquals(spanContext.getTraceId(), record.getTraceId());
        assertEquals(spanContext.getSpanId(), record.getSpanId());
        assertEquals(0x01, record.getTraceFlags());
        assertTrue(record.getAttributes().stream().anyMatch(kv -> "env".equals(kv.getKey())));
    }

    @Test
    @DisplayName("logger should honor provider enabled flag")
    void loggerShouldHonorProviderEnabledFlag() {
        LoggerProviderImpl provider = LoggerProviderImpl.createProvider();
        List<LogRecord> emitted = new ArrayList<>();
        provider.setLogRecordConsumer(emitted::add);
        Logger logger = provider.getLogger("orders", "1.0.0", null);

        provider.setEnabled(false);
        logger.emit(new LogRecordImpl());
        assertTrue(emitted.isEmpty());

        provider.setEnabled(true);
        logger.emit(new LogRecordImpl());
        assertEquals(1, emitted.size());
    }

    @Test
    @DisplayName("strict mode should throw when emit receives null log record")
    void strictModeShouldThrowWhenEmitReceivesNullLogRecord() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(false);
        LoggerProviderImpl provider = LoggerProviderImpl.createProvider();
        Logger logger = provider.getLogger("orders", "1.0.0", null);

        assertThrows(IllegalArgumentException.class, () -> logger.emit(null));
    }

    @Test
    @DisplayName("lenient mode should continue when emit receives null log record")
    void lenientModeShouldContinueWhenEmitReceivesNullLogRecord() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        LoggerProviderImpl provider = LoggerProviderImpl.createProvider();
        List<LogRecord> emitted = new ArrayList<>();
        provider.setLogRecordConsumer(emitted::add);
        Logger logger = provider.getLogger("orders", "1.0.0", null);

        assertDoesNotThrow(() -> logger.emit(null));
        assertTrue(emitted.isEmpty());
    }

    @Test
    @DisplayName("emit should continue when consumer throws")
    void emitShouldContinueWhenConsumerThrows() {
        LoggerProviderImpl provider = LoggerProviderImpl.createProvider();
        provider.setLogRecordConsumer(record -> {
            throw new RuntimeException("boom");
        });
        Logger logger = provider.getLogger("orders", "1.0.0", null);

        assertDoesNotThrow(() -> logger.emit(new LogRecordImpl()));
    }

    @Test
    @DisplayName("provider setters/getters should support nulls and default no-op consumer")
    void providerSettersAndGettersShouldSupportNullsAndDefaultNoOpConsumer() {
        LoggerProviderImpl provider = LoggerProviderImpl.createProvider();

        assertNull(provider.getDefaultResource());
        assertNull(provider.getDefaultSchemaUrl());
        assertTrue(provider.getDefaultCommonAttributes().isEmpty());
        assertNull(provider.getCorrelationResolver());

        provider.getLogRecordConsumer().accept(new LogRecordImpl());
        provider.setLogRecordConsumer(null);
        provider.getLogRecordConsumer().accept(new LogRecordImpl());

        provider.setDefaultSchemaUrl("   ");
        assertNull(provider.getDefaultSchemaUrl());
    }

    @Test
    @DisplayName("getLogger should support null attributes array and normalize blank schema/version")
    void getLoggerShouldSupportNullAttributesArrayAndNormalizeBlankSchemaVersion() {
        LoggerProviderImpl provider = LoggerProviderImpl.createProvider();
        provider.setDefaultSchemaUrl("https://provider-schema");
        List<LogRecord> emitted = new ArrayList<>();
        provider.setLogRecordConsumer(emitted::add);

        Logger logger = provider.getLogger("orders", "   ", "   ", (com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue[]) null);
        logger.emit(new LogRecordImpl());

        assertEquals(1, emitted.size());
        LogRecord record = emitted.get(0);
        assertNotNull(record.getInstrumentationScope());
        assertEquals("orders", record.getInstrumentationScope().getName());
        assertNull(record.getInstrumentationScope().getVersion());
        assertEquals("https://provider-schema", record.getInstrumentationScope().getSchemaUrl());
    }

    @Test
    @DisplayName("getLogger should compute cache signatures for all AnyValue types")
    void getLoggerShouldComputeCacheSignaturesForAllAnyValueTypes() {
        LoggerProviderImpl provider = LoggerProviderImpl.createProvider();

        Logger empty = provider.getLogger(
                "orders",
                "1.0.0",
                "https://schema",
                KeyValueFactory.of("k", AnyValueFactory.empty()));
        Logger string = provider.getLogger(
                "orders",
                "1.0.0",
                "https://schema",
                KeyValueFactory.of("k", AnyValueFactory.ofString("v")));
        Logger bool = provider.getLogger(
                "orders",
                "1.0.0",
                "https://schema",
                KeyValueFactory.of("k", AnyValueFactory.ofBoolean(true)));
        Logger integer = provider.getLogger(
                "orders",
                "1.0.0",
                "https://schema",
                KeyValueFactory.of("k", AnyValueFactory.ofLong(7L)));
        Logger decimal = provider.getLogger(
                "orders",
                "1.0.0",
                "https://schema",
                KeyValueFactory.of("k", AnyValueFactory.ofDouble(1.25d)));
        Logger bytes = provider.getLogger(
                "orders",
                "1.0.0",
                "https://schema",
                KeyValueFactory.of("k", AnyValueFactory.ofBytes(new byte[]{1, 2, 3})));
        Logger array = provider.getLogger(
                "orders",
                "1.0.0",
                "https://schema",
                KeyValueFactory.of("k", AnyValueFactory.ofArray(Collections.singletonList(AnyValueFactory.ofString("x")))));
        Logger kvlist = provider.getLogger(
                "orders",
                "1.0.0",
                "https://schema",
                KeyValueFactory.of("k", AnyValueFactory.ofKvList(Collections.singletonList(
                        KeyValueFactory.of("inner", AnyValueFactory.ofString("value"))))));
        Logger nullType = provider.getLogger(
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

        Logger emptyAgain = provider.getLogger(
                "orders",
                "1.0.0",
                "https://schema",
                KeyValueFactory.of("k", AnyValueFactory.empty()));
        assertSame(empty, emptyAgain);
    }
}
