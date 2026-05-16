package com.threeamigos.common.util.implementations.messagehandler.utils.ui;

import com.threeamigos.common.util.implementations.messagehandler.utils.ui.AWTCalls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AWTCalls unit tests")
class AWTCallsUnitTest {

    @Test
    @DisplayName("Private constructor should be invokable via reflection")
    void privateConstructorShouldBeInvokableViaReflection() throws Exception {
        Constructor<AWTCalls> constructor = AWTCalls.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        AWTCalls instance = constructor.newInstance();
        assertNotNull(instance);
    }

    @Test
    @DisplayName("Public API should no-op when headless")
    void shouldNoOpWhenHeadless() {
        String original = System.getProperty("java.awt.headless");
        System.setProperty("java.awt.headless", "true");
        try {
            assertDoesNotThrow(() -> AWTCalls.showOptionPane(null, "msg", "title", JOptionPane.INFORMATION_MESSAGE));
        } finally {
            if (original == null) {
                System.clearProperty("java.awt.headless");
            } else {
                System.setProperty("java.awt.headless", original);
            }
        }
    }

    @Test
    @DisplayName("Internal API should skip display when headless")
    void shouldSkipDisplayWhenHeadless() {
        AtomicBoolean dialogShown = new AtomicBoolean(false);
        AtomicBoolean invokeAndWaitCalled = new AtomicBoolean(false);

        AWTCalls.showOptionPane(
                null,
                "message",
                "title",
                JOptionPane.INFORMATION_MESSAGE,
                () -> true,
                () -> false,
                runnable -> invokeAndWaitCalled.set(true),
                (parent, message, title, icon) -> dialogShown.set(true)
        );

        assertFalse(dialogShown.get());
        assertFalse(invokeAndWaitCalled.get());
    }

    @Test
    @DisplayName("Internal API should display directly on EDT")
    void shouldDisplayDirectlyOnEdt() {
        AtomicBoolean dialogShown = new AtomicBoolean(false);
        AtomicBoolean invokeAndWaitCalled = new AtomicBoolean(false);

        AWTCalls.showOptionPane(
                null,
                "message",
                "title",
                JOptionPane.WARNING_MESSAGE,
                () -> false,
                () -> true,
                runnable -> invokeAndWaitCalled.set(true),
                (parent, message, title, icon) -> dialogShown.set(true)
        );

        assertTrue(dialogShown.get());
        assertFalse(invokeAndWaitCalled.get());
    }

    @Test
    @DisplayName("Internal API should invoke and wait off EDT")
    void shouldInvokeAndWaitOffEdt() {
        AtomicBoolean dialogShown = new AtomicBoolean(false);
        AtomicBoolean invokeAndWaitCalled = new AtomicBoolean(false);

        AWTCalls.showOptionPane(
                null,
                "message",
                "title",
                JOptionPane.ERROR_MESSAGE,
                () -> false,
                () -> false,
                runnable -> {
                    invokeAndWaitCalled.set(true);
                    runnable.run();
                },
                (parent, message, title, icon) -> dialogShown.set(true)
        );

        assertTrue(invokeAndWaitCalled.get());
        assertTrue(dialogShown.get());
    }

    @Test
    @DisplayName("Internal API should restore interrupt flag on InterruptedException")
    void shouldRestoreInterruptFlagOnInterruptedException() {
        assertFalse(Thread.currentThread().isInterrupted());
        try {
            AWTCalls.showOptionPane(
                    null,
                    "message",
                    "title",
                    JOptionPane.ERROR_MESSAGE,
                    () -> false,
                    () -> false,
                    runnable -> {
                        throw new InterruptedException("forced");
                    },
                    (parent, message, title, icon) -> {
                    }
            );
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("Internal API should wrap InvocationTargetException cause")
    void shouldWrapInvocationTargetExceptionCause() {
        AtomicReference<RuntimeException> thrown = new AtomicReference<>();
        IllegalStateException cause = new IllegalStateException("forced");

        try {
            AWTCalls.showOptionPane(
                    null,
                    "message",
                    "title",
                    JOptionPane.ERROR_MESSAGE,
                    () -> false,
                    () -> false,
                    runnable -> {
                        throw new InvocationTargetException(cause);
                    },
                    (parent, message, title, icon) -> {
                    }
            );
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            thrown.set(e);
        }

        assertNotNull(thrown.get());
        assertEquals(cause, thrown.get().getCause());
    }
}
