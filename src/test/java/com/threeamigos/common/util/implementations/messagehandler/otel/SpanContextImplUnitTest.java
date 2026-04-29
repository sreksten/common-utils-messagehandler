package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.TraceState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SpanContextImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class SpanContextImplUnitTest {

    private static final boolean ORIGINAL_LENIENT = OpenTelemetryAttributeValidator.isLenientMode();
    private static final String TRACE_ID = "5b8efff798038103d269b633813fc60c";
    private static final String SPAN_ID = "eee19b7ec3c1b174";

    @BeforeEach
    void setStrictModeByDefault() {
        setLenient(false);
    }

    @AfterEach
    void restoreLenientMode() {
        setLenient(ORIGINAL_LENIENT);
    }

    @Test
    @DisplayName("context should normalize IDs and expose flags, remote and trace state")
    void shouldNormalizeIdsAndExposeFlagsRemoteAndTraceState() {
        TraceState traceState = new TraceStateImpl().set("vendor", AnyValueFactory.ofString("value"));

        SpanContextImpl context = new SpanContextImpl(
                "5B8EFFF798038103D269B633813FC60C",
                "EEE19B7EC3C1B174",
                (byte) 0x03,
                true,
                traceState
        );

        assertEquals(TRACE_ID, context.getTraceId());
        assertEquals(SPAN_ID, context.getSpanId());
        assertArrayEquals(hexToBytes(TRACE_ID), context.getTraceIdBytes());
        assertArrayEquals(hexToBytes(SPAN_ID), context.getSpanIdBytes());
        assertEquals((byte) 0x03, context.getTraceFlags());
        assertTrue(context.isSampled());
        assertTrue(context.isRandom());
        assertTrue(context.isValid());
        assertTrue(context.isRemote());
        assertEquals(1, context.getTraceState().getValues().size());
        assertEquals("vendor", context.getTraceState().getValues().iterator().next().getKey());
        assertEquals("value", context.getTraceState().getValues().iterator().next().getValue().asString());
    }

    @Test
    @DisplayName("equals and hashCode should use all value fields")
    void shouldImplementEqualsAndHashCodeByValue() {
        TraceState ts1 = new TraceStateImpl().set("vendor", AnyValueFactory.ofString("value"));
        TraceState ts2 = new TraceStateImpl().set("vendor", AnyValueFactory.ofString("value"));

        SpanContextImpl context1 = new SpanContextImpl(TRACE_ID, SPAN_ID, (byte) 0x01, false, ts1);
        SpanContextImpl context2 = new SpanContextImpl(TRACE_ID, SPAN_ID, (byte) 0x01, false, ts2);
        SpanContextImpl different = new SpanContextImpl(TRACE_ID, SPAN_ID, (byte) 0x00, false, ts1);
        SpanContextImpl remote = new SpanContextImpl(TRACE_ID, SPAN_ID, (byte) 0x01, true, ts1);
        SpanContextImpl differentTraceId = new SpanContextImpl(
                "4b8efff798038103d269b633813fc60c",
                SPAN_ID,
                (byte) 0x01,
                false,
                ts1);
        SpanContextImpl differentSpanId = new SpanContextImpl(
                TRACE_ID,
                "ddd19b7ec3c1b174",
                (byte) 0x01,
                false,
                ts1);
        SpanContextImpl differentTraceState = new SpanContextImpl(
                TRACE_ID,
                SPAN_ID,
                (byte) 0x01,
                false,
                new TraceStateImpl().set("vendor", AnyValueFactory.ofString("other")));

        assertEquals(context1, context1);
        assertEquals(context1, context2);
        assertEquals(context1.hashCode(), context2.hashCode());
        assertNotEquals(context1, different);
        assertNotEquals(context1, remote);
        assertNotEquals(context1, differentTraceId);
        assertNotEquals(context1, differentSpanId);
        assertNotEquals(context1, differentTraceState);
        assertNotEquals(context1.hashCode(), remote.hashCode());
        assertNotEquals(context1, null);
        assertNotEquals(context1, "not-a-span-context");
    }

    @Test
    @DisplayName("constructor should throw in strict mode for null and invalid IDs")
    void shouldThrowInStrictModeForNullAndInvalidIds() {
        assertThrows(IllegalArgumentException.class,
                () -> new SpanContextImpl(null, SPAN_ID, (byte) 0x00, false, new TraceStateImpl()));
        assertThrows(IllegalArgumentException.class,
                () -> new SpanContextImpl(TRACE_ID, "abc", (byte) 0x00, false, new TraceStateImpl()));
    }

    @Test
    @DisplayName("constructor should fallback in lenient mode for invalid IDs and null trace state")
    void shouldFallbackInLenientModeForInvalidIdsAndNullTraceState() {
        setLenient(true);

        SpanContextImpl context = new SpanContextImpl(
                null,
                "invalid-span-id",
                (byte) 0x00,
                false,
                null
        );

        assertEquals("00000000000000000000000000000000", context.getTraceId());
        assertEquals("0000000000000000", context.getSpanId());
        assertArrayEquals(new byte[16], context.getTraceIdBytes());
        assertArrayEquals(new byte[8], context.getSpanIdBytes());
        assertFalse(context.isValid());
        assertNotNull(context.getTraceState());
        assertTrue(context.getTraceState().getValues().isEmpty());
    }

    @Test
    @DisplayName("sampled and random should be derived from trace-flags bits")
    void shouldDeriveSampledAndRandomFromTraceFlags() {
        SpanContextImpl none = new SpanContextImpl(
                TRACE_ID,
                SPAN_ID,
                (byte) 0x00,
                false,
                new TraceStateImpl()
        );
        SpanContextImpl sampledOnly = new SpanContextImpl(
                TRACE_ID,
                SPAN_ID,
                (byte) 0x01,
                false,
                new TraceStateImpl()
        );
        SpanContextImpl randomOnly = new SpanContextImpl(
                TRACE_ID,
                SPAN_ID,
                (byte) 0x02,
                false,
                new TraceStateImpl()
        );
        SpanContextImpl both = new SpanContextImpl(
                TRACE_ID,
                SPAN_ID,
                (byte) 0x03,
                false,
                new TraceStateImpl()
        );

        assertFalse(none.isSampled());
        assertFalse(none.isRandom());
        assertTrue(sampledOnly.isSampled());
        assertFalse(sampledOnly.isRandom());
        assertFalse(randomOnly.isSampled());
        assertTrue(randomOnly.isRandom());
        assertTrue(both.isSampled());
        assertTrue(both.isRandom());
    }

    @Test
    @DisplayName("validity should be derived from non-zero trace and span IDs")
    void shouldDeriveValidityFromTraceAndSpanIds() {
        SpanContextImpl valid = new SpanContextImpl(
                TRACE_ID,
                SPAN_ID,
                (byte) 0x00,
                false,
                new TraceStateImpl()
        );
        SpanContextImpl zeroTraceId = new SpanContextImpl(
                "00000000000000000000000000000000",
                SPAN_ID,
                (byte) 0x00,
                false,
                new TraceStateImpl()
        );
        SpanContextImpl zeroSpanId = new SpanContextImpl(
                TRACE_ID,
                "0000000000000000",
                (byte) 0x00,
                false,
                new TraceStateImpl()
        );

        assertTrue(valid.isValid());
        assertFalse(zeroTraceId.isValid());
        assertFalse(zeroSpanId.isValid());
    }

    @Test
    @DisplayName("default constructor should initialize canonical invalid values")
    void defaultConstructorShouldInitializeCanonicalInvalidValues() {
        SpanContextImpl context = new SpanContextImpl();

        assertEquals("00000000000000000000000000000000", context.getTraceId());
        assertEquals("0000000000000000", context.getSpanId());
        assertArrayEquals(new byte[16], context.getTraceIdBytes());
        assertArrayEquals(new byte[8], context.getSpanIdBytes());
        assertTrue(context.getTraceState().getValues().isEmpty());
        assertFalse(context.isSampled());
        assertFalse(context.isRandom());
        assertFalse(context.isValid());
        assertFalse(context.isRemote());
    }

    @Test
    @DisplayName("private parse helper should throw in strict mode on malformed hexadecimal input")
    void parseHelperShouldThrowInStrictModeOnMalformedInput() throws Exception {
        setLenient(false);
        Method parseMethod = parseHexMethod();

        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> parseMethod.invoke(null, "f", 1, "traceId"));

        assertTrue(ex.getCause() instanceof IllegalArgumentException);
    }

    @Test
    @DisplayName("private parse helper should return zeroed array in lenient mode on malformed input")
    void parseHelperShouldReturnZeroedArrayInLenientModeOnMalformedInput() throws Exception {
        setLenient(true);
        Method parseMethod = parseHexMethod();

        byte[] parsed = (byte[]) parseMethod.invoke(null, "f", 4, "spanId");

        assertArrayEquals(new byte[4], parsed);
    }

    @Test
    @DisplayName("trace state helper methods should handle nulls and malformed entries")
    void traceStateHelpersShouldHandleNullsAndMalformedEntries() throws Exception {
        Method equalsMethod = traceStateEqualsMethod();
        Method hashMethod = traceStateHashMethod();

        TraceState withSingleEntry = traceStateOf(Collections.singletonList(
                new KeyValueImpl("k", AnyValueFactory.ofString("v"))));
        TraceState withNullEntry = traceStateOf(Collections.<com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue>singletonList(null));
        TraceState withNullEntryCopy = traceStateOf(Collections.<com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue>singletonList(null));
        TraceState withDifferentKey = traceStateOf(Collections.singletonList(
                new KeyValueImpl("other", AnyValueFactory.ofString("v"))));
        TraceState withDifferentValue = traceStateOf(Collections.singletonList(
                new KeyValueImpl("k", AnyValueFactory.ofString("x"))));
        TraceState withTwoEntries = traceStateOf(Arrays.asList(
                new KeyValueImpl("k", AnyValueFactory.ofString("v")),
                new KeyValueImpl("k2", AnyValueFactory.ofString("v2"))));
        TraceState emptyState = traceStateOf(Collections.<com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue>emptyList());
        TraceState withNullValue = traceStateOf(Collections.<com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue>singletonList(
                new com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue() {
                    @Override
                    public String getKey() {
                        return "k";
                    }

                    @Override
                    public com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue getValue() {
                        return null;
                    }
                }));
        TraceState withNullValueCopy = traceStateOf(Collections.<com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue>singletonList(
                new com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue() {
                    @Override
                    public String getKey() {
                        return "k";
                    }

                    @Override
                    public com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue getValue() {
                        return null;
                    }
                }));

        assertTrue((boolean) equalsMethod.invoke(null, withSingleEntry, withSingleEntry));
        assertFalse((boolean) equalsMethod.invoke(null, withSingleEntry, null));
        assertFalse((boolean) equalsMethod.invoke(null, null, withSingleEntry));
        assertTrue((boolean) equalsMethod.invoke(null, withNullEntry, withNullEntryCopy));
        assertFalse((boolean) equalsMethod.invoke(null, withNullEntry, withSingleEntry));
        assertFalse((boolean) equalsMethod.invoke(null, withSingleEntry, withNullEntry));
        assertFalse((boolean) equalsMethod.invoke(null, withSingleEntry, withDifferentKey));
        assertFalse((boolean) equalsMethod.invoke(null, withSingleEntry, withDifferentValue));
        assertFalse((boolean) equalsMethod.invoke(null, withSingleEntry, withNullValue));
        assertFalse((boolean) equalsMethod.invoke(null, withNullValue, withSingleEntry));
        assertTrue((boolean) equalsMethod.invoke(null, withNullValue, withNullValueCopy));
        assertFalse((boolean) equalsMethod.invoke(null, withSingleEntry, withTwoEntries));
        assertFalse((boolean) equalsMethod.invoke(null, withTwoEntries, withSingleEntry));
        assertFalse((boolean) equalsMethod.invoke(null, emptyState, withSingleEntry));
        assertFalse((boolean) equalsMethod.invoke(null, withSingleEntry, emptyState));

        assertEquals(1, hashMethod.invoke(null, new Object[]{null}));
        assertNotEquals(1, hashMethod.invoke(null, withNullEntry));
    }

    @Test
    @DisplayName("implementation should not declare legacy cached/state fields")
    void shouldNotDeclareLegacyFields() {
        boolean hasTraceIdBytesField = Arrays.stream(SpanContextImpl.class.getDeclaredFields())
                .anyMatch(field -> "traceIdBytes".equals(field.getName()));
        boolean hasSpanIdBytesField = Arrays.stream(SpanContextImpl.class.getDeclaredFields())
                .anyMatch(field -> "spanIdBytes".equals(field.getName()));
        boolean hasSampledField = Arrays.stream(SpanContextImpl.class.getDeclaredFields())
                .anyMatch(field -> "sampled".equals(field.getName()));
        boolean hasRandomField = Arrays.stream(SpanContextImpl.class.getDeclaredFields())
                .anyMatch(field -> "random".equals(field.getName()));
        boolean hasValidField = Arrays.stream(SpanContextImpl.class.getDeclaredFields())
                .anyMatch(field -> "valid".equals(field.getName()));

        assertFalse(hasTraceIdBytesField);
        assertFalse(hasSpanIdBytesField);
        assertFalse(hasSampledField);
        assertFalse(hasRandomField);
        assertFalse(hasValidField);
    }

    private static byte[] hexToBytes(final String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            int high = Character.digit(hex.charAt(i), 16);
            int low = Character.digit(hex.charAt(i + 1), 16);
            out[i / 2] = (byte) ((high << 4) + low);
        }
        return out;
    }

    private static void setLenient(final boolean value) {
        OpenTelemetryAttributeValidator.setLenientModeForTests(value);
    }

    private static Method parseHexMethod() throws NoSuchMethodException {
        Method method = SpanContextImpl.class.getDeclaredMethod(
                "parseHexIdToBytes",
                String.class,
                int.class,
                String.class);
        method.setAccessible(true);
        return method;
    }

    private static Method traceStateEqualsMethod() throws NoSuchMethodException {
        Method method = SpanContextImpl.class.getDeclaredMethod(
                "traceStateEquals",
                TraceState.class,
                TraceState.class);
        method.setAccessible(true);
        return method;
    }

    private static Method traceStateHashMethod() throws NoSuchMethodException {
        Method method = SpanContextImpl.class.getDeclaredMethod("traceStateHash", TraceState.class);
        method.setAccessible(true);
        return method;
    }

    private static TraceState traceStateOf(final Collection<com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue> values) {
        return new TraceState() {
            @Override
            public com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue get(final String key) {
                return null;
            }

            @Override
            public TraceState set(final String key, final com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue value) {
                return this;
            }

            @Override
            public TraceState delete(final String key) {
                return this;
            }

            @Override
            public Collection<com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue> getValues() {
                return values;
            }
        };
    }
}
