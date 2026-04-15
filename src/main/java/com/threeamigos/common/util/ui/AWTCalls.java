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

    /**
     * Functional interface that wraps {@link javax.swing.SwingUtilities#invokeAndWait(Runnable)}.
     * Declared package-private so tests can inject an alternative implementation that avoids
     * touching the real Event Dispatch Thread.
     */
    @FunctionalInterface
    interface InvokeAndWait {
        /**
         * Schedules {@code task} on the EDT and blocks until it completes.
         *
         * @param task the Runnable to run on the EDT
         * @throws InterruptedException      if the calling thread is interrupted while waiting
         * @throws InvocationTargetException if an exception is thrown by {@code task}
         */
        void invoke(Runnable task) throws InterruptedException, InvocationTargetException;
    }

    /**
     * Functional interface that wraps {@link javax.swing.JOptionPane#showMessageDialog}.
     * Declared package-private so tests can inject a no-op or capturing implementation instead
     * of displaying a real dialog.
     */
    @FunctionalInterface
    interface ShowMessageDialog {
        /**
         * Displays a message dialog.
         *
         * @param parentComponent the parent window; may be {@code null}
         * @param message         the message text
         * @param title           the dialog title
         * @param icon            one of the {@link javax.swing.JOptionPane} icon constants
         */
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

    /**
     * Testable overload of {@link #showOptionPane(Component, String, String, int)} that accepts
     * injectable collaborators so unit tests can exercise all code paths without a real display
     * or Event Dispatch Thread.
     *
     * @param parentComponent      the parent window; may be {@code null}
     * @param message              the message text to display
     * @param title                the dialog title
     * @param icon                 one of the {@link javax.swing.JOptionPane} icon constants
     * @param isHeadless           supplier that returns {@code true} when running in a headless environment
     * @param isEventDispatchThread supplier that returns {@code true} when called from the EDT
     * @param invokeAndWait        strategy for scheduling a task on the EDT and waiting for completion
     * @param showMessageDialog    strategy for actually rendering the dialog
     */
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
