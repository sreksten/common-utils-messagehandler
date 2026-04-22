package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.logging.Level;

/**
 * A {@link MessageHandler} implementation that bridges to
 * <a href="https://logging.apache.org/log4j/2.x/">Apache Log4j 2</a>.
 * <p>
 * Message levels are mapped to Log4j 2 levels as follows:
 * <ul>
 *   <li>info  → {@link org.apache.logging.log4j.Level#INFO}</li>
 *   <li>warn  → {@link org.apache.logging.log4j.Level#WARN}</li>
 *   <li>error → {@link org.apache.logging.log4j.Level#ERROR}</li>
 *   <li>fatal → {@link org.apache.logging.log4j.Level#FATAL}</li>
 *   <li>debug → {@link org.apache.logging.log4j.Level#DEBUG}</li>
 *   <li>trace → {@link org.apache.logging.log4j.Level#TRACE}</li>
 *   <li>exception → {@link org.apache.logging.log4j.Level#ERROR} (with the exception attached as a {@link Throwable})</li>
 * </ul>
 * <p>
 * This class requires {@code log4j-api} on the classpath at runtime; it does not depend on
 * {@code log4j-core} or any other Log4j 2 implementation.
 *
 * @author Stefano Reksten
 */
public class Log4JMessageHandler extends AbstractMessageHandler {

    private final Logger logger;

    /**
     * Creates a {@code Log4JMessageHandler} that delegates to the given {@link Logger}.
     *
     * @param logger the Log4j 2 logger to delegate to; must not be {@code null}
     * @throws NullPointerException if {@code logger} is {@code null}
     */
    public Log4JMessageHandler(final Logger logger) {
        this.logger = Objects.requireNonNull(logger, MessageHandlerResourceBundle.get("loggerCannotBeNull"));
    }

    /**
     * Creates a {@code Log4JMessageHandler} that delegates to a {@link Logger} looked up by name.
     * <p>
     * The logger is obtained via {@link LogManager#getLogger(String)}, which reuses an existing
     * instance when one with the given name has already been created.
     *
     * @param loggerName the name of the Log4j 2 logger to look up; must not be {@code null} or blank
     * @throws NullPointerException     if {@code loggerName} is {@code null}
     * @throws IllegalArgumentException if {@code loggerName} is blank
     */
    public Log4JMessageHandler(final String loggerName) {
        Objects.requireNonNull(loggerName, MessageHandlerResourceBundle.get("loggerNameCannotBeNull"));
        if (loggerName.trim().isEmpty()) {
            throw new IllegalArgumentException(MessageHandlerResourceBundle.get("loggerNameCannotBeEmpty"));
        }
        this.logger = LogManager.getLogger(loggerName);
    }

    @Override
    public void handleMessage(@Nonnull SeverityNumber level, @Nonnull String message) {
        switch (level) {
            case INFO:
            case INFO2:
            case INFO3:
            case INFO4:
                logger.info(message);
                break;
            case WARN:
            case WARN2:
            case WARN3:
            case WARN4:
                logger.warn(message);
                break;
            case ERROR:
            case ERROR2:
            case ERROR3:
            case ERROR4:
                logger.error(message);
                break;
            case FATAL:
            case FATAL2:
            case FATAL3:
            case FATAL4:
                logger.fatal(message);
                break;
            case DEBUG:
            case DEBUG2:
            case DEBUG3:
            case DEBUG4:
                logger.debug(message);
                break;
            case TRACE:
            case TRACE2:
            case TRACE3:
            case TRACE4:
                logger.trace(message);
                break;
            default:
                logger.info("Unknown severity level: {}. Logging as INFO. {}", level, message);
                break;
        }
    }

    @Override
    public void handleThrowable(@Nonnull String message, @Nonnull Throwable throwable) {
        logger.error(ThrowableMessageFormatter.withPrefix(message, throwable), throwable);
    }
}
