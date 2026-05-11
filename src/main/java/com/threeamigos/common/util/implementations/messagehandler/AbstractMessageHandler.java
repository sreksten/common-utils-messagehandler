package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * An abstract implementation of the {@link MessageHandler} interface that checks if a given message level is enabled
 * before forwarding the message to the concrete implementation. Null message inputs are silently ignored:
 * if a message string is {@code null}, if a message supplier is {@code null}, or if a supplier produces
 * {@code null}, the call is treated as a no-op.<br/>
 * Null severity values are normalized to {@link SeverityNumber#INFO}.<br/>
 * The advantage of using a Supplier is that if the message level is deactivated, the message construction can be
 * skipped, saving resources.
 * <p>
 * This base class provides level filtering and dispatch semantics only. It does not provide asynchronous dispatch;
 * async support is available only in handlers based on {@link AbstractOutputMessageHandler}.
 * <p>
 * Levels enabled by default: INFO*, WARN*, ERROR*, FATAL*.
 * <p>
 * To enable or disable whole message levels, use the corresponding setEnabled methods. For example, to enable all
 * DEBUG* messages, call <code>setDebugEnabled(true)</code>. To disable all TRACE* messages, call
 * <code>setTraceEnabled(false)</code>.
 * <p>
 * To enable or disable specific message levels, use the corresponding <code>enable</code> or <code>disable</code>
 * methods. For example, to enable WARN2 messages, call <code>enable(SeverityNumber.WARN2)</code>. To disable INFO3
 * messages, call <code>disable(SeverityNumber.INFO3)</code>.
 * <p>
 * @author Stefano Reksten
 */
public abstract class AbstractMessageHandler implements MessageHandler {

    /**
     * Performs the actual exception dispatch.
     * <p>
     * Called only when exception handling is enabled and the throwable has been validated as non-null.
     *
     * @param message contextual message to pair with the throwable
     * @param throwable exception to dispatch
     */
    protected abstract void handleExceptionInternal(final @Nonnull String message, final @Nonnull Throwable throwable);

    private volatile int enabledLevels =
                    1 << SeverityNumber.INFO.getValue() |
                    1 << SeverityNumber.INFO2.getValue() |
                    1 << SeverityNumber.INFO3.getValue() |
                    1 << SeverityNumber.INFO4.getValue() |
                    1 << SeverityNumber.WARN.getValue() |
                    1 << SeverityNumber.WARN2.getValue() |
                    1 << SeverityNumber.WARN3.getValue() |
                    1 << SeverityNumber.WARN4.getValue() |
                    1 << SeverityNumber.ERROR.getValue() |
                    1 << SeverityNumber.ERROR2.getValue() |
                    1 << SeverityNumber.ERROR3.getValue() |
                    1 << SeverityNumber.ERROR4.getValue() |
                    1 << SeverityNumber.FATAL.getValue() |
                    1 << SeverityNumber.FATAL2.getValue() |
                    1 << SeverityNumber.FATAL3.getValue() |
                    1 << SeverityNumber.FATAL4.getValue()
            ;

    private static SeverityNumber normalizeLevel(final SeverityNumber level) {
        return level != null ? level : SeverityNumber.INFO;
    }

    private static int levelMask(final SeverityNumber level) {
        return 1 << normalizeLevel(level).getValue();
    }

    public void enable(final Collection<SeverityNumber> levels) {
        int newEnabledLevels = enabledLevels;
        for (SeverityNumber level : levels) {
            newEnabledLevels |= levelMask(level);
        }
        enabledLevels = newEnabledLevels;
    }

    public void enable(final SeverityNumber ... levels) {
        int newEnabledLevels = enabledLevels;
        for (SeverityNumber level : levels) {
            newEnabledLevels |= levelMask(level);
        }
        enabledLevels = newEnabledLevels;
    }

    public void disable(final Collection<SeverityNumber> levels) {
        int newEnabledLevels = enabledLevels;
        for (SeverityNumber level : levels) {
            newEnabledLevels &= ~levelMask(level);
        }
        enabledLevels = newEnabledLevels;
    }

    public void disable(final SeverityNumber ... levels) {
        int newEnabledLevels = enabledLevels;
        for (SeverityNumber level : levels) {
            newEnabledLevels &= ~levelMask(level);
        }
        enabledLevels = newEnabledLevels;
    }

    public boolean isEnabled(final @Nonnull SeverityNumber level) {
        return (enabledLevels & levelMask(level)) != 0;
    }

    public void setEnabled(final @Nonnull SeverityNumber level, final boolean enabled) {
        SeverityNumber effectiveLevel = normalizeLevel(level);
        int newEnabledLevels = enabledLevels;
        if (enabled) {
            newEnabledLevels |= levelMask(effectiveLevel);
        } else {
            newEnabledLevels &= ~levelMask(effectiveLevel);
        }
        enabledLevels = newEnabledLevels;
    }

    /**
     * Returns the currently enabled severity levels.
     *
     * @return enabled levels as a new array snapshot
     */
    public SeverityNumber[] getEnabledLevels() {
        List<SeverityNumber> enabled = new ArrayList<>(SeverityNumber.values().length);
        for (SeverityNumber level : SeverityNumber.values()) {
            if ((enabledLevels & levelMask(level)) != 0) {
                enabled.add(level);
            }
        }
        return enabled.toArray(new SeverityNumber[0]);
    }

    /**
     * Returns the currently disabled severity levels.
     *
     * @return disabled levels as a new array snapshot
     */
    public SeverityNumber[] getDisabledLevels() {
        List<SeverityNumber> disabled = new ArrayList<>(SeverityNumber.values().length);
        for (SeverityNumber level : SeverityNumber.values()) {
            if ((enabledLevels & levelMask(level)) == 0) {
                disabled.add(level);
            }
        }
        return disabled.toArray(new SeverityNumber[0]);
    }

    public void log(final @Nonnull SeverityNumber level, final @Nonnull Supplier<String> message) {
        if (message == null) {
            return;
        }
        SeverityNumber effectiveLevel = normalizeLevel(level);
        if (isEnabled(effectiveLevel)) {
            String producedMessage = message.get();
            if (producedMessage == null) {
                return;
            }
            log(effectiveLevel, producedMessage);
        }
    }

    public void log(final @Nonnull SeverityNumber level, final @Nonnull String message) {
        if (message == null) {
            return;
        }
        SeverityNumber effectiveLevel = normalizeLevel(level);
        if (isEnabled(effectiveLevel)) {
            handleMessage(effectiveLevel, message);
        }
    }

    public void log(final @Nonnull Throwable throwable) {
        if (isEnabled(SeverityNumber.ERROR)) {
            Objects.requireNonNull(throwable, MessageHandlerResourceBundle.get("nullThrowableProvided"));
            String throwableMessage = throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
            handleExceptionInternal(throwableMessage, throwable);
        }
    }

    public void log(final @Nonnull String message, final @Nonnull Throwable throwable) {
        if (isEnabled(SeverityNumber.ERROR)) {
            if (message == null) {
                return;
            }
            Objects.requireNonNull(throwable, MessageHandlerResourceBundle.get("nullThrowableProvided"));
            handleExceptionInternal(message, throwable);
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
        if (infoEnabled) {
            enable(SeverityNumber.INFO, SeverityNumber.INFO2, SeverityNumber.INFO3, SeverityNumber.INFO4);
        } else {
            disable(SeverityNumber.INFO, SeverityNumber.INFO2, SeverityNumber.INFO3, SeverityNumber.INFO4);
        }
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
        if (warnEnabled) {
            enable(SeverityNumber.WARN, SeverityNumber.WARN2, SeverityNumber.WARN3, SeverityNumber.WARN4);
        } else {
            disable(SeverityNumber.WARN, SeverityNumber.WARN2, SeverityNumber.WARN3, SeverityNumber.WARN4);
        }
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
        if (errorEnabled) {
            enable(SeverityNumber.ERROR, SeverityNumber.ERROR2, SeverityNumber.ERROR3, SeverityNumber.ERROR4);
        } else {
            disable(SeverityNumber.ERROR, SeverityNumber.ERROR2, SeverityNumber.ERROR3, SeverityNumber.ERROR4);
        }
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
        if (fatalEnabled) {
            enable(SeverityNumber.FATAL, SeverityNumber.FATAL2, SeverityNumber.FATAL3, SeverityNumber.FATAL4);
        } else {
            disable(SeverityNumber.FATAL, SeverityNumber.FATAL2, SeverityNumber.FATAL3, SeverityNumber.FATAL4);
        }
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
        if (debugEnabled) {
            enable(SeverityNumber.DEBUG, SeverityNumber.DEBUG2, SeverityNumber.DEBUG3, SeverityNumber.DEBUG4);
        } else {
            disable(SeverityNumber.DEBUG, SeverityNumber.DEBUG2, SeverityNumber.DEBUG3, SeverityNumber.DEBUG4);
        }
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
        if (traceEnabled) {
            enable(SeverityNumber.TRACE, SeverityNumber.TRACE2, SeverityNumber.TRACE3, SeverityNumber.TRACE4);
        } else {
            disable(SeverityNumber.TRACE, SeverityNumber.TRACE2, SeverityNumber.TRACE3, SeverityNumber.TRACE4);
        }
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
