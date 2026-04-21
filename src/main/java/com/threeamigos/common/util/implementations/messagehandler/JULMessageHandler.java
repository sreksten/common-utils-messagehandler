package com.threeamigos.common.util.implementations.messagehandler;

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
 *   <li>debug → {@link java.util.logging.Level#FINE}</li>
 *   <li>trace → {@link java.util.logging.Level#FINER}</li>
 *   <li>exception → {@link java.util.logging.Level#SEVERE} (with the exception attached as a {@link Throwable})</li>
 * </ul>
 *
 * @author Stefano Reksten
 */
public class JULMessageHandler extends AbstractMessageHandler {

    private final Logger logger;

    /**
     * Creates a {@code JULMessageHandler} that delegates to the given {@link Logger}.
     *
     * @param logger the JUL logger to delegate to; must not be {@code null}
     * @throws NullPointerException if {@code logger} is {@code null}
     */
    public JULMessageHandler(Logger logger) {
        this.logger = Objects.requireNonNull(logger, MessageHandlerResourceBundle.get("loggerCannotBeNull"));
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
        Objects.requireNonNull(loggerName, MessageHandlerResourceBundle.get("loggerNameCannotBeNull"));
        if (loggerName.trim().isEmpty()) {
            throw new IllegalArgumentException(MessageHandlerResourceBundle.get("loggerNameCannotBeEmpty"));
        }
        this.logger = Logger.getLogger(loggerName);
    }

    @Override
    protected void handleInfoMessageImpl(final String message) {
        logger.log(Level.INFO, message);
    }

    @Override
    protected void handleWarnMessageImpl(final String message) {
        logger.log(Level.WARNING, message);
    }

    @Override
    protected void handleErrorMessageImpl(final String message) {
        logger.log(Level.SEVERE, message);
    }

    @Override
    protected void handleFatalMessageImpl(final String message) {
        logger.log(Level.SEVERE, message);
    }

    @Override
    protected void handleDebugMessageImpl(final String message) {
        logger.log(Level.FINER, message);
    }

    @Override
    protected void handleTraceMessageImpl(final String message) {
        logger.log(Level.FINEST, message);
    }

    @Override
    protected void handleExceptionImpl(final Exception exception) {
        logger.log(Level.SEVERE, ExceptionMessageFormatter.detail(exception), exception);
    }

    @Override
    protected void handleExceptionImpl(final String message, final Exception exception) {
        logger.log(Level.SEVERE, ExceptionMessageFormatter.withPrefix(message, exception), exception);
    }
}
