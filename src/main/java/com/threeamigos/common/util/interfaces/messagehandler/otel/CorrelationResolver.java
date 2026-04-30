package com.threeamigos.common.util.interfaces.messagehandler.otel;

import jakarta.annotation.Nullable;

/**
 * Shared correlation resolver contract for Trace, Logs, and Metrics components.
 * <p>
 * This interface provides the active correlation context used to enrich telemetry signals with
 * trace/span identifiers and optional instrumentation scope metadata.
 *
 * <p>
 * Specification references:
 * <ul>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/context/">OpenTelemetry Context</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/trace/api/">OpenTelemetry Trace API</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/logs/api/">OpenTelemetry Logs API</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/metrics/api/">OpenTelemetry Metrics API</a></li>
 * </ul>
 *
 * @author Stefano Reksten
 */
@FunctionalInterface
public interface CorrelationResolver {

    /**
     * Resolves the active span context to correlate logs/metrics with traces.
     *
     * @return active span context, or {@code null} if unavailable
     */
    @Nullable
    SpanContext resolveSpanContext();

    /**
     * Resolves the active instrumentation scope, when available.
     *
     * @return active instrumentation scope, or {@code null} if unavailable
     */
    default @Nullable InstrumentationScope resolveInstrumentationScope() {
        return null;
    }
}
