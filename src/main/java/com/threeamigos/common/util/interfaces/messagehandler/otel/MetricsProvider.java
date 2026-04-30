package com.threeamigos.common.util.interfaces.messagehandler.otel;

/**
 * OpenTelemetry-like metrics provider API.
 * <p>
 * This interface represents the provider role described in the OpenTelemetry Metrics API as
 * <em>MeterProvider</em>.
 * <p>
 * Specification references:
 * <ul>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/metrics/api/">OpenTelemetry Metrics API</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/metrics/api/#meterprovider">OpenTelemetry Metrics API:
 *   MeterProvider</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/metrics/api/#get-a-meter">OpenTelemetry Metrics API:
 *   Get a Meter</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/metrics/api/#concurrency-requirements">OpenTelemetry
 *   Metrics API: Concurrency requirements</a></li>
 * </ul>
 * Implementations are expected to be safe for concurrent use by default.
 *
 * @author Stefano Reksten
 */
public interface MetricsProvider {

    /**
     * Returns a meter with the provided instrumentation name.
     *
     * @param name instrumentation scope name
     * @return meter instance
     */
    default Meter getMeter(final String name) {
        return getMeter(name, null, null);
    }

    /**
     * Returns a meter with the provided instrumentation name and version.
     *
     * @param name instrumentation scope name
     * @param version instrumentation version
     * @return meter instance
     */
    default Meter getMeter(final String name, final String version) {
        return getMeter(name, version, null);
    }

    /**
     * Returns a meter with the provided scope metadata and no additional attributes.
     *
     * @param name instrumentation scope name
     * @param version instrumentation version
     * @param schemaUrl scope schema URL
     * @return meter instance
     */
    default Meter getMeter(final String name, final String version, final String schemaUrl) {
        return getMeter(name, version, schemaUrl, new KeyValue[0]);
    }

    /**
     * Returns a meter with the provided instrumentation scope metadata.
     *
     * @param name instrumentation scope name
     * @param version instrumentation version
     * @param schemaUrl scope schema URL
     * @param attributes scope attributes
     * @return meter instance
     */
    Meter getMeter(String name, String version, String schemaUrl, KeyValue... attributes);
}

