package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * OpenTelemetry {@link SpanContext} implementation.
 *
 * <p>Specification references:
 * <ul>
 *   <li>OpenTelemetry Trace API:
 *   https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/api.md</li>
 *   <li>W3C Trace Context Level 2:
 *   https://www.w3.org/TR/trace-context-2/</li>
 * </ul>
 *
 * @author Stefano Reksten
 */
public class SpanContextImpl implements SpanContext {

    private static final byte TRACE_FLAG_SAMPLED = 0x01;
    private static final byte TRACE_FLAG_RANDOM = 0x02;
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("(?!0{32})[0-9a-f]{32}");
    private static final Pattern SPAN_ID_PATTERN = Pattern.compile("(?!0{16})[0-9a-f]{16}");

    private final String traceId;
    private final String spanId;
    private final byte traceFlags;
    private final boolean remote;
    private final List<KeyValue> traceState;

    public SpanContextImpl() {
        this("", "", (byte) 0, false, Collections.emptyList());
    }

    public SpanContextImpl(final String traceId,
                           final String spanId,
                           final byte traceFlags,
                           final boolean remote,
                           final List<KeyValue> traceState) {
        this.traceId = normalizeId(traceId);
        this.spanId = normalizeId(spanId);
        this.traceFlags = traceFlags;
        this.remote = remote;
        this.traceState = traceState == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(traceState));
    }

    @Override
    public String getTraceId() {
        return traceId;
    }

    @Override
    public byte[] getTraceIdBytes() {
        return parseHexIdToBytes(traceId, TRACE_ID_PATTERN, 16, "traceId");
    }

    @Override
    public String getSpanId() {
        return spanId;
    }

    @Override
    public byte[] getSpanIdBytes() {
        return parseHexIdToBytes(spanId, SPAN_ID_PATTERN, 8, "spanId");
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
        return TRACE_ID_PATTERN.matcher(traceId).matches() && SPAN_ID_PATTERN.matcher(spanId).matches();
    }

    @Override
    public boolean isRemote() {
        return remote;
    }

    @Override
    public List<KeyValue> getTraceState() {
        return traceState;
    }

    private static byte[] parseHexIdToBytes(final String id,
                                            final Pattern validator,
                                            final int expectedByteLength,
                                            final String fieldName) {
        if (id == null) {
            OpenTelemetryAttributeValidator.handle(MessageHandlerResourceBundle.format(
                    "builderFieldMustNotBeNull",
                    fieldName));
            return new byte[0];
        }

        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return new byte[0];
        }
        if (!validator.matcher(normalized).matches()) {
            OpenTelemetryAttributeValidator.handle("Invalid " + fieldName + " value: " + id);
            return new byte[0];
        }

        byte[] result = new byte[expectedByteLength];
        for (int i = 0; i < normalized.length(); i += 2) {
            int high = Character.digit(normalized.charAt(i), 16);
            int low = Character.digit(normalized.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                OpenTelemetryAttributeValidator.handle("Invalid hexadecimal " + fieldName + " value: " + id);
                return new byte[0];
            }
            result[i / 2] = (byte) ((high << 4) + low);
        }
        return result;
    }

    private static String normalizeId(final String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
