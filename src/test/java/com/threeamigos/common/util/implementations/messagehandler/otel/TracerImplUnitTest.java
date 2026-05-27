package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.filters.FilterByClassName;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Filter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.StatusCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("TracerImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class TracerImplUnitTest extends AbstractOtelValidatorLogTrapUnitTest {

    private static final boolean ORIGINAL_LENIENT = OpenTelemetryAttributeValidator.isLenientMode();

    @BeforeEach
    void enforceStrictModeByDefault() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(false);
    }

    @AfterEach
    void restoreLenientMode() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(ORIGINAL_LENIENT);
    }

    @Test
    @DisplayName("constructor should preserve provided instrumentation name in scope")
    void constructorShouldPreserveProvidedInstrumentationName() {
        TracerImpl sut = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList());
        assertNotNull(sut.getInstrumentationScope());
        assertEquals("orders", sut.getInstrumentationScope().getName());
    }

    @Test
    @DisplayName("constructor should normalize null instrumentation name to absent scope name")
    void constructorShouldNormalizeNullInstrumentationNameToEmpty() {
        TracerImpl sut = new TracerImpl(null, null, null, null);
        assertNotNull(sut.getInstrumentationScope());
        assertNull(sut.getInstrumentationScope().getName());
    }

    @Test
    @DisplayName("constructor should normalize blank instrumentation name to absent scope name")
    void constructorShouldNormalizeBlankInstrumentationNameToEmpty() {
        TracerImpl sut = new TracerImpl("   ", "1.0.0", "schema", Collections.emptyList());
        assertNotNull(sut.getInstrumentationScope());
        assertNull(sut.getInstrumentationScope().getName());
    }

    @Test
    @DisplayName("constructor should copy attributes defensively")
    @SuppressWarnings({"rawtypes"})
    void constructorShouldCopyAttributesDefensively() {
        ArrayList<com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue> sourceAttributes = new ArrayList<>();
        sourceAttributes.add(new KeyValueImpl("k1", AnyValueFactory.ofString("v1")));

        TracerImpl sut = new TracerImpl("orders", "1.0.0", "schema", sourceAttributes);
        assertNotNull(sut.getInstrumentationScope());
        assertNotSame(sourceAttributes, sut.getInstrumentationScope().getAttributes());
        assertEquals(1, sut.getInstrumentationScope().getAttributes().size());

        sourceAttributes.add(new KeyValueImpl("k2", AnyValueFactory.ofString("v2")));
        assertEquals(1, sut.getInstrumentationScope().getAttributes().size());

        assertThrows(UnsupportedOperationException.class,
                () -> ((java.util.List) sut.getInstrumentationScope().getAttributes())
                        .add(new KeyValueImpl("k3", AnyValueFactory.ofString("v3"))));
    }

    @Test
    @DisplayName("isEnabled should reflect tracer mode")
    void isEnabledShouldReflectTracerMode() {
        TracerImpl enabled = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList());
        TracerImpl disabled = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList(), false);

        assertTrue(enabled.isEnabled());
        assertFalse(disabled.isEnabled());
    }

    @Test
    @DisplayName("createSpan should reject null names and produce a recording span")
    void createSpanShouldRejectNullNamesAndProduceRecordingSpan() {
        TracerImpl sut = new TracerImpl("orders", null, null, null);

        assertThrows(IllegalArgumentException.class, () -> sut.createSpan(null));

        Span span = sut.createSpan("span-name");
        assertNotNull(span);
        assertNotNull(span.getContext());
        assertNotNull(span.getInstrumentationScope());
        assertEquals("orders", span.getInstrumentationScope().getName());
        assertTrue(span.isRecording());
        assertDoesNotThrow(() -> {
            span.addAttribute("k", AnyValueFactory.ofString("v"));
            span.addEvent("event-name");
            span.addEvent("event-name-2", Arrays.asList(
                    new KeyValueImpl("attr", AnyValueFactory.ofString("v2"))), Instant.now());
            span.addLink(new SpanContextImpl(
                    "5b8efff798038103d269b633813fc60c",
                    "eee19b7ec3c1b174",
                    (byte) 0x00,
                    false,
                    new TraceStateImpl()));
            span.setStatus(StatusCode.ERROR, "boom");
            span.recordException(new IllegalStateException("boom"));
            span.updateName("updated-name");
            span.end();
        });
        assertFalse(span.isRecording());
    }

    @Test
    @DisplayName("tracer should expose effective instrumentation scope")
    void tracerShouldExposeEffectiveInstrumentationScope() {
        TracerImpl tracer = new TracerImpl("orders", "1.0.0", "https://schema", Collections.emptyList());
        assertNotNull(tracer.getInstrumentationScope());
        assertEquals("orders", tracer.getInstrumentationScope().getName());
        assertEquals("1.0.0", tracer.getInstrumentationScope().getVersion());
        assertEquals("https://schema", tracer.getInstrumentationScope().getSchemaUrl());

        TracerImpl nullNameTracer = new TracerImpl(null, "1.0.0", null, null);
        assertNotNull(nullNameTracer.getInstrumentationScope());
        assertNull(nullNameTracer.getInstrumentationScope().getName());
        assertEquals("1.0.0", nullNameTracer.getInstrumentationScope().getVersion());
        assertNull(nullNameTracer.getInstrumentationScope().getSchemaUrl());
    }

    @Test
    @DisplayName("tracer should expose tracer-aware log record factory")
    void tracerShouldExposeTracerAwareLogRecordFactory() {
        TracerProvider provider = TracerProvider.createProvider();
        TracerImpl tracer = (TracerImpl) provider.getTracer("orders", "1.0.0");

        LogRecordFactory factory = tracer.getLogRecordFactory();
        LogRecord record = factory.create();

        assertNotNull(factory);
        assertNotNull(record.getInstrumentationScope());
        assertEquals("orders", record.getInstrumentationScope().getName());
    }

    @Test
    @DisplayName("disabled tracer should return non-recording invalid span")
    void disabledTracerShouldReturnNonRecordingInvalidSpan() {
        TracerImpl disabled = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList(), false);

        assertThrows(IllegalArgumentException.class, () -> disabled.createSpan(null));

        Span span = disabled.createSpan("disabled-span");
        assertNotNull(span);
        assertFalse(span.isRecording());
        assertNotNull(span.getSpanContext());
        assertFalse(span.getSpanContext().isValid());
    }

    @Test
    @DisplayName("createSpan with valid parent should keep traceId and trace-flags from parent")
    void createSpanWithValidParentShouldKeepTraceIdAndFlagsFromParent() {
        TracerImpl tracer = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList(), true);
        SpanContextImpl parent = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x03,
                true,
                new TraceStateImpl().set("vendor", AnyValueFactory.ofString("value")));

        Span child = tracer.createSpan("child-span", parent);

        assertNotNull(child);
        assertTrue(child.isRecording());
        assertEquals(parent.getTraceId(), child.getSpanContext().getTraceId());
        assertNotEquals(parent.getSpanId(), child.getSpanContext().getSpanId());
        assertEquals(parent.getTraceFlags(), child.getSpanContext().getTraceFlags());
        assertTrue(child.getSpanContext().isSampled());
        assertTrue(child.getSpanContext().isRandom());
        assertFalse(child.getSpanContext().isRemote());
        assertEquals(1, child.getSpanContext().getTraceState().getValues().size());
        assertEquals("vendor", child.getSpanContext().getTraceState().getValues().iterator().next().getKey());
    }

    @Test
    @DisplayName("createSpan with invalid parent should start a new root span")
    void createSpanWithInvalidParentShouldStartNewRootSpan() {
        TracerImpl tracer = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList(), true);
        SpanContextImpl invalidParent = new SpanContextImpl();

        Span child = tracer.createSpan("rooted-span", invalidParent);

        assertNotNull(child);
        assertTrue(child.isRecording());
        assertTrue(child.getSpanContext().isValid());
        assertNotEquals(invalidParent.getTraceId(), child.getSpanContext().getTraceId());
        assertEquals((byte) 0x00, child.getSpanContext().getTraceFlags());
        assertFalse(child.getSpanContext().isSampled());
        assertFalse(child.getSpanContext().isRandom());
    }

    @Test
    @DisplayName("detached tracer convenience handlers should return concrete handlers")
    void detachedTracerConvenienceHandlersShouldReturnConcreteHandlers() {
        TracerImpl detached = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList());
        FilterByClassName filter = new FilterByClassName();

        MessageHandler console = detached.getConsoleMessageHandler(filter);
        MessageHandler fileFromString = detached.getFileMessageHandler("x.log", filter);
        MessageHandler fileFromFile = detached.getFileMessageHandler(new java.io.File("x.log"), filter);
        MessageHandler inMemory = detached.getInMemoryMessageHandler(filter);
        MessageHandler jul = detached.getJULMessageHandler(java.util.logging.Logger.getLogger("detached"), filter);
        MessageHandler log4j = detached.getLog4JMessageHandler(
                org.apache.logging.log4j.LogManager.getLogger("detached"), filter);
        MessageHandler slf4j = detached.getSLF4JMessageHandler(
                org.slf4j.LoggerFactory.getLogger("detached"), filter);
        MessageHandler swing = detached.getSwingMessageHandler(filter);
        MessageHandler jaeger = detached.getJaegerMessageHandler("http://localhost:4318/v1/logs", filter);
        MessageHandler grafana = detached.getGrafanaMessageHandler("http://localhost:3100/loki/api/v1/push", filter);
        MessageHandler voidHandler = detached.getVoidMessageHandler();

        assertTrue(console instanceof com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler);
        assertTrue(fileFromString instanceof com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler);
        assertTrue(fileFromFile instanceof com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler);
        assertTrue(inMemory instanceof com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler);
        assertTrue(jul instanceof com.threeamigos.common.util.implementations.messagehandler.JULMessageHandler);
        assertTrue(log4j instanceof com.threeamigos.common.util.implementations.messagehandler.Log4JMessageHandler);
        assertTrue(slf4j instanceof com.threeamigos.common.util.implementations.messagehandler.SLF4JMessageHandler);
        assertTrue(swing instanceof com.threeamigos.common.util.implementations.messagehandler.SwingMessageHandler);
        assertTrue(jaeger instanceof com.threeamigos.common.util.implementations.messagehandler.JaegerMessageHandler);
        assertTrue(grafana instanceof com.threeamigos.common.util.implementations.messagehandler.GrafanaMessageHandler);
        assertTrue(voidHandler instanceof com.threeamigos.common.util.implementations.messagehandler.VoidMessageHandler);

        MessageHandler fileFromFileNoFilter = detached.getFileMessageHandler(new java.io.File("x.log"));
        assertTrue(fileFromFileNoFilter instanceof com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler);

        jaeger.close();
        grafana.close();
        fileFromString.close();
        fileFromFile.close();
        fileFromFileNoFilter.close();
    }

    @Test
    @DisplayName("applyFilterLevels should enrich probes with source class attributes when provided")
    void applyFilterLevelsShouldEnrichProbesWithSourceClassAttributesWhenProvided() throws Exception {
        TracerImpl tracer = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList());
        InMemoryMessageHandler handler = new InMemoryMessageHandler();
        Method applyFilterLevels = TracerImpl.class.getDeclaredMethod(
                "applyFilterLevels",
                MessageHandler.class,
                Class.class,
                Filter.class);
        applyFilterLevels.setAccessible(true);

        Filter namespaceRequiredFilter = record -> {
            List<KeyValue> attributes = record.getAttributes();
            if (attributes == null) {
                return null;
            }
            for (KeyValue attribute : attributes) {
                if ("code.namespace".equals(attribute.getKey())
                        && attribute.getValue() != null
                        && attribute.getValue().asString() != null
                        && attribute.getValue().asString().equals(TracerImplUnitTest.class.getName())) {
                    return record;
                }
            }
            return null;
        };

        applyFilterLevels.invoke(tracer, handler, TracerImplUnitTest.class, namespaceRequiredFilter);

        assertTrue(handler.isInfoEnabled());
        assertTrue(handler.isDebugEnabled());
        assertTrue(handler.isTraceEnabled());
    }

    @Test
    @DisplayName("applyFilterLevels should support non-LogRecordImpl probes without namespace enrichment")
    void applyFilterLevelsShouldSupportNonLogRecordImplProbesWithoutNamespaceEnrichment() throws Exception {
        TracerImpl tracer = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList()) {
            @Override
            LogRecord createFilterProbe(final SeverityNumber severityNumber) {
                return mock(LogRecord.class);
            }
        };
        InMemoryMessageHandler handler = new InMemoryMessageHandler();
        Method applyFilterLevels = TracerImpl.class.getDeclaredMethod(
                "applyFilterLevels",
                MessageHandler.class,
                Class.class,
                Filter.class);
        applyFilterLevels.setAccessible(true);

        applyFilterLevels.invoke(tracer, handler, TracerImplUnitTest.class, (Filter) record -> record);

        assertTrue(handler.isInfoEnabled());
        assertTrue(handler.isDebugEnabled());
        assertTrue(handler.isTraceEnabled());
    }

    @Test
    @DisplayName("applyFilterLevels should no-op for handlers outside AbstractMessageHandler hierarchy")
    void applyFilterLevelsShouldNoOpForHandlersOutsideAbstractMessageHandlerHierarchy() throws Exception {
        TracerImpl tracer = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList());
        MessageHandler nonAbstractHandler = mock(MessageHandler.class);
        Method applyFilterLevels = TracerImpl.class.getDeclaredMethod(
                "applyFilterLevels",
                MessageHandler.class,
                Class.class,
                Filter.class);
        applyFilterLevels.setAccessible(true);

        assertDoesNotThrow(() -> applyFilterLevels.invoke(
                tracer,
                nonAbstractHandler,
                TracerImplUnitTest.class,
                (Filter) record -> record));
    }

    @Test
    @DisplayName("detached tracer logger overloads without filter should be non-blocking and return concrete handlers")
    void detachedTracerLoggerOverloadsWithoutFilterShouldBeNonBlockingAndReturnConcreteHandlers() {
        TracerImpl detached = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList());

        MessageHandler jul = detached.getJULMessageHandler(java.util.logging.Logger.getLogger("detached-jul"));
        MessageHandler log4j = detached.getLog4JMessageHandler(
                org.apache.logging.log4j.LogManager.getLogger("detached-log4j"));
        MessageHandler slf4j = detached.getSLF4JMessageHandler(org.slf4j.LoggerFactory.getLogger("detached-slf4j"));

        assertDoesNotThrow(() -> {
            jul.info("x");
            log4j.info("x");
            slf4j.info("x");
        });

        assertTrue(jul instanceof com.threeamigos.common.util.implementations.messagehandler.JULMessageHandler);
        assertTrue(log4j instanceof com.threeamigos.common.util.implementations.messagehandler.Log4JMessageHandler);
        assertTrue(slf4j instanceof com.threeamigos.common.util.implementations.messagehandler.SLF4JMessageHandler);
    }

    @Test
    @DisplayName("attached tracer convenience handlers should resolve explicit handler implementations")
    void attachedTracerConvenienceHandlersShouldResolveExplicitImplementations() {
        TracerProvider provider = TracerProvider.builder()
                .serviceName("orders")
                .build();
        TracerImpl attached = (TracerImpl) provider.getTracer("orders-api", "1.0.0");

        MessageHandler console = attached.getConsoleMessageHandler();
        MessageHandler file = attached.getFileMessageHandler("target/tracer-impl-test.log");
        MessageHandler inMemory = attached.getInMemoryMessageHandler();
        MessageHandler swing = attached.getSwingMessageHandler();
        MessageHandler jaeger = attached.getJaegerMessageHandler("http://localhost:4318/v1/logs");
        MessageHandler grafana = attached.getGrafanaMessageHandler("http://localhost:3100/loki/api/v1/push");
        MessageHandler voidHandler = attached.getVoidMessageHandler();

        assertTrue(console instanceof com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler);
        assertTrue(file instanceof com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler);
        assertTrue(inMemory instanceof com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler);
        assertTrue(swing instanceof com.threeamigos.common.util.implementations.messagehandler.SwingMessageHandler);
        assertTrue(jaeger instanceof com.threeamigos.common.util.implementations.messagehandler.JaegerMessageHandler);
        assertTrue(grafana instanceof com.threeamigos.common.util.implementations.messagehandler.GrafanaMessageHandler);
        assertTrue(voidHandler instanceof com.threeamigos.common.util.implementations.messagehandler.VoidMessageHandler);
        jaeger.close();
        grafana.close();
        file.close();
    }

    @Test
    @DisplayName("tracer should expose full connection configuration for jaeger and grafana handlers")
    void tracerShouldExposeFullConnectionConfigurationForJaegerAndGrafanaHandlers() {
        TracerProvider provider = TracerProvider.builder()
                .serviceName("orders")
                .build();
        TracerImpl tracer = (TracerImpl) provider.getTracer("orders-api", "1.0.0");
        FilterByClassName filter = new FilterByClassName();

        MessageHandler jaeger = tracer.getJaegerMessageHandler(
                "http://localhost:4318/v1/logs",
                "jaeger-user",
                "jaeger-pass",
                "jaeger-token",
                5_000,
                5_000,
                Collections.singletonMap("X-Jaeger", "enabled"),
                true,
                16,
                true,
                filter);

        MessageHandler grafana = tracer.getGrafanaMessageHandler(
                "http://localhost:3100/loki/api/v1/push",
                "grafana-user",
                "grafana-pass",
                "grafana-token",
                5_000,
                5_000,
                Collections.singletonMap("X-Grafana", "enabled"),
                true,
                16,
                true,
                filter);

        assertTrue(jaeger instanceof com.threeamigos.common.util.implementations.messagehandler.JaegerMessageHandler);
        assertTrue(grafana instanceof com.threeamigos.common.util.implementations.messagehandler.GrafanaMessageHandler);

        jaeger.close();
        grafana.close();
    }

    @Test
    @DisplayName("tracer should expose basic-auth and no-filter full-config overloads")
    void tracerShouldExposeBasicAuthAndNoFilterFullConfigOverloads() {
        TracerProvider provider = TracerProvider.builder()
                .serviceName("orders")
                .build();
        TracerImpl tracer = (TracerImpl) provider.getTracer("orders-api", "1.0.0");

        MessageHandler jaegerBasic = tracer.getJaegerMessageHandler(
                "http://localhost:4318/v1/logs",
                "jaeger-user",
                "jaeger-pass");
        MessageHandler jaegerFull = tracer.getJaegerMessageHandler(
                "http://localhost:4318/v1/logs",
                "jaeger-user",
                "jaeger-pass",
                "jaeger-token",
                5_000,
                5_000,
                Collections.singletonMap("X-Jaeger", "enabled"),
                false,
                0,
                false);

        MessageHandler grafanaBasic = tracer.getGrafanaMessageHandler(
                "http://localhost:3100/loki/api/v1/push",
                "grafana-user",
                "grafana-pass");
        MessageHandler grafanaFull = tracer.getGrafanaMessageHandler(
                "http://localhost:3100/loki/api/v1/push",
                "grafana-user",
                "grafana-pass",
                "grafana-token",
                5_000,
                5_000,
                Collections.singletonMap("X-Grafana", "enabled"),
                false,
                0,
                false);

        assertTrue(jaegerBasic instanceof com.threeamigos.common.util.implementations.messagehandler.JaegerMessageHandler);
        assertTrue(jaegerFull instanceof com.threeamigos.common.util.implementations.messagehandler.JaegerMessageHandler);
        assertTrue(grafanaBasic instanceof com.threeamigos.common.util.implementations.messagehandler.GrafanaMessageHandler);
        assertTrue(grafanaFull instanceof com.threeamigos.common.util.implementations.messagehandler.GrafanaMessageHandler);

        jaegerBasic.close();
        jaegerFull.close();
        grafanaBasic.close();
        grafanaFull.close();
    }

    @Test
    @DisplayName("file handler should use provider default path when null or blank is passed")
    void fileHandlerShouldUseProviderDefaultPathWhenNullOrBlankIsPassed() {
        TracerProvider provider = TracerProvider.builder()
                .serviceName("orders")
                .defaultFilePath("target/tracer-impl-default-path.log")
                .build();
        TracerImpl tracer = (TracerImpl) provider.getTracer("orders-api", "1.0.0");

        MessageHandler fromNull = tracer.getFileMessageHandler((String) null);
        MessageHandler fromBlank = tracer.getFileMessageHandler("   ");
        assertNotNull(fromNull);
        assertNotNull(fromBlank);
        fromNull.close();
        fromBlank.close();
    }

    @Test
    @DisplayName("file handler overloads should create sidecar lock files automatically")
    void fileHandlerOverloadsShouldCreateSidecarLockFilesAutomatically() throws Exception {
        Path pathFromString = Files.createTempFile("tracer-impl-lock-string-", ".log");
        Path pathFromStringFormatter = Files.createTempFile("tracer-impl-lock-string-formatter-", ".log");
        Path pathFromFile = Files.createTempFile("tracer-impl-lock-file-", ".log");
        Path pathFromFileFormatter = Files.createTempFile("tracer-impl-lock-file-formatter-", ".log");
        TracerProvider provider = TracerProvider.builder()
                .serviceName("orders")
                .build();
        TracerImpl tracer = (TracerImpl) provider.getTracer("orders-api", "1.0.0");
        LogRecordFormatter formatter = logRecord -> "LOCK:" + logRecord.getBody().asString();
        Filter passThrough = new Filter() {
            @Override
            public LogRecord filter(final LogRecord logRecord) {
                return logRecord;
            }
        };

        try (MessageHandler fromString = tracer.getFileMessageHandler(pathFromString.toString(), passThrough);
             MessageHandler fromStringFormatter = tracer.getFileMessageHandler(
                     pathFromStringFormatter.toString(),
                     formatter,
                     passThrough);
             MessageHandler fromFile = tracer.getFileMessageHandler(pathFromFile.toFile(), passThrough);
             MessageHandler fromFileFormatter = tracer.getFileMessageHandler(
                     pathFromFileFormatter.toFile(),
                     formatter,
                     passThrough)) {
            fromString.info("string");
            fromStringFormatter.info("string-formatter");
            fromFile.info("file");
            fromFileFormatter.info("file-formatter");
        }

        assertTrue(Files.exists(resolveLockSidecarPath(pathFromString)));
        assertTrue(Files.exists(resolveLockSidecarPath(pathFromStringFormatter)));
        assertTrue(Files.exists(resolveLockSidecarPath(pathFromFile)));
        assertTrue(Files.exists(resolveLockSidecarPath(pathFromFileFormatter)));
    }

    @Test
    @DisplayName("console formatter overloads should render formatted output and respect filters")
    void consoleFormatterOverloadsShouldRenderFormattedOutputAndRespectFilters() throws Exception {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outBuffer, true, StandardCharsets.UTF_8.name()));
        System.setErr(new PrintStream(errBuffer, true, StandardCharsets.UTF_8.name()));
        try {
            TracerImpl tracer = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList());
            LogRecordFormatter formatter = logRecord -> "TRACER-CONSOLE:" + logRecord.getBody().asString();

            MessageHandler unfiltered = tracer.getConsoleMessageHandler(formatter);
            unfiltered.info("hello-console");
            unfiltered.close();

            com.threeamigos.common.util.interfaces.messagehandler.otel.Filter allowOnlyErrors = logRecord ->
                    logRecord.getSeverityNumber() == SeverityNumber.ERROR ? logRecord : null;
            MessageHandler filtered = tracer.getConsoleMessageHandler(formatter, allowOnlyErrors);
            filtered.info("drop-console");
            filtered.error("keep-console");
            filtered.close();

            String stdout = outBuffer.toString(StandardCharsets.UTF_8.name());
            String stderr = errBuffer.toString(StandardCharsets.UTF_8.name());

            assertTrue(stdout.contains("TRACER-CONSOLE:hello-console"));
            assertFalse(stdout.contains("TRACER-CONSOLE:drop-console"));
            assertTrue(stderr.contains("TRACER-CONSOLE:keep-console"));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    @Test
    @DisplayName("file formatter overloads should render formatted output and respect filters")
    void fileFormatterOverloadsShouldRenderFormattedOutputAndRespectFilters() throws Exception {
        Path unfilteredFile = Files.createTempFile("tracer-impl-formatter-unfiltered-", ".log");
        Path filteredFile = Files.createTempFile("tracer-impl-formatter-filtered-", ".log");
        Path stringTargetFile = Files.createTempFile("tracer-impl-formatter-string-", ".log");
        Path fileTargetFile = Files.createTempFile("tracer-impl-formatter-file-", ".log");
        Path stringFilteredTargetFile = Files.createTempFile("tracer-impl-formatter-string-filtered-", ".log");
        Path fileFilteredTargetFile = Files.createTempFile("tracer-impl-formatter-file-filtered-", ".log");

        LogRecordFormatter formatter = logRecord -> "TRACER-FILE:" + logRecord.getBody().asString();

        TracerProvider unfilteredProvider = TracerProvider.builder()
                .serviceName("orders")
                .defaultFilePath(unfilteredFile.toAbsolutePath().toString())
                .build();
        TracerImpl unfilteredTracer = (TracerImpl) unfilteredProvider.getTracer("orders-api", "1.0.0");

        MessageHandler unfiltered = unfilteredTracer.getFileMessageHandler(
                unfilteredFile.toAbsolutePath().toString(),
                formatter);
        unfiltered.info("hello-file");
        unfiltered.close();

        String unfilteredOutput = new String(Files.readAllBytes(unfilteredFile), StandardCharsets.UTF_8);
        assertTrue(unfilteredOutput.contains("TRACER-FILE:hello-file"));

        MessageHandler fromString = unfilteredTracer.getFileMessageHandler(stringTargetFile.toString(), formatter);
        fromString.info("hello-file-string");
        fromString.close();
        String fromStringOutput = new String(Files.readAllBytes(stringTargetFile), StandardCharsets.UTF_8);
        assertTrue(fromStringOutput.contains("TRACER-FILE:hello-file-string"));

        MessageHandler fromFile = unfilteredTracer.getFileMessageHandler(fileTargetFile.toFile(), formatter);
        fromFile.info("hello-file-object");
        fromFile.close();
        String fromFileOutput = new String(Files.readAllBytes(fileTargetFile), StandardCharsets.UTF_8);
        assertTrue(fromFileOutput.contains("TRACER-FILE:hello-file-object"));

        TracerProvider filteredProvider = TracerProvider.builder()
                .serviceName("orders")
                .defaultFilePath(filteredFile.toAbsolutePath().toString())
                .build();
        TracerImpl filteredTracer = (TracerImpl) filteredProvider.getTracer("orders-api", "1.0.0");

        com.threeamigos.common.util.interfaces.messagehandler.otel.Filter allowOnlyErrors = logRecord ->
                logRecord.getSeverityNumber() == SeverityNumber.ERROR ? logRecord : null;
        MessageHandler filtered = filteredTracer.getFileMessageHandler(
                filteredFile.toAbsolutePath().toString(),
                formatter,
                allowOnlyErrors);
        filtered.info("drop-file");
        filtered.error("keep-file");
        filtered.close();

        String filteredOutput = new String(Files.readAllBytes(filteredFile), StandardCharsets.UTF_8);
        assertFalse(filteredOutput.contains("TRACER-FILE:drop-file"));
        assertTrue(filteredOutput.contains("TRACER-FILE:keep-file"));

        MessageHandler filteredFromString = filteredTracer.getFileMessageHandler(
                stringFilteredTargetFile.toString(),
                formatter,
                allowOnlyErrors);
        filteredFromString.info("drop-file-string");
        filteredFromString.error("keep-file-string");
        filteredFromString.close();
        String filteredFromStringOutput = new String(Files.readAllBytes(stringFilteredTargetFile), StandardCharsets.UTF_8);
        assertFalse(filteredFromStringOutput.contains("TRACER-FILE:drop-file-string"));
        assertTrue(filteredFromStringOutput.contains("TRACER-FILE:keep-file-string"));

        MessageHandler filteredFromFile = filteredTracer.getFileMessageHandler(
                fileFilteredTargetFile.toFile(),
                formatter,
                allowOnlyErrors);
        filteredFromFile.info("drop-file-object");
        filteredFromFile.error("keep-file-object");
        filteredFromFile.close();
        String filteredFromFileOutput = new String(Files.readAllBytes(fileFilteredTargetFile), StandardCharsets.UTF_8);
        assertFalse(filteredFromFileOutput.contains("TRACER-FILE:drop-file-object"));
        assertTrue(filteredFromFileOutput.contains("TRACER-FILE:keep-file-object"));

        MessageHandler fromNullFile = filteredTracer.getFileMessageHandler((java.io.File) null, formatter);
        fromNullFile.info("hello-file-null");
        fromNullFile.close();
        String nullFileOutput = new String(Files.readAllBytes(filteredFile), StandardCharsets.UTF_8);
        assertTrue(nullFileOutput.contains("TRACER-FILE:hello-file-null"));
    }

    @Test
    @DisplayName("applyFilterLevels should also evaluate source class branch")
    void applyFilterLevelsShouldAlsoEvaluateSourceClassBranch() throws Exception {
        TracerProvider provider = TracerProvider.builder().serviceName("orders").build();
        TracerImpl tracer = (TracerImpl) provider.getTracer("orders-api", "1.0.0");
        MessageHandler handler = tracer.getInMemoryMessageHandler();

        FilterByClassName filter = new FilterByClassName();
        filter.add(TracerImplUnitTest.class.getName(), com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber.INFO);

        Method method = TracerImpl.class.getDeclaredMethod(
                "applyFilterLevels",
                MessageHandler.class,
                Class.class,
                com.threeamigos.common.util.interfaces.messagehandler.otel.Filter.class);
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(tracer, handler, TracerImplUnitTest.class, filter));
    }

    @Test
    @DisplayName("applyFilterLevels should skip source attribute injection when probe is not LogRecordImpl")
    void applyFilterLevelsShouldSkipSourceAttributeInjectionWhenProbeIsNotLogRecordImpl() throws Exception {
        TracerProvider provider = TracerProvider.builder().serviceName("orders").build();
        TracerImpl tracer = new TracerImpl(
                provider,
                "orders-api",
                "1.0.0",
                "https://schema",
                Collections.emptyList()) {
            @Override
            LogRecord createFilterProbe(final com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber severityNumber) {
                return new LogRecord() {
                    @Override
                    public java.time.Instant getTimestamp() {
                        return java.time.Instant.now();
                    }

                    @Override
                    public java.time.Instant getObservedTimestamp() {
                        return null;
                    }

                    @Override
                    public String getTraceId() {
                        return null;
                    }

                    @Override
                    public String getSpanId() {
                        return null;
                    }

                    @Override
                    public int getTraceFlags() {
                        return 0;
                    }

                    @Override
                    public String getSeverityText() {
                        return null;
                    }

                    @Override
                    public com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber getSeverityNumber() {
                        return severityNumber;
                    }

                    @Override
                    public com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue getBody() {
                        return null;
                    }

                    @Override
                    public com.threeamigos.common.util.interfaces.messagehandler.otel.Resource getResource() {
                        return null;
                    }

                    @Override
                    public com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope getInstrumentationScope() {
                        return null;
                    }

                    @Override
                    public java.util.List<com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue> getAttributes() {
                        return Collections.emptyList();
                    }

                    @Override
                    public String getEventName() {
                        return null;
                    }
                };
            }
        };
        MessageHandler handler = tracer.getInMemoryMessageHandler();
        com.threeamigos.common.util.interfaces.messagehandler.otel.Filter passThrough =
                new com.threeamigos.common.util.interfaces.messagehandler.otel.Filter() {
                    @Override
                    public LogRecord filter(final LogRecord logRecord) {
                        return logRecord;
                    }
                };

        Method method = TracerImpl.class.getDeclaredMethod(
                "applyFilterLevels",
                MessageHandler.class,
                Class.class,
                com.threeamigos.common.util.interfaces.messagehandler.otel.Filter.class);
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(tracer, handler, TracerImplUnitTest.class, passThrough));
    }

    @Test
    @DisplayName("createFactory owner-null branch should return default factory")
    void createFactoryOwnerNullBranchShouldReturnDefaultFactory() throws Exception {
        TracerImpl detached = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList());
        Method method = TracerImpl.class.getDeclaredMethod("createFactory", String.class);
        method.setAccessible(true);
        Object factory = method.invoke(detached, (String) null);
        assertNotNull(factory);
        assertTrue(factory instanceof TracerMessageHandlerFactory);
    }

    @Test
    @DisplayName("detached tracer should return default log record factory when owner is absent")
    void detachedTracerShouldReturnDefaultLogRecordFactoryWhenOwnerIsAbsent() {
        TracerImpl detached = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList());
        LogRecordFactory factory = detached.getLogRecordFactory();

        assertNotNull(factory);
        LogRecord record = factory.create();
        assertNotNull(record);
    }

    @Test
    @DisplayName("detached file handler overloads should normalize null paths to concrete file handlers")
    void detachedFileHandlerOverloadsShouldNormalizeNullPathsToConcreteHandlers() {
        TracerImpl detached = new TracerImpl("orders", "1.0.0", "schema", Collections.emptyList());

        MessageHandler fromNullPath = detached.getFileMessageHandler((String) null);
        MessageHandler fromBlankPath = detached.getFileMessageHandler("   ");
        MessageHandler fromNullFile = detached.getFileMessageHandler(
                (java.io.File) null,
                (com.threeamigos.common.util.interfaces.messagehandler.otel.Filter) null);

        assertTrue(fromNullPath instanceof com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler);
        assertTrue(fromBlankPath instanceof com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler);
        assertTrue(fromNullFile instanceof com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler);

        fromNullPath.close();
        fromBlankPath.close();
        fromNullFile.close();
        assertDoesNotThrow(() -> Files.deleteIfExists(Paths.get("message-handler.log")));
        assertDoesNotThrow(() -> Files.deleteIfExists(Paths.get("message-handler.log.lck")));
    }

    private static Path resolveLockSidecarPath(final Path logFilePath) {
        Path absolute = logFilePath.toAbsolutePath().normalize();
        String lockFileName = absolute.getFileName().toString() + ".lck";
        Path parent = absolute.getParent();
        return parent != null ? parent.resolve(lockFileName) : absolute.resolveSibling(lockFileName);
    }
}
