package com.threeamigos.common.util.ui;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.function.BooleanSupplier;

/**
 * A class making direct calls to the AWT. Used to decouple other classes and be able to test them
 * in a non-interactive way.
 *
 * @author Stefano Reksten
 */
public class AWTCalls {

    @FunctionalInterface
    interface InvokeAndWait {
        void invoke(Runnable task) throws InterruptedException, InvocationTargetException;
    }

    @FunctionalInterface
    interface ShowMessageDialog {
        void show(Component parentComponent, String message, String title, int icon);
    }

    private AWTCalls() {
    }

    /**
     * Shows a JOptionPane.
     * @param parentComponent the parent window. Can be null.
     * @param message a message to show.
     * @param title a title for the message window.
     * @param icon should be one of the JOptionPane constants.
     */
    public static void showOptionPane(final @Nullable Component parentComponent, final @Nonnull String message,
                                      final @Nonnull String title, final int icon) {
        showOptionPane(parentComponent, message, title, icon,
                GraphicsEnvironment::isHeadless,
                SwingUtilities::isEventDispatchThread,
                SwingUtilities::invokeAndWait,
                JOptionPane::showMessageDialog);
    }

    static void showOptionPane(final @Nullable Component parentComponent, final @Nonnull String message,
                               final @Nonnull String title, final int icon,
                               final BooleanSupplier isHeadless,
                               final BooleanSupplier isEventDispatchThread,
                               final InvokeAndWait invokeAndWait,
                               final ShowMessageDialog showMessageDialog) {
        if (isHeadless.getAsBoolean()) {
            return;
        }
        if (isEventDispatchThread.getAsBoolean()) {
            showMessageDialog.show(parentComponent, message, title, icon);
        } else {
            try {
                invokeAndWait.invoke(() -> showMessageDialog.show(parentComponent, message, title, icon));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            }
        }
    }
}
