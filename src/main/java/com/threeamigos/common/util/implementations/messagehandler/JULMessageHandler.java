package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link com.threeamigos.common.util.interfaces.messagehandler.MessageHandler} implementation that
 * bridges to {@link java.util.logging} (JUL).
 * <p>
 * Message levels are mapped to JUL levels as follows:
 * <ul>
 *   <li>info → {@link java.util.logging.Level#INFO}</li>
 *   <li>warn → {@link java.util.logging.Level#WARNING}</li>
 *   <li>error, fatal → {@link java.util.logging.Level#SEVERE}</li>
 *   <li>debug → {@link java.util.logging.Level#FINER}</li>
 *   <li>trace → {@link java.util.logging.Level#FINEST}</li>
 *   <li>exception → {@link java.util.logging.Level#SEVERE} (with the exception attached as a {@link Throwable})</li>
 * </ul>
 *
 * @author Stefano Reksten
 */
public class JULMessageHandler extends AbstractMessageHandler {

    private final Logger logger;
    private final LogRecordFormatter logRecordFormatter;

    /**
     * Creates a {@code JULMessageHandler} that delegates to the given {@link Logger}.
     *
     * @param logger the JUL logger to delegate to; must not be {@code null}
     * @throws NullPointerException if {@code logger} is {@code null}
     */
    public JULMessageHandler(Logger logger) {
        this(logger, null, null);
    }

    public JULMessageHandler(final Logger logger,
                             final @Nullable LogRecordFactory logRecordFactory,
                             final @Nullable LogRecordFormatter logRecordFormatter) {
        this.logger = Objects.requireNonNull(logger, MessageHandlerResourceBundle.get("loggerCannotBeNull"));
        this.logRecordFormatter = logRecordFormatter;
    }

    /**
     * Creates a {@code JULMessageHandler} that delegates to a {@link Logger} looked up by name.
     * <p>
     * The logger is obtained via {@link Logger#getLogger(String)}, which reuses an existing
     * instance when one with the given name has already been created.
     *
     * @param loggerName the name of the JUL logger to look up; must not be {@code null} or blank
     * @throws NullPointerException     if {@code loggerName} is {@code null}
     * @throws IllegalArgumentException if {@code loggerName} is blank
     */
    public JULMessageHandler(String loggerName) {
        this(loggerName, null, null);
    }

    public JULMessageHandler(final String loggerName,
                             final @Nullable LogRecordFactory logRecordFactory,
                             final @Nullable LogRecordFormatter logRecordFormatter) {
        Objects.requireNonNull(loggerName, MessageHandlerResourceBundle.get("loggerNameCannotBeNull"));
        if (loggerName.trim().isEmpty()) {
            throw new IllegalArgumentException(MessageHandlerResourceBundle.get("loggerNameCannotBeEmpty"));
        }
        this.logger = Logger.getLogger(loggerName);
        this.logRecordFormatter = logRecordFormatter;
    }

    @Override
    public void handleMessage(@Nonnull SeverityNumber level, @Nonnull String message) {
        String rendered = formatMessage(level, message);
        switch (level) {
            case INFO:
            case INFO2:
            case INFO3:
            case INFO4:
                logger.log(Level.INFO, rendered);
                break;
            case WARN:
            case WARN2:
            case WARN3:
            case WARN4:
                logger.log(Level.WARNING, rendered);
                break;
            case ERROR:
            case ERROR2:
            case ERROR3:
            case ERROR4:
            case FATAL:
            case FATAL2:
            case FATAL3:
            case FATAL4:
                logger.log(Level.SEVERE, rendered);
                break;
            case DEBUG:
            case DEBUG2:
            case DEBUG3:
            case DEBUG4:
                logger.log(Level.FINER, rendered);
                break;
            case TRACE:
            case TRACE2:
            case TRACE3:
            case TRACE4:
                logger.log(Level.FINEST, rendered);
                break;
            default:
                logger.log(Level.INFO, "Unknown severity level: " + level + ". Logging as INFO. " + rendered);
                break;
        }
    }

    @Override
    protected void handleExceptionInternal(@Nonnull String message, @Nonnull Throwable throwable) {
        String rendered = formatExceptionMessage(message, throwable);
        logger.log(Level.SEVERE, ThrowableMessageFormatter.withPrefix(rendered, throwable), throwable);
    }

    private String formatMessage(final SeverityNumber level, final String message) {
        if (logRecordFormatter == null) {
            return message;
        }
        LogRecord logRecord = createLogRecord(level, message);
        return logRecordFormatter.format(logRecord);
    }

    private String formatExceptionMessage(final String message, final Throwable throwable) {
        if (logRecordFormatter == null) {
            return message;
        }
        LogRecord logRecord = createLogRecord(message, throwable);
        return logRecordFormatter.format(logRecord);
    }
}
