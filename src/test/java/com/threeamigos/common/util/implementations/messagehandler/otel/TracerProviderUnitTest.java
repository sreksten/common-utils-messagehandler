package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.AbstractMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Filter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("TracerProvider unit tests")
@Tag("unit")
@Tag("messageHandler")
class TracerProviderUnitTest extends AbstractOtelValidatorLogTrapUnitTest {

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
    @DisplayName("filter-aware tracer should apply filter mapping when creating message handlers")
    void filterAwareTracerShouldApplyFilterMappingWhenCreatingMessageHandlers() {
        TracerProvider provider = TracerProvider.builder()
                .serviceName("orders")
                .build();
        Filter filter = record -> {
            SeverityNumber severity = record.getSeverityNumber();
            return severity == SeverityNumber.ERROR ? record : null;
        };

        Tracer tracer = provider.getTracer("orders-api", filter);
        MessageHandler handler = tracer.getInMemoryMessageHandler(filter);

        assertTrue(handler instanceof AbstractMessageHandler);
        AbstractMessageHandler abstractHandler = (AbstractMessageHandler) handler;
        assertTrue(!abstractHandler.isEnabled(SeverityNumber.INFO));
        assertTrue(abstractHandler.isEnabled(SeverityNumber.ERROR));
    }

    @Test
    @DisplayName("message handler created before span should still log active span trace and span ids")
    void messageHandlerCreatedBeforeSpanShouldStillLogActiveSpanTraceAndSpanIds() throws Exception {
        Path tempFile = Files.createTempFile("tracer-provider-correlation-", ".log");
        TracerProvider provider = TracerProvider.builder()
                .serviceName("orders")
                .defaultFilePath(tempFile.toAbsolutePath().toString())
                .build();
        assertNotNull(provider.getCorrelationResolver());
        Tracer tracer = provider.getTracer("orders-api", "1.0.0");

        MessageHandler handler = tracer.getFileMessageHandler(tempFile.toAbsolutePath().toString());
        Span span = tracer.createSpan("order.place");
        String traceId = span.getSpanContext().getTraceId();
        String spanId = span.getSpanContext().getSpanId();

        handler.info("hello");
        span.end();
        handler.close();

        String output = new String(Files.readAllBytes(tempFile), StandardCharsets.UTF_8);
        assertTrue(output.contains("\"traceId\":\"" + traceId + "\""));
        assertTrue(output.contains("\"spanId\":\"" + spanId + "\""));
    }

    @Test
    @DisplayName("file handler should use call-level file path when provided")
    void fileHandlerShouldUseCallLevelFilePathWhenProvided() throws Exception {
        Path providerDefault = Files.createTempFile("tracer-provider-default-", ".log");
        Files.deleteIfExists(providerDefault);
        Path customTarget = Files.createTempFile("tracer-provider-custom-", ".log");
        Files.deleteIfExists(customTarget);

        TracerProvider provider = TracerProvider.builder()
                .serviceName("orders")
                .defaultFilePath(providerDefault.toAbsolutePath().toString())
                .build();
        Tracer tracer = provider.getTracer("orders-api", "1.0.0");

        MessageHandler handler = tracer.getFileMessageHandler(customTarget.toAbsolutePath().toString());
        handler.info("custom-path-message");
        handler.close();

        assertTrue(Files.exists(customTarget));
        String customContent = new String(Files.readAllBytes(customTarget), StandardCharsets.UTF_8);
        assertTrue(customContent.contains("custom-path-message"));
        assertFalse(Files.exists(providerDefault));
    }

    @Test
    @DisplayName("builder should allow explicit resource and merge it with builder-level attributes")
    void builderShouldAllowExplicitResourceAndMergeWithBuilderLevelAttributes() {
        Resource explicitResource = ResourceFactory.create(
                "https://schema.explicit",
                null,
                Collections.singletonList(
                        KeyValueFactory.of("host.name", AnyValueFactory.ofString("host-a"))));

        TracerProvider provider = TracerProvider.builder()
                .resource(explicitResource)
                .serviceName("orders")
                .deploymentEnvironment("prod")
                .build();

        Resource result = provider.getDefaultResource();
        assertNotNull(result);
        List<String> attributeKeys = result.getAttributes().stream().map(KeyValue::getKey).collect(Collectors.toList());
        assertTrue(attributeKeys.contains("host.name"));
        assertTrue(attributeKeys.contains(OTelTags.SERVICE_NAME.getValue()));
        assertTrue(attributeKeys.contains(OTelTags.DEPLOYMENT_ENVIRONMENT_NAME.getValue()));
    }

    @Test
    @DisplayName("tracer convenience handlers should support optional per-call filter")
    void tracerConvenienceHandlersShouldSupportOptionalPerCallFilter() {
        TracerProvider provider = TracerProvider.builder()
                .serviceName("orders")
                .build();
        Tracer tracer = provider.getTracer("orders-api", "1.0.0");

        Filter allowOnlyErrors = record -> {
            SeverityNumber severity = record.getSeverityNumber();
            return severity == SeverityNumber.ERROR ? record : null;
        };

        MessageHandler handler = tracer.getConsoleMessageHandler(allowOnlyErrors);
        assertTrue(handler instanceof AbstractMessageHandler);
        AbstractMessageHandler abstractHandler = (AbstractMessageHandler) handler;
        assertTrue(!abstractHandler.isEnabled(SeverityNumber.INFO));
        assertTrue(abstractHandler.isEnabled(SeverityNumber.ERROR));
    }

    @Test
    @DisplayName("tracer convenience handlers should accept logger instances")
    void tracerConvenienceHandlersShouldAcceptLoggerInstances() {
        TracerProvider provider = TracerProvider.builder()
                .serviceName("orders")
                .build();
        Tracer tracer = provider.getTracer("orders-api", "1.0.0");

        assertDoesNotThrow(() -> {
            MessageHandler jul = tracer.getJULMessageHandler(Logger.getLogger("orders-jul"));
            jul.info("hello jul");

            MessageHandler log4j = tracer.getLog4JMessageHandler(
                    org.apache.logging.log4j.LogManager.getLogger("orders-log4j"));
            log4j.info("hello log4j");

            MessageHandler slf4j = tracer.getSLF4JMessageHandler(
                    org.slf4j.LoggerFactory.getLogger("orders-slf4j"));
            slf4j.info("hello slf4j");
        });
    }

    @Test
    @DisplayName("void convenience handler should return void handler")
    void voidConvenienceHandlerShouldReturnVoidHandler() {
        TracerProvider provider = TracerProvider.builder()
                .serviceName("orders")
                .build();
        Tracer tracer = provider.getTracer("orders-api", "1.0.0");

        MessageHandler handler = tracer.getVoidMessageHandler();
        assertTrue(handler instanceof com.threeamigos.common.util.implementations.messagehandler.VoidMessageHandler);
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
    @DisplayName("getTracer with InstrumentationScope and filter should resolve through filter-aware path")
    void getTracerWithInstrumentationScopeAndFilterShouldResolveThroughFilterAwarePath() {
        TracerProvider provider = TracerProvider.createProvider();
        InstrumentationScope scope = InstrumentationScopeFactory.create(
                "orders",
                "1.0.0",
                "https://schema",
                Collections.emptyList());
        Filter allowOnlyErrors = record -> record.getSeverityNumber() == SeverityNumber.ERROR ? record : null;

        Tracer fromScopeWithFilterA = provider.getTracer(scope, allowOnlyErrors);
        Tracer fromScopeWithFilterB = provider.getTracer(scope, allowOnlyErrors);
        Tracer fromScopeWithoutFilter = provider.getTracer(scope);

        assertNotNull(fromScopeWithFilterA);
        assertNotSame(fromScopeWithFilterA, fromScopeWithFilterB);
        assertNotSame(fromScopeWithFilterA, fromScopeWithoutFilter);
        assertEquals("orders", fromScopeWithFilterA.getInstrumentationScope().getName());
        assertEquals("1.0.0", fromScopeWithFilterA.getInstrumentationScope().getVersion());
        assertEquals("https://schema", fromScopeWithFilterA.getInstrumentationScope().getSchemaUrl());
    }

    @Test
    @DisplayName("getTracer with null InstrumentationScope and filter should still produce a tracer")
    void getTracerWithNullInstrumentationScopeAndFilterShouldStillProduceTracer() {
        TracerProvider provider = TracerProvider.createProvider();
        Filter allowAll = record -> record;

        Tracer tracer = provider.getTracer((InstrumentationScope) null, allowAll);

        assertNotNull(tracer);
        assertNotNull(tracer.getInstrumentationScope());
    }

    @Test
    @DisplayName("three-argument getTracer overload should route through filter-aware construction")
    void threeArgumentGetTracerOverloadShouldRouteThroughFilterAwareConstruction() {
        TracerProvider provider = TracerProvider.createProvider();
        Filter allowAll = new Filter() {
            @Override
            public LogRecord filter(final LogRecord logRecord) {
                return logRecord;
            }
        };

        Tracer first = provider.getTracer("orders", "1.0.0", allowAll);
        Tracer second = provider.getTracer("orders", "1.0.0", allowAll);

        assertNotNull(first);
        assertNotNull(second);
        assertNotSame(first, second);
    }

    @Test
    @DisplayName("getTracer with null InstrumentationScope should route to default field-based overload")
    void getTracerWithNullInstrumentationScopeShouldRouteToDefaultFieldBasedOverload() {
        TracerProvider provider = TracerProvider.createProvider();
        Tracer fromNullScope = provider.getTracer((InstrumentationScope) null);
        Tracer fromFields = provider.getTracer(null, null, null, null);

        assertNotNull(fromNullScope);
        assertSame(fromFields, fromNullScope);
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
        provider.setDefaultCommonAttributes(Collections.singletonList(
                KeyValueFactory.of("env", AnyValueFactory.ofString("test"))));

        assertNotNull(provider.getDefaultResource());
        assertEquals(1, provider.getDefaultCommonAttributes().size());
        assertEquals("env", provider.getDefaultCommonAttributes().get(0).getKey());

        provider.setDefaultSchemaUrl("   ");
        assertNull(provider.getDefaultSchemaUrl());
    }

    @Test
    @DisplayName("log record factory should apply provider defaults and resolver correlation")
    void logRecordFactoryShouldApplyProviderDefaultsAndResolverCorrelation() {
        TracerProvider provider = TracerProvider.createProvider();
        provider.setDefaultResource(ResourceFactory.create("https://provider.schema", null, null));
        provider.setDefaultCommonAttributes(Collections.singletonList(
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
        TracerProvider.CorrelationScope token = provider.attachCorrelation(resolverContext, resolverScope);

        LogRecord record;
        try {
            LogRecordFactory enrichedFactory = provider.getLogRecordFactory(null);
            record = enrichedFactory.create(SeverityNumber.INFO, "hello");
        } finally {
            token.close();
        }

        assertNotNull(record.getResource());
        assertNotNull(record.getInstrumentationScope());
        assertEquals("resolver-scope", record.getInstrumentationScope().getName());
        assertEquals(resolverContext.getTraceId(), record.getTraceId());
        assertEquals(resolverContext.getSpanId(), record.getSpanId());
        assertEquals(0x01, record.getTraceFlags());
        assertTrue(record.getAttributes().stream().map(kv -> kv.getKey()).collect(Collectors.toList()).contains("env"));
    }

    @Test
    @DisplayName("no-argument log record factory should delegate to scope-aware factory with null scope")
    void noArgumentLogRecordFactoryShouldDelegateToScopeAwareFactoryWithNullScope() {
        TracerProvider provider = TracerProvider.createProvider();
        LogRecordFactory noArg = provider.getLogRecordFactory();
        LogRecordFactory explicitNull = provider.getLogRecordFactory(null);

        LogRecord a = noArg.create();
        LogRecord b = explicitNull.create();
        assertNotNull(a);
        assertNotNull(b);
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
        TracerProvider.CorrelationScope token = provider.attachCorrelation(resolverContext, null);

        LogRecord record;
        try {
            LogRecordFactory enrichedFactory = provider.getLogRecordFactory(null, explicitContext);
            record = enrichedFactory.create();
        } finally {
            token.close();
        }

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
        TracerProvider.CorrelationScope token = provider.attachCorrelation(
                null,
                InstrumentationScopeFactory.create(
                        "resolver-scope",
                        "1.0.0",
                        "https://resolver.schema",
                        Collections.emptyList()));

        LogRecord record;
        try {
            LogRecordFactory enrichedFactory = provider.getLogRecordFactory(explicitScope);
            record = enrichedFactory.create();
        } finally {
            token.close();
        }

        assertNotNull(record.getInstrumentationScope());
        assertSame(explicitScope, record.getInstrumentationScope());
    }

    @Test
    @DisplayName("setters/getters should support null values and normalize blank schema")
    void settersAndGettersShouldSupportNullValuesAndNormalizeBlankSchema() {
        TracerProvider provider = TracerProvider.createProvider();

        assertNull(provider.getDefaultResource());
        assertNull(provider.getDefaultSchemaUrl());
        assertTrue(provider.getDefaultCommonAttributes().isEmpty());
        assertNotNull(provider.getCorrelationResolver());

        provider.setDefaultSchemaUrl("   ");
        assertNull(provider.getDefaultSchemaUrl());

        provider.setDefaultCommonAttributes(null);
        assertTrue(provider.getDefaultCommonAttributes().isEmpty());

        assertEquals("message-handler.log", provider.getDefaultFilePath());
        provider.setDefaultFilePath("   ");
        assertEquals("message-handler.log", provider.getDefaultFilePath());
    }

    @Test
    @DisplayName("provider attachCorrelation should set and restore context without resolver exposure")
    void providerAttachCorrelationShouldSetAndRestoreContextWithoutResolverExposure() {
        TracerProvider provider = TracerProvider.createProvider();
        CorrelationResolver resolver = provider.getCorrelationResolver();
        SpanContext spanContext = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());
        InstrumentationScope scope = InstrumentationScopeFactory.create(
                "orders",
                "1.0.0",
                "https://schema",
                Collections.emptyList());

        try (TracerProvider.CorrelationScope ignored = provider.attachCorrelation(spanContext, scope)) {
            assertSame(spanContext, resolver.resolveSpanContext());
            assertSame(scope, resolver.resolveInstrumentationScope());
        }

        assertNull(resolver.resolveSpanContext());
        assertNull(resolver.resolveInstrumentationScope());

        provider.attachCorrelation(spanContext).close();
        provider.clearCorrelation();
        assertNull(resolver.resolveSpanContext());
    }

    @Test
    @DisplayName("provider wrap and context-aware executor helpers should propagate context")
    void providerWrapAndContextAwareExecutorHelpersShouldPropagateContext() throws Exception {
        TracerProvider provider = TracerProvider.createProvider();
        CorrelationResolver resolver = provider.getCorrelationResolver();
        SpanContext mainSpan = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());
        resolver.setActiveSpanContext(mainSpan);

        ExecutorService raw = Executors.newSingleThreadExecutor();
        try {
            Executor contextAwareExecutor = provider.contextAwareExecutor(raw);
            AtomicReference<SpanContext> seenByExecute = new AtomicReference<SpanContext>();
            CountDownLatch latch = new CountDownLatch(1);
            contextAwareExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    seenByExecute.set(resolver.resolveSpanContext());
                    latch.countDown();
                }
            });
            assertTrue(latch.await(5, TimeUnit.SECONDS));

            ExecutorService contextAwareService = provider.contextAwareExecutorService(raw);
            Future<SpanContext> second = contextAwareService.submit(provider.wrap(new Callable<SpanContext>() {
                @Override
                public SpanContext call() {
                    return resolver.resolveSpanContext();
                }
            }));

            assertSame(mainSpan, seenByExecute.get());
            assertSame(mainSpan, second.get());
        } finally {
            raw.shutdownNow();
        }
    }

    @Test
    @DisplayName("provider wrap(Runnable) should propagate active span context")
    void providerWrapRunnableShouldPropagateActiveSpanContext() {
        TracerProvider provider = TracerProvider.createProvider();
        CorrelationResolver resolver = provider.getCorrelationResolver();
        SpanContext mainSpan = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());
        resolver.setActiveSpanContext(mainSpan);

        AtomicReference<SpanContext> seen = new AtomicReference<SpanContext>();
        Runnable wrapped = provider.wrap(new Runnable() {
            @Override
            public void run() {
                seen.set(resolver.resolveSpanContext());
            }
        });
        wrapped.run();

        assertSame(mainSpan, seen.get());
    }

    @Test
    @DisplayName("getTracer should support null attributes collection and normalize blank values")
    void getTracerShouldSupportNullAttributesCollectionAndNormalizeBlankValues() {
        TracerProvider provider = TracerProvider.createProvider();
        provider.setDefaultSchemaUrl("https://provider-schema");

        Tracer tracer = provider.getTracer("orders", "   ", "   ", null);

        assertNotNull(tracer);
        assertNotNull(tracer.getInstrumentationScope());
        assertEquals("orders", tracer.getInstrumentationScope().getName());
        assertNull(tracer.getInstrumentationScope().getVersion());
        assertEquals("https://provider-schema", tracer.getInstrumentationScope().getSchemaUrl());
    }

    @Test
    @DisplayName("getTracer should compute cache signatures for all AnyValue types")
    void getTracerShouldComputeCacheSignaturesForAllAnyValueTypes() {
        TracerProvider provider = TracerProvider.createProvider();

        Tracer empty = provider.getTracer(
                "orders",
                "1.0.0",
                "https://schema",
                Collections.singletonList(KeyValueFactory.of("k", AnyValueFactory.empty())));
        Tracer string = provider.getTracer(
                "orders",
                "1.0.0",
                "https://schema",
                Collections.singletonList(KeyValueFactory.of("k", AnyValueFactory.ofString("v"))));
        Tracer bool = provider.getTracer(
                "orders",
                "1.0.0",
                "https://schema",
                Collections.singletonList(KeyValueFactory.of("k", AnyValueFactory.ofBoolean(true))));
        Tracer integer = provider.getTracer(
                "orders",
                "1.0.0",
                "https://schema",
                Collections.singletonList(KeyValueFactory.of("k", AnyValueFactory.ofLong(7L))));
        Tracer decimal = provider.getTracer(
                "orders",
                "1.0.0",
                "https://schema",
                Collections.singletonList(KeyValueFactory.of("k", AnyValueFactory.ofDouble(1.25d))));
        Tracer bytes = provider.getTracer(
                "orders",
                "1.0.0",
                "https://schema",
                Collections.singletonList(KeyValueFactory.of("k", AnyValueFactory.ofBytes(new byte[]{1, 2, 3}))));
        Tracer array = provider.getTracer(
                "orders",
                "1.0.0",
                "https://schema",
                Collections.singletonList(KeyValueFactory.of("k", AnyValueFactory.ofArray(Collections.singletonList(AnyValueFactory.ofString("x"))))));
        Tracer kvlist = provider.getTracer(
                "orders",
                "1.0.0",
                "https://schema",
                Collections.singletonList(KeyValueFactory.of("k", AnyValueFactory.ofKvList(Collections.singletonList(
                        KeyValueFactory.of("inner", AnyValueFactory.ofString("value")))))));
        Tracer nullType = provider.getTracer(
                "orders",
                "1.0.0",
                "https://schema",
                Collections.singletonList(KeyValueFactory.of("k", new com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue() {
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
                })));

        assertNotSame(empty, string);
        assertNotSame(string, bool);
        assertNotSame(bool, integer);
        assertNotSame(integer, decimal);
        assertNotSame(decimal, bytes);
        assertNotSame(bytes, array);
        assertNotSame(array, kvlist);
        assertNotSame(kvlist, nullType);

        Tracer emptyAgain = provider.getTracer(
                "orders",
                "1.0.0",
                "https://schema",
                Collections.singletonList(KeyValueFactory.of("k", AnyValueFactory.empty())));
        assertSame(empty, emptyAgain);
    }

    private static final class FilterProbe {
    }

    @Test
    @DisplayName("TracerKey equals/hash should cover self and null comparisons")
    void tracerKeyEqualsHashShouldCoverSelfAndNullComparisons() throws Exception {
        Class<?> keyClass = Class.forName("com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider$TracerKey");
        Constructor<?> constructor = keyClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> type = parameterTypes[i];
            if (type == TracerProvider.class) {
                args[i] = TracerProvider.createProvider();
            } else if (type == String.class) {
                args[i] = "x";
            } else if (List.class.isAssignableFrom(type)) {
                args[i] = Collections.singletonList("k=v");
            } else {
                args[i] = null;
            }
        }
        Object key = constructor.newInstance(args);

        assertTrue(key.equals(key));
        assertFalse(key.equals(null));
        assertNotNull(key.hashCode());
    }

    @Test
    @DisplayName("anyValueSignature should fallback to default branch when switch map entry is missing")
    void anyValueSignatureShouldFallbackToDefaultBranchWhenSwitchMapEntryIsMissing() throws Exception {
        Method anyValueSignature = TracerProvider.class.getDeclaredMethod("anyValueSignature", AnyValue.class);
        anyValueSignature.setAccessible(true);
        assertEquals("EMPTY", anyValueSignature.invoke(null, new Object[]{null}));

        Class<?> switchClass = Class.forName("com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider$1");
        Field switchField = null;
        for (Field field : switchClass.getDeclaredFields()) {
            if (field.getName().startsWith("$SwitchMap$")) {
                switchField = field;
                break;
            }
        }
        assertNotNull(switchField);
        switchField.setAccessible(true);
        int[] switchMap = (int[]) switchField.get(null);
        int ordinal = AnyValue.Type.STRING.ordinal();
        int original = switchMap[ordinal];
        switchMap[ordinal] = 0;
        try {
            Object signature = anyValueSignature.invoke(null, AnyValueFactory.ofString("value"));
            assertEquals("STRING", signature);
        } finally {
            switchMap[ordinal] = original;
        }
    }

    @Test
    @DisplayName("TracerKey equals should evaluate deeper fields when instrumentation name matches")
    void tracerKeyEqualsShouldEvaluateDeeperFieldsWhenInstrumentationNameMatches() throws Exception {
        Class<?> keyClass = Class.forName("com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider$TracerKey");
        Constructor<?> constructor = keyClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] argsA = new Object[parameterTypes.length];
        Object[] argsB = new Object[parameterTypes.length];
        int stringIndex = 0;
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> type = parameterTypes[i];
            if (type == TracerProvider.class) {
                TracerProvider provider = TracerProvider.createProvider();
                argsA[i] = provider;
                argsB[i] = provider;
            } else if (type == String.class) {
                if (stringIndex == 0) {
                    argsA[i] = "orders";
                    argsB[i] = "orders";
                } else if (stringIndex == 1) {
                    argsA[i] = "1.0.0";
                    argsB[i] = "2.0.0";
                } else {
                    argsA[i] = "https://schema";
                    argsB[i] = "https://schema";
                }
                stringIndex++;
            } else if (List.class.isAssignableFrom(type)) {
                argsA[i] = Collections.singletonList("k=v");
                argsB[i] = Collections.singletonList("k=v");
            } else {
                argsA[i] = null;
                argsB[i] = null;
            }
        }

        Object keyA = constructor.newInstance(argsA);
        Object keyB = constructor.newInstance(argsB);
        assertFalse(keyA.equals(keyB));
    }

    @Test
    @DisplayName("TracerKey equals/hash should differ for mismatched instrumentation names")
    void tracerKeyEqualsHashShouldDifferForMismatchedInstrumentationNames() throws Exception {
        Class<?> keyClass = Class.forName("com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider$TracerKey");
        Constructor<?> constructor = keyClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] argsA = new Object[parameterTypes.length];
        Object[] argsB = new Object[parameterTypes.length];
        Object[] argsC = new Object[parameterTypes.length];
        int stringIndex = 0;
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> type = parameterTypes[i];
            if (List.class.isAssignableFrom(type)) {
                argsA[i] = Collections.singletonList("k=v");
                argsB[i] = Collections.singletonList("k=v");
                argsC[i] = Collections.singletonList("k=v");
            } else if (type == String.class) {
                if (stringIndex == 0) {
                    argsA[i] = "n";
                    argsB[i] = "n";
                    argsC[i] = "n2";
                } else if (stringIndex == 1) {
                    argsA[i] = "v";
                    argsB[i] = "v";
                    argsC[i] = "v";
                } else {
                    argsA[i] = "s";
                    argsB[i] = "s";
                    argsC[i] = "s";
                }
                stringIndex++;
            } else if (type == TracerProvider.class) {
                TracerProvider provider = TracerProvider.createProvider();
                argsA[i] = provider;
                argsB[i] = provider;
                argsC[i] = provider;
            } else {
                argsA[i] = null;
                argsB[i] = null;
                argsC[i] = null;
            }
        }

        Object a = constructor.newInstance(argsA);
        Object b = constructor.newInstance(argsB);
        Object c = constructor.newInstance(argsC);

        assertTrue(a.equals(b));
        assertFalse(a.equals(c));
        assertFalse(a.equals("not-a-key"));
        assertNotEquals(a.hashCode(), c.hashCode());
    }

    @Test
    @DisplayName("TracerKey equals should return false when schema URL or attribute signature differs")
    void tracerKeyEqualsShouldReturnFalseWhenSchemaUrlOrAttributeSignatureDiffers() throws Exception {
        Class<?> keyClass = Class.forName("com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider$TracerKey");
        Constructor<?> constructor = keyClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Class<?>[] parameterTypes = constructor.getParameterTypes();

        Object[] baseArgs = new Object[parameterTypes.length];
        Object[] schemaDiffArgs = new Object[parameterTypes.length];
        Object[] attributesDiffArgs = new Object[parameterTypes.length];
        int stringIndex = 0;
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> type = parameterTypes[i];
            if (List.class.isAssignableFrom(type)) {
                baseArgs[i] = Collections.singletonList("k=v");
                schemaDiffArgs[i] = Collections.singletonList("k=v");
                attributesDiffArgs[i] = Collections.singletonList("k2=v2");
            } else if (type == String.class) {
                if (stringIndex == 0) {
                    baseArgs[i] = "orders";
                    schemaDiffArgs[i] = "orders";
                    attributesDiffArgs[i] = "orders";
                } else if (stringIndex == 1) {
                    baseArgs[i] = "1.0.0";
                    schemaDiffArgs[i] = "1.0.0";
                    attributesDiffArgs[i] = "1.0.0";
                } else {
                    baseArgs[i] = "https://schema-a";
                    schemaDiffArgs[i] = "https://schema-b";
                    attributesDiffArgs[i] = "https://schema-a";
                }
                stringIndex++;
            } else {
                baseArgs[i] = null;
                schemaDiffArgs[i] = null;
                attributesDiffArgs[i] = null;
            }
        }

        Object base = constructor.newInstance(baseArgs);
        Object schemaDiff = constructor.newInstance(schemaDiffArgs);
        Object attributesDiff = constructor.newInstance(attributesDiffArgs);

        assertFalse(base.equals(schemaDiff));
        assertFalse(base.equals(attributesDiff));
    }
}
