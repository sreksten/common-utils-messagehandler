package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Stefano Reksten
 */
public class SpanContextImpl implements SpanContext {

    private final String traceId;
    private final byte[] traceIdBytes;
    private final String spanId;
    private final byte[] spanIdBytes;
    private final boolean sampled;
    private final boolean random;
    private final boolean valid;
    private final boolean remote;
    private final List<KeyValue> traceState;

    public SpanContextImpl() {
        this("", new byte[0], "", new byte[0], false, false, false, false, Collections.emptyList());
    }

    public SpanContextImpl(final String traceId,
                           final byte[] traceIdBytes,
                           final String spanId,
                           final byte[] spanIdBytes,
                           final boolean sampled,
                           final boolean random,
                           final boolean valid,
                           final boolean remote,
                           final List<KeyValue> traceState) {
        this.traceId = traceId != null ? traceId : "";
        this.traceIdBytes = traceIdBytes != null ? traceIdBytes.clone() : new byte[0];
        this.spanId = spanId != null ? spanId : "";
        this.spanIdBytes = spanIdBytes != null ? spanIdBytes.clone() : new byte[0];
        this.sampled = sampled;
        this.random = random;
        this.valid = valid;
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
        return traceIdBytes.clone();
    }

    @Override
    public String getSpanId() {
        return spanId;
    }

    @Override
    public byte[] getSpanIdBytes() {
        return spanIdBytes.clone();
    }

    @Override
    public boolean isSampled() {
        return sampled;
    }

    @Override
    public boolean isRandom() {
        return random;
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public boolean isRemote() {
        return remote;
    }

    @Override
    public List<KeyValue> getTraceState() {
        return traceState;
    }
}
