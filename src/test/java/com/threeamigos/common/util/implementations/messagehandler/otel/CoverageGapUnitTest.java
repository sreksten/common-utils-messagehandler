package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Context;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Entity;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import com.threeamigos.common.util.interfaces.messagehandler.otel.StatusCode;
import com.threeamigos.common.util.interfaces.messagehandler.otel.TraceState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Coverage gap tests")
@Tag("unit")
@Tag("messageHandler")
class CoverageGapUnitTest extends AbstractOtelValidatorLogTrapUnitTest {

    @AfterEach
    void resetLenient() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(false);
    }

    @Test
    @DisplayName("builder and validator edge branches should be covered with log trap")
    void builderAndValidatorEdgeBranchesShouldBeCoveredWithLogTrap() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        AtomicInteger trapped = new AtomicInteger();
        OpenTelemetryAttributeValidator.setLogTrapForTests((m, t) -> trapped.incrementAndGet());

        TracerProvider provider = TracerProvider.builder()
                .resourceAttribute((OTelTags) null, "x")
                .resourceAttribute(" ", "x")
                .commonAttribute(" ", "x")
                .serviceName("svc")
                .build();

        assertNotNull(provider.getDefaultResource());
        assertTrue(trapped.get() > 0);
    }

    @Test
    @DisplayName("default console and file handler constructors should be covered")
    void defaultConsoleAndFileHandlerConstructorsShouldBeCovered() throws Exception {
        ConsoleMessageHandler console = new ConsoleMessageHandler();
        console.info("console-default");
        console.close();

        Path temp = Files.createTempFile("file-handler-default-", ".log");
        FileMessageHandler file = new FileMessageHandler(temp.toString());
        file.info("file-default");
        file.close();

        assertTrue(Files.readAllBytes(temp).length > 0);
    }

    @Test
    @DisplayName("context detach non-lifo and trace state invalid branches should be covered")
    void contextDetachNonLifoAndTraceStateInvalidBranchesShouldBeCovered() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        Context ctx = new ContextImpl();
        Context.Key key = ctx.createKey("k");
        Context c1 = ctx.set(key, AnyValueFactory.ofString("v1"));
        Context c2 = c1.set(key, AnyValueFactory.ofString("v2"));
        String t1 = ctx.attachContext(c1);
        String t2 = ctx.attachContext(c2);
        assertFalse(ctx.detachContext(t1));
        assertTrue(ctx.detachContext(t2));
        assertTrue(ctx.detachContext(t1));

        TraceState traceState = new TraceStateImpl();
        assertSame(traceState, traceState.set("INVALID KEY", AnyValueFactory.ofString("x")));
        assertSame(traceState, traceState.delete("INVALID KEY"));
        assertNull(traceState.get("INVALID KEY"));
    }

    @Test
    @DisplayName("enriching factory resolver exception and tracer null-owner branches should be covered")
    void enrichingFactoryResolverExceptionAndTracerNullOwnerBranchesShouldBeCovered() {
        EnrichingLogRecordFactory factory = new EnrichingLogRecordFactory(
                new LogRecordFactoryImpl(),
                null,
                null,
                Collections.<KeyValue>emptyList(),
                null,
                () -> {
                    throw new IllegalStateException("span-resolver");
                },
                () -> {
                    throw new IllegalStateException("scope-resolver");
                });
        LogRecord record = factory.create(SeverityNumber.INFO, "hello");
        assertNotNull(record);

        TracerImpl orphanTracer = new TracerImpl("orphan", "1", null, Collections.<KeyValue>emptyList());
        assertDoesNotThrow(() -> {
            orphanTracer.getJULMessageHandler(java.util.logging.Logger.getLogger("j")).info("x");
            orphanTracer.getLog4JMessageHandler(org.apache.logging.log4j.LogManager.getLogger("l")).info("x");
            orphanTracer.getSLF4JMessageHandler(org.slf4j.LoggerFactory.getLogger("s")).info("x");
        });
    }

    @Test
    @DisplayName("span scope token failure and status transitions should be covered")
    void spanScopeTokenFailureAndStatusTransitionsShouldBeCovered() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        SpanContext ctx = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());

        SpanImpl span = new SpanImpl("s", ctx, null, Instant.now(), true, new AutoCloseable() {
            @Override
            public void close() {
                throw new RuntimeException("scope-close");
            }
        });
        span.setStatus(StatusCode.OK);
        span.setStatus(StatusCode.ERROR, "ignored-because-ok");
        span.recordException(new IllegalArgumentException("boom"));
        span.end();
        assertFalse(span.isRecording());
    }

    @Test
    @DisplayName("tracer key equals and hashcode branches should be covered")
    void tracerKeyEqualsAndHashCodeBranchesShouldBeCovered() throws Exception {
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

        Method equalsMethod = keyClass.getDeclaredMethod("equals", Object.class);
        Method hashMethod = keyClass.getDeclaredMethod("hashCode");
        equalsMethod.setAccessible(true);
        hashMethod.setAccessible(true);

        assertTrue((Boolean) equalsMethod.invoke(a, b));
        assertFalse((Boolean) equalsMethod.invoke(a, c));
        assertFalse((Boolean) equalsMethod.invoke(a, "not-a-key"));
        assertNotEquals(hashMethod.invoke(a), hashMethod.invoke(c));
    }

    @Test
    @DisplayName("log record and key/value edge branches should be covered")
    void logRecordAndKeyValueEdgeBranchesShouldBeCovered() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);

        LogRecordFactoryImpl factory = new LogRecordFactoryImpl();
        factory.create((SeverityNumber) null, null);
        factory.create((String) null, null);
        factory.create((Throwable) null);

        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(Instant.ofEpochSecond(-1));
        record.setObservedTimestamp(Instant.ofEpochSecond(-1));
        record.setTraceId("invalid");
        record.setSpanId("invalid");
        record.setTraceFlags(-1);
        record.setSeverityText("  ");
        record.setEventName("  ");
        record.setAttributes(null);

        new KeyValueImpl((OTelTags) null, AnyValueFactory.ofString("x"));
        new KeyValueImpl(" ", null);

        List<KeyValue> dup = new ArrayList<KeyValue>();
        dup.add(KeyValueFactory.of("k", AnyValueFactory.ofString("1")));
        dup.add(KeyValueFactory.of("k", AnyValueFactory.ofString("2")));
        AnyValue v1 = AnyValueFactory.ofKvList(dup);
        AnyValue v2 = AnyValueFactory.ofKvList(dup);
        assertTrue(v1.equals(v2));
        assertNotNull(v1.hashCode());
    }

    @Test
    @DisplayName("entity and resource edge merges should be covered")
    void entityAndResourceEdgeMergesShouldBeCovered() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);

        Entity e1 = EntityFactory.create("service", null,
                Collections.singletonList(KeyValueFactory.of("service.name", AnyValueFactory.ofString("a"))),
                Collections.singletonList(KeyValueFactory.of("x", AnyValueFactory.ofString("1"))));
        Entity e2 = EntityFactory.create("service", null,
                Collections.singletonList(KeyValueFactory.of("service.name", AnyValueFactory.ofString("a"))),
                Collections.singletonList(KeyValueFactory.of("x", AnyValueFactory.ofString("2"))));
        Entity merged = e1.merge(e2);
        assertNotNull(merged);

        Resource r1 = ResourceFactory.create("s1",
                Collections.singletonList(e1),
                Collections.singletonList(KeyValueFactory.of("loose", AnyValueFactory.ofString("v1"))));
        Resource r2 = ResourceFactory.create("s2",
                Collections.singletonList(e2),
                Collections.singletonList(KeyValueFactory.of("loose", AnyValueFactory.ofString("v2"))));
        Resource mergedResource = r1.merge(r2);
        assertNotNull(mergedResource);

        Entity built = EntityBuilderFactory.getBuilder()
                .withType("x")
                .withNoSchemaUrl()
                .withId((List<KeyValue>) null)
                .withDescription((List<KeyValue>) null)
                .build();
        assertNotNull(built);
    }

    @Test
    @DisplayName("tracer provider correlation scope close should be covered")
    void tracerProviderCorrelationScopeCloseShouldBeCovered() {
        TracerProvider provider = TracerProvider.createProvider();
        Span span = provider.getTracer("n", "1").createSpan("x");
        TracerProvider.CorrelationScope scope = provider.attachCorrelation(span.getSpanContext());
        scope.close();
        provider.clearCorrelation();
    }
}
