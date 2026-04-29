package com.threeamigos.common.util.interfaces.messagehandler.otel;

/**
 * Span status code
 * <p>
 * Specification reference:
 * <ul>
 *   <li><a href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/api.md#set-status">
 *       OpenTelemetry Trace API: Set Status</a></li>
 * </ul>
 *
 * @author Stefano Reksten
 */
public enum StatusCode {
    UNSET,
    OK,
    ERROR
}
