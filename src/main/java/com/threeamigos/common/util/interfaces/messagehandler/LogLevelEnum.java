package com.threeamigos.common.util.interfaces.messagehandler;

/**
 * Lists the severity levels supported by the message-handler framework.
 * <p>
 * Levels are ordered from highest to lowest verbosity:
 * {@link #TRACE} &gt; {@link #DEBUG} &gt; {@link #INFO} &gt; {@link #WARN} &gt; {@link #ERROR} &gt; {@link #FATAL}.
 * {@link #EXCEPTION} is a special level for exception events, treated as equivalent to {@link #ERROR}.
 * See also {@link <a href="https://opentelemetry.io/docs/specs/otel/logs/data-model/">OpenTelemetry Logs Data Model</a>} for detailed descriptions of each level.
 *
 * @author Stefano Reksten
 */
public enum LogLevelEnum {

    /** Fine-grained trace messages for detailed diagnostic output. */
    TRACE,
    TRACE2,
    TRACE3,
    TRACE4,

    /** Debug messages intended for developers during development or troubleshooting. */
    DEBUG,
    DEBUG2,
    DEBUG3,
    DEBUG4,

    /** Informational messages intended for end users. */
    INFO,
    INFO2,
    INFO3,
    INFO4,

    /** Warning messages indicating potentially harmful situations. */
    WARN,
    WARN2,
    WARN3,
    WARN4,

    /** Error messages indicating a failure that has been handled. */
    ERROR,
    ERROR2,
    ERROR3,
    ERROR4,

    /** Fatal messages indicating a severe failure that may cause the application to abort. */
    FATAL,
    FATAL2,
    FATAL3,
    FATAL4,

    /** Exception events; treated as equivalent in severity to {@link #ERROR}. */
    EXCEPTION;

    public String getSeverityText() {
        return name();
    }

    public int getSeverityLevel() {
        return ordinal();
    }

}
