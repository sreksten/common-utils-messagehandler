package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.Event;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Link;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanData;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanDispatcher;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import com.threeamigos.common.util.interfaces.messagehandler.otel.StatusCode;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SpanImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class SpanImplUnitTest extends AbstractOtelValidatorLogTrapUnitTest {

    private static final boolean ORIGINAL_LENIENT = OpenTelemetryAttributeValidator.isLenientMode();

    private SpanContext context;

    @BeforeEach
    void setup() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(false);
        context = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());
    }

    @AfterEach
    void teardown() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(ORIGINAL_LENIENT);
    }

    @Test
    @DisplayName("span should expose context and be recording until end")
    void spanShouldExposeContextAndRecordingLifecycle() {
        SpanImpl span = new SpanImpl("span-name", context);
        assertEquals(context, span.getSpanContext());
        assertEquals(context, span.getContext());
        assertNull(span.getInstrumentationScope());
        assertTrue(span.isRecording());
        assertNull(span.getEndTimestamp());

        span.end();
        assertFalse(span.isRecording());
        assertNotNull(span.getEndTimestamp());
    }

    @Test
    @DisplayName("span should expose instrumentation scope when provided")
    void spanShouldExposeInstrumentationScopeWhenProvided() {
        InstrumentationScope scope = InstrumentationScopeFactory.create(
                "orders", "1.0.0", "https://schema", Collections.emptyList());
        SpanImpl span = new SpanImpl("span-name", context, scope, Instant.now(), true);

        assertNotNull(span.getInstrumentationScope());
        assertEquals("orders", span.getInstrumentationScope().getName());
        assertEquals("1.0.0", span.getInstrumentationScope().getVersion());
    }

    @Test
    @DisplayName("create should produce child span using parent context when tracer is bound")
    void createShouldProduceChildSpanUsingParentContextWhenTracerIsBound() throws Exception {
        Tracer tracer = new TracerImpl("orders", "1.0.0", null, Collections.<KeyValue>emptyList());
        Span parent = tracer.createSpan("parent");

        Span child = parent.create("child");

        assertEquals(parent.getSpanContext().getTraceId(), child.getSpanContext().getTraceId());
        assertEquals(parent.getSpanContext().getSpanId(), privateField(child, "parentSpanId"));
        child.end();
        parent.end();
    }

    @Test
    @DisplayName("create should produce child span with same trace id when tracer is not bound")
    void createShouldProduceChildSpanWithSameTraceIdWhenTracerIsNotBound() throws Exception {
        SpanImpl parent = new SpanImpl("parent", context);

        Span child = parent.create("child");

        assertEquals(context.getTraceId(), child.getSpanContext().getTraceId());
        assertEquals(context.getSpanId(), privateField(child, "parentSpanId"));
        child.end();
    }

    @Test
    @DisplayName("create should generate a fresh child context when parent context is invalid")
    void createShouldGenerateFreshChildContextWhenParentContextIsInvalid() throws Exception {
        SpanImpl parent = new SpanImpl("parent", new SpanContextImpl());

        Span child = parent.create("child");

        assertTrue(child.getSpanContext().isValid());
        assertNull(privateField(child, "parentSpanId"));
        child.end();
    }

    @Test
    @DisplayName("create should generate a child context when internal parent context is null")
    void createShouldGenerateChildContextWhenInternalParentContextIsNull() throws Exception {
        SpanImpl parent = new SpanImpl("parent", context);
        setField(parent, "spanContext", null);

        Span child = parent.create("child");

        assertTrue(child.getSpanContext().isValid());
        assertNull(privateField(child, "parentSpanId"));
        child.end();
    }

    @Test
    @DisplayName("constructor should default null start timestamp in lenient mode")
    void constructorShouldDefaultNullStartTimestampInLenientMode() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        SpanImpl span = new SpanImpl("span-name", context, null, null, true);
        assertNotNull(span.getStartTimestamp());
        assertTrue(span.isRecording());
    }

    @Test
    @DisplayName("setAttribute should upsert attributes while recording")
    void setAttributeShouldUpsertAttributes() {
        SpanImpl span = new SpanImpl("span-name", context);
        span.setAttribute("http.method", AnyValueFactory.ofString("GET"));
        span.setAttribute("http.method", AnyValueFactory.ofString("POST"));

        List<KeyValue> attributes = span.getAttributesSnapshot();
        assertEquals(1, attributes.size());
        assertEquals("http.method", attributes.get(0).getKey());
        assertEquals("POST", attributes.get(0).getValue().asString());
    }

    @Test
    @DisplayName("addEvent overloads should record named events with timestamps")
    void addEventOverloadsShouldRecordEvents() {
        SpanImpl span = new SpanImpl("span-name", context);
        Instant eventTime = Instant.now();

        span.addEvent("e1");
        span.addEvent("e2", Collections.singletonList(
                new KeyValueImpl("k", AnyValueFactory.ofString("v"))));
        span.addEvent("e3", Collections.<KeyValue>emptyList(), eventTime);

        List<Event> events = span.getEventsSnapshot();
        assertEquals(3, events.size());
        assertEquals("e1", events.get(0).getName());
        assertEquals("e2", events.get(1).getName());
        assertEquals("e3", events.get(2).getName());
        assertEquals(eventTime, events.get(2).getTimestamp());
    }

    @Test
    @DisplayName("addLink overloads should record links")
    void addLinkOverloadsShouldRecordLinks() {
        SpanImpl span = new SpanImpl("span-name", context);
        SpanContext linkedContext = new SpanContextImpl(
                "4b8efff798038103d269b633813fc60c",
                "ddd19b7ec3c1b174",
                (byte) 0x00,
                true,
                new TraceStateImpl());

        span.addLink(linkedContext);
        span.addLink(linkedContext, Collections.singletonList(
                new KeyValueImpl("link.attr", AnyValueFactory.ofString("x"))));

        assertEquals(2, span.getLinksSnapshot().size());
        assertEquals(linkedContext, span.getLinksSnapshot().get(0).getSpanContext());
        assertEquals("link.attr", span.getLinksSnapshot().get(1).getAttributes().get(0).getKey());
    }

    @Test
    @DisplayName("setStatus should ignore UNSET and make OK final")
    void setStatusShouldFollowOtelRules() {
        SpanImpl span = new SpanImpl("span-name", context);

        span.setStatus(StatusCode.UNSET);
        assertEquals(StatusCode.UNSET, span.getStatusCode());

        span.setStatus(StatusCode.ERROR, "boom");
        assertEquals(StatusCode.ERROR, span.getStatusCode());
        assertEquals("boom", span.getStatusDescription());

        span.setStatus(StatusCode.OK, "ignored");
        assertEquals(StatusCode.OK, span.getStatusCode());
        assertEquals("", span.getStatusDescription());

        span.setStatus(StatusCode.ERROR, "should-not-override-ok");
        assertEquals(StatusCode.OK, span.getStatusCode());
        assertEquals("", span.getStatusDescription());
    }

    @Test
    @DisplayName("updateName should replace span name while recording")
    void updateNameShouldReplaceSpanName() {
        SpanImpl span = new SpanImpl("span-name", context);
        span.updateName("renamed-span");
        assertEquals("renamed-span", span.getName());
    }

    @Test
    @DisplayName("end with timestamp should set explicit end and be idempotent")
    void endWithTimestampShouldSetExplicitEndAndBeIdempotent() {
        SpanImpl span = new SpanImpl("span-name", context);
        Instant firstEnd = Instant.now();
        Instant secondEnd = firstEnd.plusSeconds(10);

        span.end(firstEnd);
        span.end(secondEnd);

        assertEquals(firstEnd, span.getEndTimestamp());
        assertFalse(span.isRecording());
    }

    @Test
    @DisplayName("recordException should append exception event attributes")
    void recordExceptionShouldAppendExceptionEventAttributes() {
        SpanImpl span = new SpanImpl("span-name", context);
        span.recordException(new IllegalStateException("boom"),
                Collections.singletonList(new KeyValueImpl("extra", AnyValueFactory.ofString("x"))));

        List<Event> events = span.getEventsSnapshot();
        assertEquals(1, events.size());
        assertEquals("exception", events.get(0).getName());
        List<String> keys = Arrays.asList(
                events.get(0).getAttributes().get(0).getKey(),
                events.get(0).getAttributes().get(1).getKey(),
                events.get(0).getAttributes().get(2).getKey(),
                events.get(0).getAttributes().get(3).getKey());
        assertTrue(keys.contains(OTelTags.EXCEPTION_TYPE.getValue()));
        assertTrue(keys.contains(OTelTags.EXCEPTION_MESSAGE.getValue()));
        assertTrue(keys.contains(OTelTags.EXCEPTION_STACKTRACE.getValue()));
        assertTrue(keys.contains("extra"));
    }

    @Test
    @DisplayName("scope token close failures should be swallowed when ending span")
    void scopeTokenCloseFailuresShouldBeSwallowedWhenEndingSpan() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        SpanImpl span = new SpanImpl("span-name", context, null, Instant.now(), true, new AutoCloseable() {
            @Override
            public void close() {
                throw new RuntimeException("scope-close");
            }
        });

        span.setStatus(StatusCode.OK);
        span.setStatus(StatusCode.ERROR, "ignored-because-ok");
        assertDoesNotThrow(() -> span.end());
        assertFalse(span.isRecording());
        assertEquals(StatusCode.OK, span.getStatusCode());
    }

    @Test
    @DisplayName("recordException should skip exception.message when null")
    void recordExceptionShouldSkipExceptionMessageWhenNull() {
        SpanImpl span = new SpanImpl("span-name", context);
        span.recordException(new RuntimeException());

        List<Event> events = span.getEventsSnapshot();
        assertEquals(1, events.size());
        boolean hasMessage = false;
        for (KeyValue kv : events.get(0).getAttributes()) {
            if (OTelTags.EXCEPTION_MESSAGE.getValue().equals(kv.getKey())) {
                hasMessage = true;
            }
        }
        assertFalse(hasMessage);
    }

    @Test
    @DisplayName("recording disabled span should start as ended")
    void recordingDisabledSpanShouldStartEnded() {
        SpanImpl span = new SpanImpl("span-name", context, null, Instant.now(), false);
        assertFalse(span.isRecording());
    }

    @Test
    @DisplayName("null event timestamp and null wrappers should be handled in lenient mode")
    void nullEventTimestampAndNullWrappersShouldBeHandledInLenientMode() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        SpanImpl span = new SpanImpl("span-name", context);
        span.addEvent("evt", Collections.<KeyValue>emptyList(), null);
        span.addEvent((Event) null);
        span.addLink((Link) null);
        assertEquals(1, span.getEventsSnapshot().size());
    }

    @Test
    @DisplayName("second recording checks inside synchronized blocks should be covered")
    void secondRecordingChecksInsideSynchronizedBlocksShouldBeCovered() throws Exception {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        SpanImpl span = new SpanImpl("span-name", context);
        runWhileSecondRecordingCheckShouldFail(span, new Runnable() {
            @Override
            public void run() {
                span.setAttribute("k", AnyValueFactory.ofString("v"));
            }
        });
        runWhileSecondRecordingCheckShouldFail(span, new Runnable() {
            @Override
            public void run() {
                span.addEvent("e", Collections.<KeyValue>emptyList(), Instant.now());
            }
        });
        runWhileSecondRecordingCheckShouldFail(span, new Runnable() {
            @Override
            public void run() {
                span.addLink(context, Collections.<KeyValue>emptyList());
            }
        });
        runWhileSecondRecordingCheckShouldFail(span, new Runnable() {
            @Override
            public void run() {
                span.setStatus(StatusCode.ERROR, "boom");
            }
        });
        runWhileSecondRecordingCheckShouldFail(span, new Runnable() {
            @Override
            public void run() {
                span.updateName("new-name");
            }
        });

        assertTrue(span.getAttributesSnapshot().isEmpty());
        assertTrue(span.getEventsSnapshot().isEmpty());
        assertTrue(span.getLinksSnapshot().isEmpty());
        assertEquals(StatusCode.UNSET, span.getStatusCode());
        assertEquals("span-name", span.getName());
    }

    @Test
    @DisplayName("recordException should handle empty stack trace branch")
    void recordExceptionShouldHandleEmptyStackTraceBranch() {
        SpanImpl span = new SpanImpl("span-name", context);
        RuntimeException withoutStack = new RuntimeException("x") {
            @Override
            public void printStackTrace(java.io.PrintWriter s) {
                // force empty stack trace serialization
            }
        };
        span.recordException(withoutStack);
        assertEquals(1, span.getEventsSnapshot().size());
    }

    private static Object privateField(final Object target, final String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void setEnded(final SpanImpl span, final boolean value) throws Exception {
        Field f = SpanImpl.class.getDeclaredField("ended");
        f.setAccessible(true);
        f.set(span, value);
    }

    private static void setField(final Object target, final String name, final Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static void runWhileSecondRecordingCheckShouldFail(final SpanImpl span,
                                                               final Runnable call) throws Exception {
        final Object lock = privateField(span, "lock");
        final Thread[] holder = new Thread[1];
        synchronized (lock) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    call.run();
                }
            });
            holder[0] = thread;
            thread.start();
            long deadline = System.currentTimeMillis() + 2000L;
            while (thread.getState() != Thread.State.BLOCKED
                    && thread.isAlive()
                    && System.currentTimeMillis() < deadline) {
                Thread.yield();
            }
            setEnded(span, true);
        }
        holder[0].join(2000L);
        assertFalse(holder[0].isAlive());
        setEnded(span, false);
    }

    @Test
    @DisplayName("recordException additional attributes should override generated ones")
    void recordExceptionAdditionalAttributesShouldOverrideGeneratedKeys() {
        SpanImpl span = new SpanImpl("span-name", context);
        span.recordException(new IllegalStateException("boom"),
                Collections.singletonList(new KeyValueImpl(
                        OTelTags.EXCEPTION_TYPE.getValue(), AnyValueFactory.ofString("custom.type"))));

        List<Event> events = span.getEventsSnapshot();
        assertEquals(1, events.size());
        String exceptionTypeValue = null;
        for (KeyValue kv : events.get(0).getAttributes()) {
            if (OTelTags.EXCEPTION_TYPE.getValue().equals(kv.getKey())) {
                exceptionTypeValue = kv.getValue().asString();
            }
        }
        assertEquals("custom.type", exceptionTypeValue);
    }

    @Test
    @DisplayName("end should dispatch snapshot to configured span dispatcher")
    void endShouldDispatchSnapshotToConfiguredSpanDispatcher() {
        AtomicReference<SpanData> captured = new AtomicReference<SpanData>();
        SpanDispatcher dispatcher = captured::set;
        SpanImpl span = new SpanImpl(
                "span-name",
                context,
                null,
                Instant.now(),
                true,
                null,
                "abcdabcdabcdabcd",
                dispatcher);
        span.setAttribute("k", AnyValueFactory.ofString("v"));
        span.addEvent("event-1");
        span.addLink(context);
        span.setStatus(StatusCode.ERROR, "boom");

        span.end();

        SpanData snapshot = captured.get();
        assertNotNull(snapshot);
        assertEquals("span-name", snapshot.getName());
        assertEquals(context, snapshot.getSpanContext());
        assertEquals("abcdabcdabcdabcd", snapshot.getParentSpanId());
        assertNotNull(snapshot.getStartTimestamp());
        assertNotNull(snapshot.getEndTimestamp());
        assertEquals(StatusCode.ERROR, snapshot.getStatusCode());
        assertEquals(1, snapshot.getAttributes().size());
        assertEquals(1, snapshot.getEvents().size());
        assertEquals("boom", snapshot.getStatusDescription());
        assertEquals(1, snapshot.getLinks().size());
    }

    @Test
    @DisplayName("ended span should ignore mutating operations")
    void endedSpanShouldIgnoreMutatingOperations() {
        SpanImpl span = new SpanImpl("span-name", context);
        span.end();

        span.setAttribute("k", AnyValueFactory.ofString("v"));
        span.addEvent("e");
        span.addLink(context);
        span.setStatus(StatusCode.ERROR, "boom");
        span.updateName("new-name");
        span.recordException(new IllegalStateException("boom"));

        assertTrue(span.getAttributesSnapshot().isEmpty());
        assertTrue(span.getEventsSnapshot().isEmpty());
        assertTrue(span.getLinksSnapshot().isEmpty());
        assertEquals(StatusCode.UNSET, span.getStatusCode());
        assertEquals("span-name", span.getName());
    }

    @Test
    @DisplayName("event and link object overloads should delegate to field-based overloads")
    void eventAndLinkObjectOverloadsShouldDelegateToFieldBasedOverloads() {
        SpanImpl span = new SpanImpl("span-name", context);
        Event event = new EventImpl("object-event", Instant.now(), Collections.<KeyValue>emptyList());
        Link link = new LinkImpl(context, Collections.<KeyValue>emptyList());

        span.addEvent(event);
        span.addLink(link);

        assertEquals(1, span.getEventsSnapshot().size());
        assertEquals("object-event", span.getEventsSnapshot().get(0).getName());
        assertEquals(1, span.getLinksSnapshot().size());
    }

    @Test
    @DisplayName("setStatus should normalize null descriptions for error status")
    void setStatusShouldNormalizeNullDescriptionsForErrorStatus() {
        SpanImpl span = new SpanImpl("span-name", context);
        span.setStatus(StatusCode.ERROR, null);
        assertEquals(StatusCode.ERROR, span.getStatusCode());
        assertEquals("", span.getStatusDescription());

        span.setStatus(StatusCode.OK, "");
        span.setStatus(StatusCode.OK, "");
        assertEquals(StatusCode.OK, span.getStatusCode());
    }

    @Test
    @DisplayName("dispatcher failures should be swallowed for both IO and runtime exceptions")
    void dispatcherFailuresShouldBeSwallowedForBothIoAndRuntimeExceptions() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);

        SpanImpl ioFailure = new SpanImpl(
                "span-name",
                context,
                null,
                Instant.now(),
                true,
                null,
                null,
                new SpanDispatcher() {
                    @Override
                    public void dispatchSpan(final SpanData spanData) throws java.io.IOException {
                        throw new java.io.IOException("io failure");
                    }
                });
        ioFailure.end();
        assertFalse(ioFailure.isRecording());

        SpanImpl runtimeFailure = new SpanImpl(
                "span-name",
                context,
                null,
                Instant.now(),
                true,
                null,
                null,
                new SpanDispatcher() {
                    @Override
                    public void dispatchSpan(final SpanData spanData) {
                        throw new RuntimeException("runtime failure");
                    }
                });
        runtimeFailure.end();
        assertFalse(runtimeFailure.isRecording());
    }

    @Test
    @DisplayName("dispatch helper should return when snapshot is null")
    void dispatchHelperShouldReturnWhenSnapshotIsNull() throws Exception {
        SpanImpl span = new SpanImpl(
                "span-name",
                context,
                null,
                Instant.now(),
                true,
                null,
                null,
                new SpanDispatcher() {
                    @Override
                    public void dispatchSpan(final SpanData spanData) {
                        throw new AssertionError("dispatch should not be called");
                    }
                });

        java.lang.reflect.Method dispatch = SpanImpl.class.getDeclaredMethod("dispatchSpanDataQuietly", SpanData.class);
        dispatch.setAccessible(true);
        dispatch.invoke(span, new Object[]{null});
    }

    @Test
    @DisplayName("non-recording span wrapper should no-op but keep context")
    void nonRecordingSpanWrapperShouldNoOp() {
        Span span = Span.wrap(context);
        assertFalse(span.isRecording());
        assertEquals(context, span.getSpanContext());
        assertEquals(context, span.getContext());

        span.setAttribute("k", AnyValueFactory.ofString("v"));
        span.addEvent("e");
        span.addLink(context);
        span.setStatus(StatusCode.ERROR, "boom");
        span.updateName("new-name");
        span.recordException(new IllegalStateException("boom"));
        span.end();
        span.end(Instant.now());

        assertFalse(span.isRecording());
    }

    @Test
    @DisplayName("wrap with null context should return non-recording span with invalid context")
    void wrapWithNullContextShouldReturnNonRecordingSpanWithInvalidContext() {
        Span span = Span.wrap(null);
        assertFalse(span.isRecording());
        assertNotNull(span.getSpanContext());
        assertFalse(span.getSpanContext().isValid());
    }

    @Test
    @DisplayName("strict mode should throw on invalid span inputs")
    void strictModeShouldThrowOnInvalidInputs() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(false);

        assertThrows(IllegalArgumentException.class, () -> new SpanImpl(null, context));
        SpanImpl span = new SpanImpl("span-name", context);
        assertThrows(IllegalArgumentException.class, () -> span.setAttribute(null, AnyValueFactory.ofString("v")));
        assertThrows(IllegalArgumentException.class, () -> span.setAttribute("k", null));
        assertThrows(IllegalArgumentException.class, () -> span.addEvent((String) null));
        assertThrows(IllegalArgumentException.class, () -> span.addLink((SpanContext) null));
        assertThrows(IllegalArgumentException.class, () -> span.setStatus(null));
        assertThrows(IllegalArgumentException.class, () -> span.updateName(" "));
        assertThrows(IllegalArgumentException.class, () -> span.end(null));
        assertThrows(IllegalArgumentException.class, () -> span.recordException(null));
    }

    @Test
    @DisplayName("lenient mode should sanitize invalid span inputs and continue")
    void lenientModeShouldSanitizeInvalidInputs() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);

        SpanImpl span = new SpanImpl(null, null);
        assertEquals("unknown", span.getName());
        assertNotNull(span.getContext());

        span.setAttribute(null, null);
        span.addEvent((String) null);
        span.addLink((SpanContext) null);
        span.setStatus(null);
        span.updateName(" ");
        span.end(null);
        span.recordException(null);

        assertFalse(span.isRecording());
        assertNotNull(span.getEndTimestamp());
    }
}
