package com.threeamigos.common.util.interfaces.messagehandler.otel;

/**
 * OpenTelemetry-like logger provider API.
 * <p>
 * Specification references:
 * <ul>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/logs/api/">OpenTelemetry Logs API</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/logs/api/#loggerprovider">OpenTelemetry Logs API:
 *   LoggerProvider</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/logs/api/#get-a-logger">OpenTelemetry Logs API:
 *   Get a Logger</a></li>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/logs/api/#concurrency-requirements">OpenTelemetry Logs
 *   API: Concurrency requirements</a></li>
 * </ul>
 * Implementations are expected to be safe for concurrent use by default.
 *
 * @author Stefano Reksten
 */
public interface LoggerProvider {

    /**
     * Returns a logger with the provided instrumentation name.
     *
     * @param name instrumentation scope name
     * @return logger instance
     */
    default Logger getLogger(final String name) {
        return getLogger(name, null, null);
    }

    /**
     * Returns a logger with the provided instrumentation name and version.
     *
     * @param name instrumentation scope name
     * @param version instrumentation version
     * @return logger instance
     */
    default Logger getLogger(final String name, final String version) {
        return getLogger(name, version, null);
    }

    /**
     * Returns a logger with the provided scope metadata and no additional attributes.
     *
     * @param name instrumentation scope name
     * @param version instrumentation version
     * @param schemaUrl scope schema URL
     * @return logger instance
     */
    default Logger getLogger(final String name, final String version, final String schemaUrl) {
        return getLogger(name, version, schemaUrl, new KeyValue[0]);
    }

    /**
     * Returns a logger with the provided instrumentation scope metadata.
     *
     * @param name instrumentation scope name
     * @param version instrumentation version
     * @param schemaUrl scope schema URL
     * @param attributes scope attributes
     * @return logger instance
     */
    Logger getLogger(String name, String version, String schemaUrl, KeyValue... attributes);
}

