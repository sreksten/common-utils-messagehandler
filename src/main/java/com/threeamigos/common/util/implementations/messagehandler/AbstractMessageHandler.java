package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.ContextInfo;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
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

    static final ContextInfo EMPTY_CONTEXT_INFO = new ContextInfoImpl();

    private volatile boolean isInfoEnabled = true;
    private volatile boolean isWarnEnabled = true;
    private volatile boolean isErrorEnabled = true;
    private volatile boolean isFatalEnabled = true;
    private volatile boolean isDebugEnabled = true;
    private volatile boolean isTraceEnabled = true;
    private volatile boolean isExceptionEnabled = true;

    /**
     * @return {@code true} if info-level message handling is currently enabled.
     */
    public boolean isInfoEnabled() {
        return isInfoEnabled;
    }

    /**
     * Enables or disables info-level message handling.
     * <p>
     * When set to {@code false}, calls to {@code info(...)} variants are silently dropped before
     * reaching the concrete implementation.
     */
    public void setInfoEnabled(boolean infoEnabled) {
        isInfoEnabled = infoEnabled;
    }

    /**
     * @return {@code true} if warning-level message handling is currently enabled.
     */
    public boolean isWarnEnabled() {
        return isWarnEnabled;
    }

    /**
     * Enables or disables warning-level message handling.
     * <p>
     * When set to {@code false}, calls to {@code warn(...)} variants are silently dropped before
     * reaching the concrete implementation.
     */
    public void setWarnEnabled(boolean warnEnabled) {
        isWarnEnabled = warnEnabled;
    }

    /**
     * @return {@code true} if error-level message handling is currently enabled.
     */
    public boolean isErrorEnabled() {
        return isErrorEnabled;
    }

    /**
     * Enables or disables error-level message handling.
     * <p>
     * When set to {@code false}, calls to {@code error(...)} variants are silently dropped before
     * reaching the concrete implementation.
     */
    public void setErrorEnabled(boolean errorEnabled) {
        isErrorEnabled = errorEnabled;
    }

    /**
     * @return {@code true} if fatal-level message handling is currently enabled.
     */
    public boolean isFatalEnabled() {
        return isFatalEnabled;
    }

    /**
     * Enables or disables fatal-level message handling.
     * <p>
     * When set to {@code false}, calls to {@code fatal(...)} variants are silently dropped before
     * reaching the concrete implementation.
     */
    public void setFatalEnabled(boolean fatalEnabled) {
        isFatalEnabled = fatalEnabled;
    }

    /**
     * @return {@code true} if debug-level message handling is currently enabled.
     */
    public boolean isDebugEnabled() {
        return isDebugEnabled;
    }

    /**
     * Enables or disables debug-level message handling.
     * <p>
     * When set to {@code false}, calls to {@code #debug(...)} variants are silently dropped before
     * reaching the concrete implementation.
     */
    public void setDebugEnabled(boolean debugEnabled) {
        isDebugEnabled = debugEnabled;
    }

    /**
     * @return {@code true} if trace-level message handling is currently enabled.
     */
    public boolean isTraceEnabled() {
        return isTraceEnabled;
    }

    /**
     * Enables or disables trace-level message handling.
     * <p>
     * When set to {@code false}, calls to {@code trace(...)} variants are silently dropped before
     * reaching the concrete implementation.
     */
    public void setTraceEnabled(boolean traceEnabled) {
        isTraceEnabled = traceEnabled;
    }

    /**
     * @return {@code true} if exception handling is currently enabled.
     */
    public boolean isExceptionEnabled() {
        return isExceptionEnabled;
    }

    /**
     * Enables or disables exception handling.
     * <p>
     * When set to {@code false}, calls to {@code #exception(...)} variants are silently dropped before reaching
     * the concrete implementation.
     */
    public void setExceptionEnabled(boolean exceptionEnabled) {
        isExceptionEnabled = exceptionEnabled;
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
        if (isInfoEnabled) {
            Objects.requireNonNull(message, MessageHandlerResourceBundle.get("nullMessageProvided"));
            handleInfoMessageImpl(message, EMPTY_CONTEXT_INFO);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when info-level handling is disabled.
     *
     * @throws NullPointerException if either {@code message} or {@code contextInfo} is {@code null}
     */
    @Override
    public void info(final @Nonnull String message, final @Nonnull ContextInfo contextInfo) {
        if (isInfoEnabled) {
            Objects.requireNonNull(message, MessageHandlerResourceBundle.get("nullMessageProvided"));
            Objects.requireNonNull(contextInfo, MessageHandlerResourceBundle.get("nullContextInfoProvided"));
            handleInfoMessageImpl(message, contextInfo);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when info-level handling is disabled.
     *
     * @throws NullPointerException if either {@code messageSupplier} or its production is {@code null}
     */
    public void info(final @Nonnull Supplier<String> messageSupplier) {
        if (isInfoEnabled) {
            Objects.requireNonNull(messageSupplier, MessageHandlerResourceBundle.get("nullMessageSupplierProvided"));
            info(messageSupplier.get());
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when info-level handling is disabled.
     *
     * @throws NullPointerException if either {@code messageSupplier}, its production, or {@code contextInfo} is {@code null}
     */
    public void info(final @Nonnull Supplier<String> messageSupplier, final @Nonnull ContextInfo contextInfo) {
        if (isInfoEnabled) {
            Objects.requireNonNull(messageSupplier, MessageHandlerResourceBundle.get("nullMessageSupplierProvided"));
            info(messageSupplier.get(), contextInfo);
        }
    }

    /**
     * Performs the actual info-level message dispatch.
     * <p>
     * Called by {@link #info(String)} only when info handling is enabled
     * and the message has been validated as non-null.
     *
     * @param message the validated, non-null info message to handle
     */
    protected abstract void handleInfoMessageImpl(final String message, final ContextInfo contextInfo);

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when warning-level handling is disabled.
     *
     * @throws NullPointerException if {@code message} is {@code null}
     */
    @Override
    public void warn(final @Nonnull String message) {
        if (isWarnEnabled) {
            Objects.requireNonNull(message, MessageHandlerResourceBundle.get("nullMessageProvided"));
            handleWarnMessageImpl(message, EMPTY_CONTEXT_INFO);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when warning-level handling is disabled.
     *
     * @throws NullPointerException if either {@code message} or {@code contextInfo} is {@code null}
     */
    @Override
    public void warn(final @Nonnull String message, final @Nonnull ContextInfo contextInfo) {
        if (isWarnEnabled) {
            Objects.requireNonNull(message, MessageHandlerResourceBundle.get("nullMessageProvided"));
            Objects.requireNonNull(contextInfo, MessageHandlerResourceBundle.get("nullContextInfoProvided"));
            handleWarnMessageImpl(message, contextInfo);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when warn-level handling is disabled.
     *
     * @throws NullPointerException if either {@code messageSupplier} or its production is {@code null}
     */
    public void warn(final @Nonnull Supplier<String> messageSupplier) {
        if (isWarnEnabled) {
            Objects.requireNonNull(messageSupplier, MessageHandlerResourceBundle.get("nullMessageSupplierProvided"));
            warn(messageSupplier.get());
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when warn-level handling is disabled.
     *
     * @throws NullPointerException if either {@code messageSupplier}, its production, or {@code contextInfo} is {@code null}
     */
    public void warn(final @Nonnull Supplier<String> messageSupplier, final @Nonnull ContextInfo contextInfo) {
        if (isWarnEnabled) {
            Objects.requireNonNull(messageSupplier, MessageHandlerResourceBundle.get("nullMessageSupplierProvided"));
            warn(messageSupplier.get(), contextInfo);
        }
    }

    /**
     * Performs the actual warning-level message dispatch.
     * <p>
     * Called by {@link #warn(String)} only when warn handling is enabled
     * and the message has been validated as non-null.
     *
     * @param message the validated, non-null warning message to handle
     */
    protected abstract void handleWarnMessageImpl(final String message, final ContextInfo contextInfo);

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when error-level handling is disabled.
     *
     * @throws NullPointerException if {@code message} is {@code null}
     */
    @Override
    public void error(final @Nonnull String message) {
        if (isErrorEnabled) {
            Objects.requireNonNull(message, MessageHandlerResourceBundle.get("nullMessageProvided"));
            handleErrorMessageImpl(message, EMPTY_CONTEXT_INFO);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when error-level handling is disabled.
     *
     * @throws NullPointerException if either {@code message} or {@code contextInfo} is {@code null}
     */
    @Override
    public void error(final @Nonnull String message, final @Nonnull ContextInfo contextInfo) {
        if (isErrorEnabled) {
            Objects.requireNonNull(message, MessageHandlerResourceBundle.get("nullMessageProvided"));
            Objects.requireNonNull(contextInfo, MessageHandlerResourceBundle.get("nullContextInfoProvided"));
            handleErrorMessageImpl(message, contextInfo);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when error-level handling is disabled.
     *
     * @throws NullPointerException if either {@code messageSupplier} or its production is {@code null}
     */
    public void error(final @Nonnull Supplier<String> messageSupplier) {
        if (isErrorEnabled) {
            Objects.requireNonNull(messageSupplier, MessageHandlerResourceBundle.get("nullMessageSupplierProvided"));
            error(messageSupplier.get());
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when error-level handling is disabled.
     *
     * @throws NullPointerException if either {@code messageSupplier}, its production, or {@code contextInfo} is {@code null}
     */
    public void error(final @Nonnull Supplier<String> messageSupplier, final @Nonnull ContextInfo contextInfo) {
        if (isErrorEnabled) {
            Objects.requireNonNull(messageSupplier, MessageHandlerResourceBundle.get("nullMessageSupplierProvided"));
            error(messageSupplier.get(), contextInfo);
        }
    }

    /**
     * Performs the actual error-level message dispatch.
     * <p>
     * Called by {@link #error(String)} only when error handling is enabled
     * and the message has been validated as non-null.
     *
     * @param message the validated, non-null error message to handle
     */
    protected abstract void handleErrorMessageImpl(final String message, final ContextInfo contextInfo);

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when fatal-level handling is disabled.
     *
     * @throws NullPointerException if either {@code message} or {@code contextInfo} is {@code null}
     */
    @Override
    public void fatal(final @Nonnull String message) {
        if (isFatalEnabled) {
            Objects.requireNonNull(message, MessageHandlerResourceBundle.get("nullMessageProvided"));
            handleFatalMessageImpl(message, EMPTY_CONTEXT_INFO);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when fatal-level handling is disabled.
     *
     * @throws NullPointerException if either {@code message} or {@code contextInfo} is {@code null}
     */
    @Override
    public void fatal(final @Nonnull String message, final @Nonnull ContextInfo contextInfo) {
        if (isFatalEnabled) {
            Objects.requireNonNull(message, MessageHandlerResourceBundle.get("nullMessageProvided"));
            Objects.requireNonNull(contextInfo, MessageHandlerResourceBundle.get("nullContextInfoProvided"));
            handleFatalMessageImpl(message, contextInfo);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when fatal-level handling is disabled.
     *
     * @throws NullPointerException if either {@code messageSupplier} or its production is {@code null}
     */
    public void fatal(final @Nonnull Supplier<String> messageSupplier) {
        if (isFatalEnabled) {
            Objects.requireNonNull(messageSupplier, MessageHandlerResourceBundle.get("nullMessageSupplierProvided"));
            fatal(messageSupplier.get());
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when fatal-level handling is disabled.
     *
     * @throws NullPointerException if either {@code messageSupplier}, its production, or {@code contextInfo} is {@code null}
     */
    public void fatal(final @Nonnull Supplier<String> messageSupplier, final @Nonnull ContextInfo contextInfo) {
        if (isFatalEnabled) {
            Objects.requireNonNull(messageSupplier, MessageHandlerResourceBundle.get("nullMessageSupplierProvided"));
            fatal(messageSupplier.get(), contextInfo);
        }
    }

    /**
     * Performs the actual error-level message dispatch.
     * <p>
     * Called by {@link #error(String)} only when error handling is enabled
     * and the message has been validated as non-null.
     *
     * @param message the validated, non-null error message to handle
     */
    protected abstract void handleFatalMessageImpl(final String message, final ContextInfo contextInfo);

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when debug-level handling is disabled.
     *
     * @throws NullPointerException if {@code message} is {@code null}
     */
    @Override
    public void debug(final @Nonnull String message) {
        if (isDebugEnabled) {
            Objects.requireNonNull(message, MessageHandlerResourceBundle.get("nullMessageProvided"));
            handleDebugMessageImpl(message, EMPTY_CONTEXT_INFO);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when debug-level handling is disabled.
     *
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws NullPointerException if {@code contextInfo} is {@code null}
     */
    @Override
    public void debug(final @Nonnull String message, final @Nonnull ContextInfo contextInfo) {
        if (isDebugEnabled) {
            Objects.requireNonNull(message, MessageHandlerResourceBundle.get("nullMessageProvided"));
            Objects.requireNonNull(contextInfo, MessageHandlerResourceBundle.get("nullContextInfoProvided"));
            handleDebugMessageImpl(message, contextInfo);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when debug-level handling is disabled.
     *
     * @throws NullPointerException if {@code messageSupplier} is {@code null}
     */
    public void debug(final @Nonnull Supplier<String> messageSupplier) {
        if (isDebugEnabled) {
            Objects.requireNonNull(messageSupplier, MessageHandlerResourceBundle.get("nullMessageSupplierProvided"));
            debug(messageSupplier.get());
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when debug-level handling is disabled.
     *
     * @throws NullPointerException if {@code messageSupplier} is {@code null}
     */
    public void debug(final @Nonnull Supplier<String> messageSupplier, final @Nonnull ContextInfo contextInfo) {
        if (isDebugEnabled) {
            Objects.requireNonNull(messageSupplier, MessageHandlerResourceBundle.get("nullMessageSupplierProvided"));
            debug(messageSupplier.get(), contextInfo);
        }
    }

    /**
     * Performs the actual debug-level message dispatch.
     * <p>
     * Called by {@link #debug(String)} only when debug handling is enabled
     * and the message has been validated as non-null.
     *
     * @param message the validated, non-null debug message to handle
     */
    protected abstract void handleDebugMessageImpl(final String message, final ContextInfo contextInfo);

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when trace-level handling is disabled.
     *
     * @throws NullPointerException if {@code message} is {@code null}
     */
    @Override
    public void trace(final @Nonnull String message) {
        if (isTraceEnabled) {
            Objects.requireNonNull(message, MessageHandlerResourceBundle.get("nullMessageProvided"));
            handleTraceMessageImpl(message, EMPTY_CONTEXT_INFO);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when trace-level handling is disabled.
     *
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws NullPointerException if {@code contextInfo} is {@code null}
     */
    @Override
    public void trace(final @Nonnull String message, final @Nonnull ContextInfo contextInfo) {
        if (isTraceEnabled) {
            Objects.requireNonNull(message, MessageHandlerResourceBundle.get("nullMessageProvided"));
            Objects.requireNonNull(contextInfo, MessageHandlerResourceBundle.get("nullContextInfoProvided"));
            handleTraceMessageImpl(message, contextInfo);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when trace-level handling is disabled.
     *
     * @throws NullPointerException if {@code messageSupplier} is {@code null}
     */
    public void trace(final @Nonnull Supplier<String> messageSupplier) {
        if (isTraceEnabled) {
            Objects.requireNonNull(messageSupplier, MessageHandlerResourceBundle.get("nullMessageSupplierProvided"));
            trace(messageSupplier.get());
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when trace-level handling is disabled.
     *
     * @throws NullPointerException if {@code messageSupplier} is {@code null}
     * @throws NullPointerException if {@code contextInfo} is {@code null}
     */
    public void trace(final @Nonnull Supplier<String> messageSupplier, final @Nonnull ContextInfo contextInfo) {
        if (isTraceEnabled) {
            Objects.requireNonNull(messageSupplier, MessageHandlerResourceBundle.get("nullMessageSupplierProvided"));
            trace(messageSupplier.get(), contextInfo);
        }
    }

    /**
     * Performs the actual trace-level message dispatch.
     * <p>
     * Called by {@link #trace(String)} only when trace handling is enabled
     * and the message has been validated as non-null.
     *
     * @param message the validated, non-null trace message to handle
     */
    protected abstract void handleTraceMessageImpl(final String message, final ContextInfo contextInfo);

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when exception handling is disabled.
     *
     * @throws NullPointerException if {@code exception} is {@code null}
     */
    @Override
    public void exception(final @Nonnull Exception exception) {
        if (isExceptionEnabled) {
            Objects.requireNonNull(exception, MessageHandlerResourceBundle.get("nullExceptionProvided"));
            handleExceptionImpl(exception, EMPTY_CONTEXT_INFO);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when exception handling is disabled.
     *
     * @throws NullPointerException if {@code exception} is {@code null}
     * @throws NullPointerException if {@code contextInfo} is {@code null}
     */
    @Override
    public void exception(final @Nonnull Exception exception, final @Nonnull ContextInfo contextInfo) {
        if (isExceptionEnabled) {
            Objects.requireNonNull(exception, MessageHandlerResourceBundle.get("nullExceptionProvided"));
            Objects.requireNonNull(contextInfo, MessageHandlerResourceBundle.get("nullContextInfoProvided"));
            handleExceptionImpl(exception, contextInfo);
        }
    }

    /**
     * Performs the actual exception dispatch (without a contextual message).
     * <p>
     * Called by {@link #exception(Exception)} only when exception handling is enabled
     * and the exception has been validated as non-null.
     *
     * @param exception the validated, non-null exception to handle
     */
    protected abstract void handleExceptionImpl(final Exception exception, final ContextInfo contextInfo);

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when exception handling is disabled.
     *
     * @throws NullPointerException if either {@code message} or {@code exception} is {@code null}
     */
    public void exception(final @Nonnull String message, final @Nonnull Exception exception) {
        if (isExceptionEnabled) {
            Objects.requireNonNull(message, MessageHandlerResourceBundle.get("nullMessageProvided"));
            Objects.requireNonNull(exception, MessageHandlerResourceBundle.get("nullExceptionProvided"));
            handleExceptionImpl(message, exception, EMPTY_CONTEXT_INFO);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when exception handling is disabled.
     *
     * @throws NullPointerException if either {@code message}, {@code exception} or {@code contextInfo} is {@code null}
     */
    public void exception(final @Nonnull String message, final @Nonnull Exception exception, final @Nonnull ContextInfo contextInfo) {
        if (isExceptionEnabled) {
            Objects.requireNonNull(message, MessageHandlerResourceBundle.get("nullMessageProvided"));
            Objects.requireNonNull(exception, MessageHandlerResourceBundle.get("nullExceptionProvided"));
            Objects.requireNonNull(contextInfo, MessageHandlerResourceBundle.get("nullContextInfoProvided"));
            handleExceptionImpl(message, exception, contextInfo);
        }
    }

    /**
     * Performs the actual exception dispatch with a contextual message.
     * <p>
     * Called by {@link #exception(String, Exception)} only when exception handling is enabled
     * and both arguments have been validated as non-null.
     *
     * @param message   the validated, non-null contextual description of the failure
     * @param exception the validated, non-null exception to handle
     */
    protected abstract void handleExceptionImpl(final String message, final Exception exception, final ContextInfo contextInfo);
}
