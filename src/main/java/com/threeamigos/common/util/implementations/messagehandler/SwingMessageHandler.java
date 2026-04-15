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

    public SwingMessageHandler(final Component parentComponent) {
        this.parentComponent = parentComponent;
    }

    public SwingMessageHandler() {
    }

    public void setParentComponent(final Component parentComponent) {
        this.parentComponent = parentComponent;
    }

    public Component getParentComponent() {
        return parentComponent;
    }

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
