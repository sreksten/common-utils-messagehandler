package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * An abstract implementation of the {@link MessageHandler} interface that checks if a given message level is enabled
 * before forwarding the message to the concrete implementation. If the message parameter is null, an exception is
 * thrown. If the parameter is a Supplier, and the supplier returns null, an exception is thrown.<br/>
 * The advantage of using a Supplier is that if the message level is deactivated, the message construction can be
 * skipped, saving resources.
 *
 * @author Stefano Reksten
 */
public abstract class AbstractMessageHandler implements MessageHandler {

    private volatile int enabledLevels = 0b11111111111111111111111111; // All enabled by default

    public void enable(final SeverityNumber ... levels) {
        int newEnabledLevels = 0;
        for (SeverityNumber level : levels) {
            newEnabledLevels |= level.getValue();
        }
        enabledLevels = newEnabledLevels;
    }

    public void disable(final SeverityNumber ... levels) {
        int newEnabledLevels = enabledLevels;
        for (SeverityNumber level : levels) {
            newEnabledLevels &= ~level.getValue();
        }
        enabledLevels = newEnabledLevels;
    }

    public boolean isEnabled(final @Nonnull SeverityNumber level) {
        Objects.requireNonNull(level, MessageHandlerResourceBundle.get("nullLevelProvided"));
        return (enabledLevels & level.getValue()) != 0;
    }

    public void setEnabled(final @Nonnull SeverityNumber level, final boolean enabled) {
        Objects.requireNonNull(level, MessageHandlerResourceBundle.get("nullLevelProvided"));
        if (enabled) {
            enabledLevels |= level.getValue();
        } else {
            enabledLevels &= ~level.getValue();
        }
    }
    public void log(final @Nonnull SeverityNumber level, final @Nonnull Supplier<String> message) {
        if (isEnabled(level)) {
            Objects.requireNonNull(message, MessageHandlerResourceBundle.get("nullMessageSupplierProvided"));
            log(level, message.get());
        }
    }

    public void log(final @Nonnull SeverityNumber level, final @Nonnull String message) {
        if (isEnabled(level)) {
            Objects.requireNonNull(level, MessageHandlerResourceBundle.get("nullLevelProvided"));
            Objects.requireNonNull(message, MessageHandlerResourceBundle.get("nullMessageProvided"));
            handleMessage(level, message);
        }
    }

    public void log(final @Nonnull Throwable throwable) {
        if (isEnabled(SeverityNumber.ERROR)) {
            Objects.requireNonNull(throwable, MessageHandlerResourceBundle.get("nullThrowableProvided"));
            String throwableMessage = throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
            handleThrowable(throwableMessage, throwable);
        }
    }

    public void log(final @Nonnull String message, final @Nonnull Throwable throwable) {
        if (isEnabled(SeverityNumber.ERROR)) {
            Objects.requireNonNull(message, MessageHandlerResourceBundle.get("nullMessageProvided"));
            Objects.requireNonNull(throwable, MessageHandlerResourceBundle.get("nullThrowableProvided"));
            handleThrowable(message, throwable);
        }
    }

    /**
     * @return {@code true} if info-level message handling is currently enabled.
     */
    public boolean isInfoEnabled() {
        return isEnabled(SeverityNumber.INFO);
    }

    /**
     * Enables or disables info-level message handling.
     * <p>
     * When set to {@code false}, calls to {@code info(...)} variants are silently dropped before
     * reaching the concrete implementation.
     */
    public void setInfoEnabled(boolean infoEnabled) {
        setEnabled(SeverityNumber.INFO, infoEnabled);
    }

    /**
     * @return {@code true} if warning-level message handling is currently enabled.
     */
    public boolean isWarnEnabled() {
        return isEnabled(SeverityNumber.WARN);
    }

    /**
     * Enables or disables warning-level message handling.
     * <p>
     * When set to {@code false}, calls to {@code warn(...)} variants are silently dropped before
     * reaching the concrete implementation.
     */
    public void setWarnEnabled(boolean warnEnabled) {
        setEnabled(SeverityNumber.WARN, warnEnabled);
    }

    /**
     * @return {@code true} if error-level message handling is currently enabled.
     */
    public boolean isErrorEnabled() {
        return isEnabled(SeverityNumber.ERROR);
    }

    /**
     * Enables or disables error-level message handling.
     * <p>
     * When set to {@code false}, calls to {@code error(...)} variants are silently dropped before
     * reaching the concrete implementation.
     */
    public void setErrorEnabled(boolean errorEnabled) {
        setEnabled(SeverityNumber.ERROR, errorEnabled);
    }

    /**
     * @return {@code true} if fatal-level message handling is currently enabled.
     */
    public boolean isFatalEnabled() {
        return isEnabled(SeverityNumber.FATAL);
    }

    /**
     * Enables or disables fatal-level message handling.
     * <p>
     * When set to {@code false}, calls to {@code fatal(...)} variants are silently dropped before
     * reaching the concrete implementation.
     */
    public void setFatalEnabled(boolean fatalEnabled) {
        setEnabled(SeverityNumber.FATAL, fatalEnabled);
    }

    /**
     * @return {@code true} if debug-level message handling is currently enabled.
     */
    public boolean isDebugEnabled() {
        return isEnabled(SeverityNumber.DEBUG);
    }

    /**
     * Enables or disables debug-level message handling.
     * <p>
     * When set to {@code false}, calls to {@code #debug(...)} variants are silently dropped before
     * reaching the concrete implementation.
     */
    public void setDebugEnabled(boolean debugEnabled) {
        setEnabled(SeverityNumber.DEBUG, debugEnabled);
    }

    /**
     * @return {@code true} if trace-level message handling is currently enabled.
     */
    public boolean isTraceEnabled() {
        return isEnabled(SeverityNumber.TRACE);
    }

    /**
     * Enables or disables trace-level message handling.
     * <p>
     * When set to {@code false}, calls to {@code trace(...)} variants are silently dropped before
     * reaching the concrete implementation.
     */
    public void setTraceEnabled(boolean traceEnabled) {
        setEnabled(SeverityNumber.TRACE, traceEnabled);
    }


    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when info-level handling is disabled.
     *
     * @throws NullPointerException if {@code message} is {@code null}
     */
    @Override
    public void info(final @Nonnull String message) {
        log(SeverityNumber.INFO, message);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when info-level handling is disabled.
     *
     * @throws NullPointerException if either {@code messageSupplier} or its production is {@code null}
     */
    public void info(final @Nonnull Supplier<String> messageSupplier) {
        log(SeverityNumber.INFO, messageSupplier);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when warning-level handling is disabled.
     *
     * @throws NullPointerException if {@code message} is {@code null}
     */
    @Override
    public void warn(final @Nonnull String message) {
        log(SeverityNumber.WARN, message);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when warn-level handling is disabled.
     *
     * @throws NullPointerException if either {@code messageSupplier} or its production is {@code null}
     */
    public void warn(final @Nonnull Supplier<String> messageSupplier) {
        log(SeverityNumber.WARN, messageSupplier);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when error-level handling is disabled.
     *
     * @throws NullPointerException if {@code message} is {@code null}
     */
    @Override
    public void error(final @Nonnull String message) {
        log(SeverityNumber.ERROR, message);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when error-level handling is disabled.
     *
     * @throws NullPointerException if either {@code messageSupplier} or its production is {@code null}
     */
    public void error(final @Nonnull Supplier<String> messageSupplier) {
        log(SeverityNumber.ERROR, messageSupplier);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when fatal-level handling is disabled.
     *
     * @throws NullPointerException if either {@code message} or {@code contextInfo} is {@code null}
     */
    @Override
    public void fatal(final @Nonnull String message) {
        log(SeverityNumber.FATAL, message);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when fatal-level handling is disabled.
     *
     * @throws NullPointerException if either {@code messageSupplier} or its production is {@code null}
     */
    public void fatal(final @Nonnull Supplier<String> messageSupplier) {
        log(SeverityNumber.FATAL, messageSupplier);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when debug-level handling is disabled.
     *
     * @throws NullPointerException if {@code message} is {@code null}
     */
    @Override
    public void debug(final @Nonnull String message) {
        log(SeverityNumber.DEBUG, message);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when debug-level handling is disabled.
     *
     * @throws NullPointerException if {@code messageSupplier} is {@code null}
     */
    public void debug(final @Nonnull Supplier<String> messageSupplier) {
        log(SeverityNumber.DEBUG, messageSupplier);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when trace-level handling is disabled.
     *
     * @throws NullPointerException if {@code message} is {@code null}
     */
    @Override
    public void trace(final @Nonnull String message) {
        log(SeverityNumber.TRACE, message);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when trace-level handling is disabled.
     *
     * @throws NullPointerException if {@code messageSupplier} is {@code null}
     */
    public void trace(final @Nonnull Supplier<String> messageSupplier) {
        log(SeverityNumber.TRACE, messageSupplier);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when exception handling is disabled.
     *
     * @throws NullPointerException if {@code throwable} is {@code null}
     */
    @Override
    public void exception(final @Nonnull Throwable throwable) {
        log(throwable);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when exception handling is disabled.
     *
     * @throws NullPointerException if either {@code message} or {@code throwable} is {@code null}
     */
    public void exception(final @Nonnull String message, final @Nonnull Throwable throwable) {
        log(message, throwable);
    }
}
