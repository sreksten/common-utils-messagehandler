package com.threeamigos.common.util.implementations.messagehandler.otel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SpanContextImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class SpanContextImplUnitTest {

    private static final boolean ORIGINAL_LENIENT = OpenTelemetryAttributeValidator.isLenientMode();

    @BeforeEach
    void setStrictModeByDefault() {
        setLenient(false);
    }

    @AfterEach
    void restoreLenientMode() {
        setLenient(ORIGINAL_LENIENT);
    }

    @Test
    @DisplayName("getTraceIdBytes() and getSpanIdBytes() should convert valid hex IDs to bytes")
    void shouldConvertValidIdsToBytes() {
        SpanContextImpl context = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x03,
                false,
                null
        );

        assertArrayEquals(hexToBytes("5b8efff798038103d269b633813fc60c"), context.getTraceIdBytes());
        assertArrayEquals(hexToBytes("eee19b7ec3c1b174"), context.getSpanIdBytes());
        assertTrue(context.isSampled());
        assertTrue(context.isRandom());
        assertTrue(context.isValid());
    }

    @Test
    @DisplayName("getTraceIdBytes() should fail fast in strict mode for invalid trace IDs")
    void shouldFailFastForInvalidTraceIdInStrictMode() {
        SpanContextImpl context = new SpanContextImpl(
                "00000000000000000000000000000000",
                "eee19b7ec3c1b174",
                (byte) 0x01,
                false,
                null
        );

        assertThrows(IllegalArgumentException.class, context::getTraceIdBytes);
    }

    @Test
    @DisplayName("getSpanIdBytes() should fail fast in strict mode for invalid span IDs")
    void shouldFailFastForInvalidSpanIdInStrictMode() {
        SpanContextImpl context = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "abc",
                (byte) 0x01,
                false,
                null
        );

        assertThrows(IllegalArgumentException.class, context::getSpanIdBytes);
    }

    @Test
    @DisplayName("invalid IDs should return empty byte arrays in lenient mode")
    void shouldReturnEmptyArraysForInvalidIdsInLenientMode() {
        setLenient(true);
        SpanContextImpl context = new SpanContextImpl(
                "invalid-trace-id",
                "invalid-span-id",
                (byte) 0x00,
                false,
                null
        );

        assertEquals(0, context.getTraceIdBytes().length);
        assertEquals(0, context.getSpanIdBytes().length);
    }

    @Test
    @DisplayName("sampled and random should be derived from trace-flags bits")
    void shouldDeriveSampledAndRandomFromTraceFlags() {
        SpanContextImpl none = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x00,
                false,
                null
        );
        SpanContextImpl sampledOnly = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x01,
                false,
                null
        );
        SpanContextImpl randomOnly = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x02,
                false,
                null
        );
        SpanContextImpl both = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x03,
                false,
                null
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
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x00,
                false,
                null
        );
        SpanContextImpl zeroTraceId = new SpanContextImpl(
                "00000000000000000000000000000000",
                "eee19b7ec3c1b174",
                (byte) 0x00,
                false,
                null
        );
        SpanContextImpl zeroSpanId = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "0000000000000000",
                (byte) 0x00,
                false,
                null
        );
        SpanContextImpl invalidFormat = new SpanContextImpl(
                "abc",
                "eee19b7ec3c1b174",
                (byte) 0x00,
                false,
                null
        );

        assertTrue(valid.isValid());
        assertFalse(zeroTraceId.isValid());
        assertFalse(zeroSpanId.isValid());
        assertFalse(invalidFormat.isValid());
    }

    @Test
    @DisplayName("default constructor should initialize empty IDs and empty byte arrays")
    void defaultConstructorShouldInitializeEmptyIdsAndArrays() {
        setLenient(true);
        SpanContextImpl context = new SpanContextImpl();

        assertEquals("", context.getTraceId());
        assertEquals("", context.getSpanId());
        assertEquals(0, context.getTraceIdBytes().length);
        assertEquals(0, context.getSpanIdBytes().length);
        assertTrue(context.getTraceState().isEmpty());
        assertFalse(context.isSampled());
        assertFalse(context.isRandom());
        assertFalse(context.isValid());
        assertFalse(context.isRemote());
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
}
