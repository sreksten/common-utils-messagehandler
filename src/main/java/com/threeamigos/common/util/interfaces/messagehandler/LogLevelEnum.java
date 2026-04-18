package com.threeamigos.common.util.interfaces.messagehandler;

/**
 * Lists the severity levels supported by the message-handler framework.
 * <p>
 * Levels are ordered from highest to lowest verbosity:
 * {@link #TRACE} &gt; {@link #DEBUG} &gt; {@link #INFO} &gt; {@link #WARN} &gt; {@link #ERROR} &gt; {@link #FATAL}.
 * {@link #EXCEPTION} is a special level for exception events, treated as equivalent to {@link #ERROR}.
 *
 * @author Stefano Reksten
 */
public enum LogLevelEnum {

    /** Informational messages intended for end users. */
    INFO,

    /** Warning messages indicating potentially harmful situations. */
    WARN,

    /** Error messages indicating a failure that has been handled. */
    ERROR,

    /** Fatal messages indicating a severe failure that may cause the application to abort. */
    FATAL,

    /** Debug messages intended for developers during development or troubleshooting. */
    DEBUG,

    /** Fine-grained trace messages for detailed diagnostic output. */
    TRACE,

    /** Exception events; treated as equivalent in severity to {@link #ERROR}. */
    EXCEPTION

}
