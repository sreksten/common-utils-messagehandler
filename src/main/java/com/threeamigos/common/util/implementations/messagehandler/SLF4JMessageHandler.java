package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * A {@link MessageHandler} implementation that bridges to
 * <a href="https://www.slf4j.org/">SLF4J</a>.
 * <p>
 * Message levels are mapped to SLF4J levels as follows:
 * <ul>
 *   <li>info  → {@link Logger#info(String)}</li>
 *   <li>warn  → {@link Logger#warn(String)}</li>
 *   <li>error, fatal → {@link Logger#error(String)}</li>
 *   <li>debug → {@link Logger#debug(String)}</li>
 *   <li>trace → {@link Logger#trace(String)}</li>
 *   <li>exception → {@link Logger#error(String, Throwable)} (exception attached as a {@link Throwable})</li>
 * </ul>
 * <p>
 * This class requires {@code slf4j-api} on the classpath at runtime and an SLF4J binding
 * (e.g. {@code logback-classic}, {@code slf4j-simple}) provided by the consumer application.
 *
 * @author Stefano Reksten
 */
public class SLF4JMessageHandler extends AbstractMessageHandler {

    private final Logger logger;

    /**
     * Creates an {@code SLF4JMessageHandler} that delegates to the given {@link Logger}.
     *
     * @param logger the SLF4J logger to delegate to; must not be {@code null}
     * @throws NullPointerException if {@code logger} is {@code null}
     */
    public SLF4JMessageHandler(final Logger logger) {
        this.logger = Objects.requireNonNull(logger, MessageHandlerResourceBundle.get("loggerCannotBeNull"));
    }

    /**
     * Creates an {@code SLF4JMessageHandler} that delegates to a {@link Logger} looked up by name.
     * <p>
     * The logger is obtained via {@link LoggerFactory#getLogger(String)}, which reuses an existing
     * instance when one with the given name has already been created.
     *
     * @param loggerName the name of the SLF4J logger to look up; must not be {@code null} or blank
     * @throws NullPointerException     if {@code loggerName} is {@code null}
     * @throws IllegalArgumentException if {@code loggerName} is blank
     */
    public SLF4JMessageHandler(final String loggerName) {
        Objects.requireNonNull(loggerName, MessageHandlerResourceBundle.get("loggerNameCannotBeNull"));
        if (loggerName.trim().isEmpty()) {
            throw new IllegalArgumentException(MessageHandlerResourceBundle.get("loggerNameCannotBeEmpty"));
        }
        this.logger = LoggerFactory.getLogger(loggerName);
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
            case FATAL:
            case FATAL2:
            case FATAL3:
            case FATAL4:
                logger.error(message);
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
