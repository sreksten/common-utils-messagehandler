package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.ui.AWTCalls;

import javax.swing.*;
import java.awt.*;
import java.util.ResourceBundle;

/**
 * An implementation of the {@link MessageHandler} interface that uses an
 * OptionPane to show messages and exceptions to the user.
 *
 * @author Stefano Reksten
 */
public class SwingMessageHandler extends AbstractMessageHandler {

    private static final ResourceBundle BUNDLE = MessageHandlerResourceBundles.load(
            "com.threeamigos.common.util.implementations.messagehandler.SwingMessageHandler.SwingMessageHandler",
            SwingMessageHandler.class);

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
     * All dialogs will be centred on screen. The parent can be set later via
     * {@link #setParentComponent(Component)}.
     */
    public SwingMessageHandler() {
    }

    /**
     * Sets the parent window used to position {@link javax.swing.JOptionPane} dialogs.
     *
     * @param parentComponent the parent window; may be {@code null} to centre on screen
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

    protected void handleInfoMessageImpl(final String message) {
        showOptionPane(message, BUNDLE.getString("info"), JOptionPane.INFORMATION_MESSAGE);
    }

    protected void handleWarnMessageImpl(final String message) {
        showOptionPane(message, BUNDLE.getString("warning"), JOptionPane.WARNING_MESSAGE);
    }

    protected void handleErrorMessageImpl(final String message) {
        showOptionPane(message, BUNDLE.getString("error"), JOptionPane.ERROR_MESSAGE);
    }

    protected void handleDebugMessageImpl(final String message) {
        showOptionPane(message, BUNDLE.getString("debug"), JOptionPane.INFORMATION_MESSAGE);
    }

    protected void handleTraceMessageImpl(final String message) {
        showOptionPane(message, BUNDLE.getString("trace"), JOptionPane.INFORMATION_MESSAGE);
    }

    protected void handleExceptionImpl(final Exception exception) {
        showOptionPane(ExceptionMessageFormatter.detail(exception), BUNDLE.getString("exception"), JOptionPane.ERROR_MESSAGE);
    }

    protected void handleExceptionImpl(final String message, final Exception exception) {
        showOptionPane(ExceptionMessageFormatter.withPrefix(message, exception), BUNDLE.getString("exception"), JOptionPane.ERROR_MESSAGE);
    }
}
