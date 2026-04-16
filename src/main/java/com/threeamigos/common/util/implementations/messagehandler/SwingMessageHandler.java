package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.ui.AWTCalls;

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

    /**
     * Creates a {@code SwingMessageHandler} that uses the given Swing component as the parent
     * window for all {@link javax.swing.JOptionPane} dialogs.
     *
     * @param parentComponent the parent window; may be {@code null} (dialog will be centred on screen)
     */
    public SwingMessageHandler(final Component parentComponent) {
        this.parentComponent = parentComponent;
    }

    /**
     * Creates a {@code SwingMessageHandler} with no parent window.
     * <p>
     * All dialogs will be centred on the screen. The parent can be set later via
     * {@link #setParentComponent(Component)}.
     */
    public SwingMessageHandler() {
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
    public Component getParentComponent() {
        return parentComponent;
    }

    /**
     * Displays a modal {@link javax.swing.JOptionPane} dialog.
     * <p>
     * Delegates to {@link com.threeamigos.common.util.ui.AWTCalls#showOptionPane(java.awt.Component, java.lang.String, java.lang.String, int)} which
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

    /**
     * Displays the message in an information dialog with a localized title.
     *
     * @param message the info-level text to display
     */
    protected void handleInfoMessageImpl(final String message) {
        showOptionPane(message, MessageHandlerResourceBundle.BUNDLE.getString("info"), JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Displays the message in a warning dialog with a localized title.
     *
     * @param message the warn-level text to display
     */
    protected void handleWarnMessageImpl(final String message) {
        showOptionPane(message, MessageHandlerResourceBundle.BUNDLE.getString("warning"), JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Displays the message in an error dialog with a localized title.
     *
     * @param message the error-level text to display
     */
    protected void handleErrorMessageImpl(final String message) {
        showOptionPane(message, MessageHandlerResourceBundle.BUNDLE.getString("error"), JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Displays the message in an information dialog with a localized title.
     * <p>
     * Debug output uses {@link JOptionPane#INFORMATION_MESSAGE} rather than a dedicated
     * icon because Swing provides no built-in debug icon type.
     *
     * @param message the debug-level text to display
     */
    protected void handleDebugMessageImpl(final String message) {
        showOptionPane(message, MessageHandlerResourceBundle.BUNDLE.getString("debug"), JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Displays the message in an information dialog with a localized title.
     * <p>
     * Trace output uses {@link JOptionPane#INFORMATION_MESSAGE} rather than a dedicated
     * icon because Swing provides no built-in trace icon type.
     *
     * @param message the trace-level text to display
     */
    protected void handleTraceMessageImpl(final String message) {
        showOptionPane(message, MessageHandlerResourceBundle.BUNDLE.getString("trace"), JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Displays the exception detail in an error dialog with a localized title.
     * <p>
     * The displayed text is produced by {@link ExceptionMessageFormatter#detail(Exception)}:
     * {@link Exception#getMessage()} when non-null, otherwise {@link Exception#toString()}.
     *
     * @param exception the exception to display
     */
    protected void handleExceptionImpl(final Exception exception) {
        showOptionPane(ExceptionMessageFormatter.detail(exception), MessageHandlerResourceBundle.BUNDLE.getString("exception"), JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Displays the contextual message and exception detail in an error dialog with a localized title.
     * <p>
     * The displayed text is produced by {@link ExceptionMessageFormatter#withPrefix(String, Exception)},
     * yielding {@code "<message>: <detail>"}.
     *
     * @param message   a contextual prefix describing where or why the exception occurred
     * @param exception the exception to display
     */
    protected void handleExceptionImpl(final String message, final Exception exception) {
        showOptionPane(ExceptionMessageFormatter.withPrefix(message, exception), MessageHandlerResourceBundle.BUNDLE.getString("exception"), JOptionPane.ERROR_MESSAGE);
    }
}
