package com.threeamigos.common.util.interfaces.messagehandler.otel;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * A Span identifies a single unit of work.
 * <p>
 * Specification reference:
 * <ul>
 *   <li><a href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/api.md#span-operations">
 *   OpenTelemetry Trace API: Span operations</a></li>
 *   <li><a href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/api.md#record-exception">
 *   OpenTelemetry Trace API: Record Exception</a></li>
 * </ul>
 *
 * @author Stefano Reksten
 */
public interface Span {

    /**
     * Returns the span context for this span.
     *
     * @return the span context
     */
    SpanContext getSpanContext();

    /**
     * Returns the instrumentation scope that produced this span.
     * <p>
     * This is optional metadata; may be {@code null}.
     *
     * @return emitting instrumentation scope, or {@code null} when unavailable
     */
    default InstrumentationScope getInstrumentationScope() {
        return null;
    }

    /**
     * Backward-compatible alias for {@link #getSpanContext()}.
     */
    default SpanContext getContext() {
        return getSpanContext();
    }

    boolean isRecording();

    void setAttribute(String key, AnyValue value);

    /**
     * Backward-compatible alias for {@link #setAttribute(String, AnyValue)}.
     */
    default void addAttribute(final String key, final AnyValue value) {
        setAttribute(key, value);
    }

    void addEvent(String name);

    void addEvent(String name, List<KeyValue> attributes);

    void addEvent(String name, List<KeyValue> attributes, Instant timestamp);

    /**
     * Backward-compatible API for pre-built event objects.
     */
    void addEvent(Event event);

    void addLink(SpanContext spanContext);

    void addLink(SpanContext spanContext, List<KeyValue> attributes);

    /**
     * Backward-compatible API for pre-built link objects.
     */
    void addLink(Link link);

    void setStatus(StatusCode statusCode);

    void setStatus(StatusCode statusCode, String description);

    void updateName(String name);

    void end();

    void end(Instant endTimestamp);

    void recordException(Throwable exception);

    void recordException(Throwable exception, List<KeyValue> additionalAttributes);

    /**
     * Wraps a {@link SpanContext} into a non-recording span.
     * <p>
     * Specification reference:
     * <a href="https://opentelemetry.io/docs/specs/otel/trace/api/#wrapping-a-spancontext-in-a-span">OpenTelemetry Trace API: wrapping a SpanContext in a Span</a>.
     *
     * @param spanContext context to wrap; when null an invalid context is used
     * @return a non-recording span
     */
    static Span wrap(final SpanContext spanContext) {
        return new Span() {
            private final SpanContext wrappedContext = spanContext == null ? new InvalidSpanContext() : spanContext;

            @Override
            public SpanContext getSpanContext() {
                return wrappedContext;
            }

            @Override
            public boolean isRecording() {
                return false;
            }

            @Override
            public void setAttribute(final String key, final AnyValue value) {
                // no-op by contract for non-recording spans
            }

            @Override
            public void addEvent(final String name) {
                // no-op by contract for non-recording spans
            }

            @Override
            public void addEvent(final String name, final List<KeyValue> attributes) {
                // no-op by contract for non-recording spans
            }

            @Override
            public void addEvent(final String name, final List<KeyValue> attributes, final Instant timestamp) {
                // no-op by contract for non-recording spans
            }

            @Override
            public void addEvent(final Event event) {
                // no-op by contract for non-recording spans
            }

            @Override
            public void addLink(final SpanContext spanContext) {
                // no-op by contract for non-recording spans
            }

            @Override
            public void addLink(final SpanContext spanContext, final List<KeyValue> attributes) {
                // no-op by contract for non-recording spans
            }

            @Override
            public void addLink(final Link link) {
                // no-op by contract for non-recording spans
            }

            @Override
            public void setStatus(final StatusCode statusCode) {
                // no-op by contract for non-recording spans
            }

            @Override
            public void setStatus(final StatusCode statusCode, final String description) {
                // no-op by contract for non-recording spans
            }

            @Override
            public void updateName(final String name) {
                // no-op by contract for non-recording spans
            }

            @Override
            public void end() {
                // no-op by contract for non-recording spans
            }

            @Override
            public void end(final Instant endTimestamp) {
                // no-op by contract for non-recording spans
            }

            @Override
            public void recordException(final Throwable exception) {
                // no-op by contract for non-recording spans
            }

            @Override
            public void recordException(final Throwable exception, final List<KeyValue> additionalAttributes) {
                // no-op by contract for non-recording spans
            }
        };
    }

    final class InvalidSpanContext implements SpanContext {

        @Override
        public String getTraceId() {
            return "00000000000000000000000000000000";
        }

        @Override
        public byte[] getTraceIdBytes() {
            return new byte[16];
        }

        @Override
        public String getSpanId() {
            return "0000000000000000";
        }

        @Override
        public byte[] getSpanIdBytes() {
            return new byte[8];
        }

        @Override
        public byte getTraceFlags() {
            return 0x00;
        }

        @Override
        public boolean isSampled() {
            return false;
        }

        @Override
        public boolean isRandom() {
            return false;
        }

        @Override
        public boolean isValid() {
            return false;
        }

        @Override
        public boolean isRemote() {
            return false;
        }

        @Override
        public TraceState getTraceState() {
            return new TraceState() {
                @Override
                public AnyValue get(final String key) {
                    return null;
                }

                @Override
                public TraceState set(final String key, final AnyValue value) {
                    return this;
                }

                @Override
                public TraceState delete(final String key) {
                    return this;
                }

                @Override
                public List<KeyValue> getValues() {
                    return Collections.emptyList();
                }
            };
        }
    }

}
