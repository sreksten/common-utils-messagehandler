package com.threeamigos.common.util.interfaces.messagehandler.otel;

/**
 * Span status code as defined by the OpenTelemetry trace API.
 * <p>
 * Specification reference:
 * <a href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/api.md#set-status">OpenTelemetry Trace API: Set Status</a>.
 *
 * @author Stefano Reksten
 */
public enum StatusCode {
    UNSET,
    OK,
    ERROR
}
