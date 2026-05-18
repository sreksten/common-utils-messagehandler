package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import com.threeamigos.common.util.implementations.messagehandler.utils.ui.AWTCalls;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * An implementation of the {@link MessageHandler} interface that uses an
 * OptionPane to show messages and exceptions to the user.
 *
 * @author Stefano Reksten
 */
public class SwingMessageHandler extends AbstractMessageHandler {

    private Component parentComponent;
    private final LogRecordFactory logRecordFactory;
    private final LogRecordFormatter logRecordFormatter;

    /**
     * Creates a {@code SwingMessageHandler} that uses the given Swing component as the parent
     * window for all {@link javax.swing.JOptionPane} dialogs.
     *
     * @param parentComponent the parent window; may be {@code null} (dialog will be centred on screen)
     */
    public SwingMessageHandler(final Component parentComponent) {
        this(parentComponent, null, null);
    }

    public SwingMessageHandler(final Component parentComponent,
                               final @Nullable LogRecordFactory logRecordFactory,
                               final @Nullable LogRecordFormatter logRecordFormatter) {
        this.parentComponent = parentComponent;
        this.logRecordFactory = logRecordFactory;
        this.logRecordFormatter = logRecordFormatter;
    }

    /**
     * Creates a {@code SwingMessageHandler} with no parent window.
     * <p>
     * All dialogs will be centred on the screen. The parent can be set later via
     * {@link #setParentComponent(Component)}.
     */
    public SwingMessageHandler() {
        this(null, null, null);
    }

    public SwingMessageHandler(final @Nullable LogRecordFactory logRecordFactory,
                               final @Nullable LogRecordFormatter logRecordFormatter) {
        this(null, logRecordFactory, logRecordFormatter);
    }

    /**
     * Sets the parent window used to position {@link javax.swing.JOptionPane} dialogs.
     *
     * @param parentComponent the parent window; may be {@code null} to center on screen
     */
    public void setParentComponent(final Component parentComponent) {
        this.parentComponent = parentComponent;
    }

    /**
     * Returns the parent window currently used to position dialogs, or {@code null} if none
     * has been set.
     *
     * @return the parent {@link Component}, or {@code null}
     */
    public @Nullable Component getParentComponent() {
        return parentComponent;
    }

    /**
     * Displays a modal {@link javax.swing.JOptionPane} dialog.
     * <p>
     * Delegates to {@link AWTCalls#showOptionPane(java.awt.Component, java.lang.String, java.lang.String, int)} which
     * handles headless environments (silently suppresses the dialog) and ensures the call
     * runs on the Event Dispatch Thread.
     *
     * @param message the text to display in the dialog body
     * @param title   the dialog window title
     * @param icon    one of the {@link javax.swing.JOptionPane} icon constants
     *                (e.g. {@link javax.swing.JOptionPane#INFORMATION_MESSAGE})
     */
    protected void showOptionPane(final String message, final String title, final int icon) {
        AWTCalls.showOptionPane(parentComponent, message, title, icon);
    }

    @Override
    public void handleMessage(@Nonnull SeverityNumber level, @Nonnull String message) {
        String rendered = formatMessage(level, message);
        switch (level) {
            case WARN:
            case WARN2:
            case WARN3:
            case WARN4:
                showOptionPane(rendered, MessageHandlerResourceBundle.get("warning"), JOptionPane.WARNING_MESSAGE);
                break;
            case ERROR:
            case ERROR2:
            case ERROR3:
            case ERROR4:
                showOptionPane(rendered, MessageHandlerResourceBundle.get("error"), JOptionPane.ERROR_MESSAGE);
                break;
            case FATAL:
            case FATAL2:
            case FATAL3:
            case FATAL4:
                showOptionPane(rendered, MessageHandlerResourceBundle.get("fatal"), JOptionPane.ERROR_MESSAGE);
                break;
            case DEBUG:
            case DEBUG2:
            case DEBUG3:
            case DEBUG4:
                showOptionPane(rendered, MessageHandlerResourceBundle.get("debug"), JOptionPane.INFORMATION_MESSAGE);
                break;
            case TRACE:
            case TRACE2:
            case TRACE3:
            case TRACE4:
                showOptionPane(rendered, MessageHandlerResourceBundle.get("trace"), JOptionPane.INFORMATION_MESSAGE);
                break;
            default:
                showOptionPane(rendered, MessageHandlerResourceBundle.get("info"), JOptionPane.INFORMATION_MESSAGE);
                break;
        }
    }

    @Override
    protected void handleExceptionInternal(@Nonnull String message, @Nonnull Throwable throwable) {
        String rendered = formatExceptionMessage(message, throwable);
        showOptionPane(ThrowableMessageFormatter.withPrefix(rendered, throwable), MessageHandlerResourceBundle.get("exception"), JOptionPane.ERROR_MESSAGE);
    }

    private String formatMessage(final SeverityNumber level, final String message) {
        if (logRecordFactory == null || logRecordFormatter == null) {
            return message;
        }
        LogRecord logRecord = logRecordFactory.create(level, message);
        return logRecordFormatter.format(logRecord);
    }

    private String formatExceptionMessage(final String message, final Throwable throwable) {
        if (logRecordFactory == null || logRecordFormatter == null) {
            return message;
        }
        LogRecord logRecord = logRecordFactory.create(message, throwable);
        return logRecordFormatter.format(logRecord);
    }
}
