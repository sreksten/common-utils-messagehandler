package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import com.threeamigos.common.util.interfaces.messagehandler.otel.TraceState;

import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An immutable implementation of a {@link SpanContext}.
 *
 * @author Stefano Reksten
 */
public class SpanContextImpl implements SpanContext {

    private static final byte TRACE_FLAG_SAMPLED = 0x01;
    private static final byte TRACE_FLAG_RANDOM = 0x02;
    private static final Pattern TRACE_ID_FORMAT_PATTERN = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern SPAN_ID_FORMAT_PATTERN = Pattern.compile("[0-9a-f]{16}");
    private static final String INVALID_TRACE_ID = "00000000000000000000000000000000";
    private static final String INVALID_SPAN_ID = "0000000000000000";

    private final String traceId;
    private final String spanId;
    private final byte traceFlags;
    private final boolean remote;
    private final TraceState traceState;

    public SpanContextImpl() {
        this(INVALID_TRACE_ID, INVALID_SPAN_ID, (byte) 0, false, new TraceStateImpl());
    }

    public SpanContextImpl(final String traceId,
                           final String spanId,
                           final byte traceFlags,
                           final boolean remote,
                           final TraceState traceState) {
        this.traceId = normalizeTraceId(traceId);
        this.spanId = normalizeSpanId(spanId);
        this.traceFlags = traceFlags;
        this.remote = remote;
        this.traceState = traceState == null ? new TraceStateImpl() : traceState;
    }

    @Override
    public String getTraceId() {
        return traceId;
    }

    @Override
    public byte[] getTraceIdBytes() {
        return parseHexIdToBytes(traceId, 16, "traceId");
    }

    @Override
    public String getSpanId() {
        return spanId;
    }

    @Override
    public byte[] getSpanIdBytes() {
        return parseHexIdToBytes(spanId, 8, "spanId");
    }

    @Override
    public byte getTraceFlags() {
        return traceFlags;
    }

    @Override
    public boolean isSampled() {
        return (traceFlags & TRACE_FLAG_SAMPLED) == TRACE_FLAG_SAMPLED;
    }

    @Override
    public boolean isRandom() {
        return (traceFlags & TRACE_FLAG_RANDOM) == TRACE_FLAG_RANDOM;
    }

    @Override
    public boolean isValid() {
        return !INVALID_TRACE_ID.equals(traceId) && !INVALID_SPAN_ID.equals(spanId);
    }

    @Override
    public boolean isRemote() {
        return remote;
    }

    @Override
    public TraceState getTraceState() {
        return traceState;
    }

    @Override
    public int hashCode() {
        int result = traceId.hashCode();
        result = 31 * result + spanId.hashCode();
        result = 31 * result + traceFlags;
        result = 31 * result + (remote ? 1 : 0);
        result = 31 * result + traceStateHash(traceState);
        return result;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SpanContextImpl)) {
            return false;
        }
        SpanContextImpl that = (SpanContextImpl) other;
        return traceFlags == that.traceFlags
                && remote == that.remote
                && traceId.equals(that.traceId)
                && spanId.equals(that.spanId)
                && traceStateEquals(traceState, that.traceState);
    }

    private static boolean traceStateEquals(final TraceState left, final TraceState right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }

        Iterator<KeyValue> leftIterator = left.getValues().iterator();
        Iterator<KeyValue> rightIterator = right.getValues().iterator();
        while (leftIterator.hasNext() && rightIterator.hasNext()) {
            KeyValue leftValue = leftIterator.next();
            KeyValue rightValue = rightIterator.next();
            if (!Objects.equals(leftValue == null ? null : leftValue.getKey(),
                    rightValue == null ? null : rightValue.getKey())) {
                return false;
            }
            if (!Objects.equals(leftValue == null ? null : leftValue.getValue(),
                    rightValue == null ? null : rightValue.getValue())) {
                return false;
            }
        }
        return !leftIterator.hasNext() && !rightIterator.hasNext();
    }

    private static int traceStateHash(final TraceState state) {
        int hash = 1;
        if (state == null) {
            return hash;
        }
        for (KeyValue keyValue : state.getValues()) {
            hash = 31 * hash + Objects.hash(
                    keyValue == null ? null : keyValue.getKey(),
                    keyValue == null ? null : keyValue.getValue());
        }
        return hash;
    }

    private static byte[] parseHexIdToBytes(final String id,
                                            final int expectedByteLength,
                                            final String fieldName) {
        byte[] result = new byte[expectedByteLength];
        try {
            for (int i = 0; i < id.length(); i += 2) {
                int high = Character.digit(id.charAt(i), 16);
                int low = Character.digit(id.charAt(i + 1), 16);
                result[i / 2] = (byte) ((high << 4) + low);
            }
        } catch (RuntimeException ex) {
            OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                    "invalidHexadecimalFieldValue",
                    fieldName,
                    id));
            return new byte[expectedByteLength];
        }
        return result;
    }

    private static String normalizeTraceId(final String id) {
        return normalizeId(id, "traceId", TRACE_ID_FORMAT_PATTERN, INVALID_TRACE_ID);
    }

    private static String normalizeSpanId(final String id) {
        return normalizeId(id, "spanId", SPAN_ID_FORMAT_PATTERN, INVALID_SPAN_ID);
    }

    private static String normalizeId(final String id,
                                      final String fieldName,
                                      final Pattern formatPattern,
                                      final String fallback) {
        if (id == null) {
            OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                    "builderFieldMustNotBeNull",
                    fieldName));
            return fallback;
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        if (!formatPattern.matcher(normalized).matches()) {
            OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                    "invalidFieldValue",
                    fieldName,
                    id));
            return fallback;
        }
        return normalized;
    }

}
