package com.threeamigos.common.util.interfaces.messagehandler.otel;

/**
 * OpenTelemetry-like meter API.
 * <p>
 * Specification references:
 * <ul>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/metrics/api/">OpenTelemetry Metrics API</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/metrics/api/#meter">OpenTelemetry Metrics API: Meter</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/metrics/api/#meter-operations">OpenTelemetry Metrics API:
 *   Meter operations</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/metrics/api/#concurrency-requirements">OpenTelemetry
 *   Metrics API: Concurrency requirements</a></li>
 * </ul>
 * Implementations are expected to be safe for concurrent use by default.
 * <p>
 * Note: instrument-specific APIs are intentionally introduced incrementally in this project. This interface provides
 * the foundational meter identity contract used by {@link MetricsProvider}.
 *
 * @author Stefano Reksten
 */
public interface Meter {

    /**
     * Returns the effective instrumentation scope for this meter.
     *
     * @return effective instrumentation scope, or {@code null} when unavailable
     */
    default InstrumentationScope getInstrumentationScope() {
        return null;
    }
}

