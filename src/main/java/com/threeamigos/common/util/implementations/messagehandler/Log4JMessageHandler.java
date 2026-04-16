package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

/**
 * A {@link MessageHandler} implementation that bridges to
 * <a href="https://logging.apache.org/log4j/2.x/">Apache Log4j 2</a>.
 * <p>
 * Message levels are mapped to Log4j 2 levels as follows:
 * <ul>
 *   <li>info  → {@link org.apache.logging.log4j.Level#INFO}</li>
 *   <li>warn  → {@link org.apache.logging.log4j.Level#WARN}</li>
 *   <li>error → {@link org.apache.logging.log4j.Level#ERROR}</li>
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
        this.logger = Objects.requireNonNull(logger, MessageHandlerResourceBundle.BUNDLE.getString("loggerCannotBeNull"));
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
        Objects.requireNonNull(loggerName, MessageHandlerResourceBundle.BUNDLE.getString("loggerNameCannotBeNull"));
        if (loggerName.trim().isEmpty()) {
            throw new IllegalArgumentException(MessageHandlerResourceBundle.BUNDLE.getString("loggerNameCannotBeEmpty"));
        }
        this.logger = LogManager.getLogger(loggerName);
    }

    @Override
    protected void handleInfoMessageImpl(final String message) {
        logger.info(message);
    }

    @Override
    protected void handleWarnMessageImpl(final String message) {
        logger.warn(message);
    }

    @Override
    protected void handleErrorMessageImpl(final String message) {
        logger.error(message);
    }

    @Override
    protected void handleDebugMessageImpl(final String message) {
        logger.debug(message);
    }

    @Override
    protected void handleTraceMessageImpl(final String message) {
        logger.trace(message);
    }

    @Override
    protected void handleExceptionImpl(final Exception exception) {
        logger.error(ExceptionMessageFormatter.detail(exception), exception);
    }

    @Override
    protected void handleExceptionImpl(final String message, final Exception exception) {
        logger.error(message, exception);
    }
}
