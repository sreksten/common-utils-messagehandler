package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.Event;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import com.threeamigos.common.util.interfaces.messagehandler.otel.StatusCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
