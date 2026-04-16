package com.threeamigos.common.util.implementations.messagehandler;

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

    private volatile boolean isInfoEnabled = true;
    private volatile boolean isWarnEnabled = true;
    private volatile boolean isErrorEnabled = true;
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
     * When set to {@code false}, calls to {@link #handleInfoMessage(String)} and
     * {@link #handleInfoMessage(java.util.function.Supplier)} are silently dropped before
     * reaching the concrete implementation.
     *
     * @param infoEnabled {@code true} to enable, {@code false} to disable
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
     * When set to {@code false}, calls to {@link #handleWarnMessage(String)} and
     * {@link #handleWarnMessage(java.util.function.Supplier)} are silently dropped before
     * reaching the concrete implementation.
     *
     * @param warnEnabled {@code true} to enable, {@code false} to disable
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
     * When set to {@code false}, calls to {@link #handleErrorMessage(String)} and
     * {@link #handleErrorMessage(java.util.function.Supplier)} are silently dropped before
     * reaching the concrete implementation.
     *
     * @param errorEnabled {@code true} to enable, {@code false} to disable
     */
    public void setErrorEnabled(boolean errorEnabled) {
        isErrorEnabled = errorEnabled;
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
     * When set to {@code false}, calls to {@link #handleDebugMessage(String)} and
     * {@link #handleDebugMessage(java.util.function.Supplier)} are silently dropped before
     * reaching the concrete implementation.
     *
     * @param debugEnabled {@code true} to enable, {@code false} to disable
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
     * When set to {@code false}, calls to {@link #handleTraceMessage(String)} and
     * {@link #handleTraceMessage(java.util.function.Supplier)} are silently dropped before
     * reaching the concrete implementation.
     *
     * @param traceEnabled {@code true} to enable, {@code false} to disable
     */
    public void setTraceEnabled(boolean traceEnabled) {
        isTraceEnabled = traceEnabled;
    }

    /**
     * @return {@code true}if exception handling is currently enabled.
     */
    public boolean isExceptionEnabled() {
        return isExceptionEnabled;
    }

    /**
     * Enables or disables exception handling.
     * <p>
     * When set to {@code false}, calls to {@link #handleException(Exception)} and
     * {@link #handleException(String, Exception)} are silently dropped before reaching
     * the concrete implementation.
     *
     * @param exceptionEnabled {@code true} to enable, {@code false} to disable
     */
    public void setExceptionEnabled(boolean exceptionEnabled) {
        isExceptionEnabled = exceptionEnabled;
    }

    /**
     * Forwards an info-level message to the concrete implementation if info messages are enabled.
     * <p>
     * The supplier is evaluated only when info is enabled, avoiding unnecessary string construction
     * for disabled levels.
     *
     * @param messageSupplier a non-null supplier that produces the message string
     * @throws NullPointerException if {@code messageSupplier} is {@code null}
     */
    public void handleInfoMessage(final @Nonnull Supplier<String> messageSupplier) {
        Objects.requireNonNull(messageSupplier, MessageHandlerResourceBundle.BUNDLE.getString("nullMessageSupplierProvided"));
        if (isInfoEnabled) {
            handleInfoMessage(messageSupplier.get());
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
    public void handleInfoMessage(final @Nonnull String message) {
        Objects.requireNonNull(message, MessageHandlerResourceBundle.BUNDLE.getString("nullMessageProvided"));
        if (isInfoEnabled) {
            handleInfoMessageImpl(message);
        }
    }

    /**
     * Performs the actual info-level message dispatch.
     * <p>
     * Called by {@link #handleInfoMessage(String)} only when info handling is enabled
     * and the message has been validated as non-null.
     *
     * @param message the validated, non-null info message to handle
     */
    protected abstract void handleInfoMessageImpl(final String message);

    /**
     * Forwards a warning-level message to the concrete implementation if warn messages are enabled.
     * <p>
     * The supplier is evaluated only when warn is enabled, avoiding unnecessary string construction
     * for disabled levels.
     *
     * @param messageSupplier a non-null supplier that produces the message string
     * @throws NullPointerException if {@code messageSupplier} is {@code null}
     */
    public void handleWarnMessage(final @Nonnull Supplier<String> messageSupplier) {
        Objects.requireNonNull(messageSupplier, MessageHandlerResourceBundle.BUNDLE.getString("nullMessageSupplierProvided"));
        if (isWarnEnabled) {
            handleWarnMessage(messageSupplier.get());
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when warning-level handling is disabled.
     *
     * @throws NullPointerException if {@code message} is {@code null}
     */
    @Override
    public void handleWarnMessage(final @Nonnull String message) {
        Objects.requireNonNull(message, MessageHandlerResourceBundle.BUNDLE.getString("nullMessageProvided"));
        if (isWarnEnabled) {
            handleWarnMessageImpl(message);
        }
    }

    /**
     * Performs the actual warning-level message dispatch.
     * <p>
     * Called by {@link #handleWarnMessage(String)} only when warn handling is enabled
     * and the message has been validated as non-null.
     *
     * @param message the validated, non-null warning message to handle
     */
    protected abstract void handleWarnMessageImpl(final String message);

    /**
     * Forwards an error-level message to the concrete implementation if error messages are enabled.
     * <p>
     * The supplier is evaluated only when error is enabled, avoiding unnecessary string construction
     * for disabled levels.
     *
     * @param messageSupplier a non-null supplier that produces the message string
     * @throws NullPointerException if {@code messageSupplier} is {@code null}
     */
    public void handleErrorMessage(final @Nonnull Supplier<String> messageSupplier) {
        Objects.requireNonNull(messageSupplier, MessageHandlerResourceBundle.BUNDLE.getString("nullMessageSupplierProvided"));
        if (isErrorEnabled) {
            handleErrorMessage(messageSupplier.get());
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when error-level handling is disabled.
     *
     * @throws NullPointerException if {@code message} is {@code null}
     */
    @Override
    public void handleErrorMessage(final @Nonnull String message) {
        Objects.requireNonNull(message, MessageHandlerResourceBundle.BUNDLE.getString("nullMessageProvided"));
        if (isErrorEnabled) {
            handleErrorMessageImpl(message);
        }
    }

    /**
     * Performs the actual error-level message dispatch.
     * <p>
     * Called by {@link #handleErrorMessage(String)} only when error handling is enabled
     * and the message has been validated as non-null.
     *
     * @param message the validated, non-null error message to handle
     */
    protected abstract void handleErrorMessageImpl(final String message);

    /**
     * Forwards a debug-level message to the concrete implementation if debug messages are enabled.
     * <p>
     * The supplier is evaluated only when debug is enabled, avoiding unnecessary string construction
     * for disabled levels.
     *
     * @param messageSupplier a non-null supplier that produces the message string
     * @throws NullPointerException if {@code messageSupplier} is {@code null}
     */
    public void handleDebugMessage(final @Nonnull Supplier<String> messageSupplier) {
        Objects.requireNonNull(messageSupplier, MessageHandlerResourceBundle.BUNDLE.getString("nullMessageSupplierProvided"));
        if (isDebugEnabled) {
            handleDebugMessage(messageSupplier.get());
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when debug-level handling is disabled.
     *
     * @throws NullPointerException if {@code message} is {@code null}
     */
    @Override
    public void handleDebugMessage(final @Nonnull String message) {
        Objects.requireNonNull(message, MessageHandlerResourceBundle.BUNDLE.getString("nullMessageProvided"));
        if (isDebugEnabled) {
            handleDebugMessageImpl(message);
        }
    }

    /**
     * Performs the actual debug-level message dispatch.
     * <p>
     * Called by {@link #handleDebugMessage(String)} only when debug handling is enabled
     * and the message has been validated as non-null.
     *
     * @param message the validated, non-null debug message to handle
     */
    protected abstract void handleDebugMessageImpl(final String message);

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when trace-level handling is disabled.
     *
     * @throws NullPointerException if {@code message} is {@code null}
     */
    @Override
    public void handleTraceMessage(final @Nonnull String message) {
        Objects.requireNonNull(message, MessageHandlerResourceBundle.BUNDLE.getString("nullMessageProvided"));
        if (isTraceEnabled) {
            handleTraceMessageImpl(message);
        }
    }

    /**
     * Forwards a trace-level message to the concrete implementation if trace messages are enabled.
     * <p>
     * The supplier is evaluated only when trace is enabled, avoiding unnecessary string construction
     * for disabled levels.
     *
     * @param messageSupplier a non-null supplier that produces the message string
     * @throws NullPointerException if {@code messageSupplier} is {@code null}
     */
    public void handleTraceMessage(final @Nonnull Supplier<String> messageSupplier) {
        Objects.requireNonNull(messageSupplier, MessageHandlerResourceBundle.BUNDLE.getString("nullMessageSupplierProvided"));
        if (isTraceEnabled) {
            handleTraceMessage(messageSupplier.get());
        }
    }

    /**
     * Performs the actual trace-level message dispatch.
     * <p>
     * Called by {@link #handleTraceMessage(String)} only when trace handling is enabled
     * and the message has been validated as non-null.
     *
     * @param message the validated, non-null trace message to handle
     */
    protected abstract void handleTraceMessageImpl(final String message);

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when exception handling is disabled.
     *
     * @throws NullPointerException if {@code exception} is {@code null}
     */
    @Override
    public void handleException(final @Nonnull Exception exception) {
        Objects.requireNonNull(exception, MessageHandlerResourceBundle.BUNDLE.getString("nullExceptionProvided"));
        if (isExceptionEnabled) {
            handleExceptionImpl(exception);
        }
    }

    /**
     * Performs the actual exception dispatch (without a contextual message).
     * <p>
     * Called by {@link #handleException(Exception)} only when exception handling is enabled
     * and the exception has been validated as non-null.
     *
     * @param exception the validated, non-null exception to handle
     */
    protected abstract void handleExceptionImpl(final Exception exception);

    /**
     * {@inheritDoc}
     * <p>
     * Silently ignores the call when exception handling is disabled.
     *
     * @throws NullPointerException if either {@code message} or {@code exception} is {@code null}
     */
    public void handleException(final @Nonnull String message, final @Nonnull Exception exception) {
        Objects.requireNonNull(message, MessageHandlerResourceBundle.BUNDLE.getString("nullMessageProvided"));
        Objects.requireNonNull(exception, MessageHandlerResourceBundle.BUNDLE.getString("nullExceptionProvided"));
        if (isExceptionEnabled) {
            handleExceptionImpl(message, exception);
        }
    }

    /**
     * Performs the actual exception dispatch with a contextual message.
     * <p>
     * Called by {@link #handleException(String, Exception)} only when exception handling is enabled
     * and both arguments have been validated as non-null.
     *
     * @param message   the validated, non-null contextual description of the failure
     * @param exception the validated, non-null exception to handle
     */
    protected abstract void handleExceptionImpl(final String message, final Exception exception);
}
