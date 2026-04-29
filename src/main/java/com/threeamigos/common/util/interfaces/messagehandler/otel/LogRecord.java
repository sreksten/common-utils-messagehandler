package com.threeamigos.common.util.interfaces.messagehandler.otel;

import java.time.Instant;
import java.util.List;

/**
 * A representation of an OpenTelemetry Log Record.
 * <p>
 * Specification references:
 * <ul>
 *   <li><a href="https://opentelemetry.io/docs/specs/otel/logs/data-model/">OpenTelemetry Logs Data
 *   Model</a></li>
 *   <li><a href="https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/logs/v1/logs.proto">OTLP
 *   logs.proto (LogRecord)</a></li>
 *   <li><a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a></li>
 * </ul>
 *
 * {@code timestamp} defaults to {@link Instant#now()} at construction time, reflecting the moment
 * the log record is created. All other fields are optional: implementations may return {@code null} for absent
 * string/object fields, {@code 0} / {@link SeverityNumber#UNSPECIFIED} for absent numeric/enum fields, and empty lists
 * for absent collection fields.<br/> Those fields may be set via the corresponding setters.
 * <p>
 * The {@link LogRecordFormatter} (NOT part of the OpenTelemetry specification) will omit absent fields
 * from the output.
 * <p>
 * Hint: if you're looking where to look for:
 * <ul>
 *     <li>Fully qualified method name: <code>code.function.name</code></li>
 *     <li>Class name: <code>code.namespace</code> (deprecated attribute)</li>
 *     <li>Logger class name (e.g., Java logger name: <code>InstrumentationScope.name</code>)</li>
 *     <li>Exception message: <code>exception.message</code> in attributes (e.g., "Division by zero")</li>
 *     <li>Exception type: <code>exception.type</code> in attributes (e.g., java.net.ConnectException)</li>
 *     <li>Exception stacktrace: <code>exception.stacktrace</code> in attributes</li>
 * </ul>
 *
 * @author Stefano Reksten
 */
public interface LogRecord {

    /**
     * @return time when the event occurred, or {@code null} if not set.
     *         {@code LogRecordImpl} always returns a non-null value (defaults to {@link java.time.Instant#now()}
     *         at construction time and normalizes invalid/null setter inputs to a current timestamp);
     *         {@code null} is only possible for custom implementations.
     */
    Instant getTimestamp();

    /**
     * @return time when the event was observed by the collector, or {@code null} if not set.
     */
    Instant getObservedTimestamp();

    /**
     * @return W3C Trace Context trace ID (32 lowercase hex characters), or {@code null} if not set.
     */
    String getTraceId();

    /**
     * @return W3C Trace Context span ID (16 lowercase hex characters), or {@code null} if not set.
     */
    String getSpanId();

    /**
     * @return W3C Trace Context {@code TraceFlags} byte value in the range {@code 0x00–0xFF};
     *         {@code 0} means not set. Bit 0 is the sampled flag.
     */
    int getTraceFlags();

    /**
     * @return the severity text (also known as log level), e.g. {@code "INFO"}, or {@code null} if not set.
     */
    String getSeverityText();

    /**
     * @return the numerical severity; {@link SeverityNumber#UNSPECIFIED} means not set.
     */
    SeverityNumber getSeverityNumber();

    /**
     * @return the body of the log record as a typed {@link AnyValue}, or {@code null} if not set.
     */
    AnyValue getBody();

    /**
     * @return the resource that produced this log record or {@code null} if not set.
     */
    Resource getResource();

    /**
     * @return the instrumentation scope that emitted this log record or {@code null} if not set.
     */
    InstrumentationScope getInstrumentationScope();

    /**
     * @return additional key-value attributes attached to this log record; never {@code null}, may be empty.
     */
    List<KeyValue> getAttributes();

    /**
     * @return the event name that identifies the class / type of event or {@code null} if not set.
     */
    String getEventName();
}
