package com.threeamigos.common.util.implementations.messagehandler;

import jakarta.annotation.Nonnull;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Global non-throwing sink for internal logging-system failures.
 * <p>
 * This utility centralizes reporting for errors raised while handlers process user log calls
 * (for example, backend dispatch failures or failing error-consumer callbacks).
 * <p>
 * The global consumer is process-wide and thread-safe. By default, it writes to {@code System.err}.
 */
public final class InnerErrorMessageHandler {

    /**
     *  If System.err::println is used as a static default consumer, that method reference binds to the System.err
     *  instance at class-load time, so when tests later do System.setErr(...) (like FileMessageHandlerUnitTest lines
     *  729/919 expectations), messages can go to an old stream, and assertions see empty output when the full suite
     *  order changes.
     */
    @SuppressWarnings("Convert2MethodRef")
    private static final Consumer<String> DEFAULT_GLOBAL_CONSUMER = message -> System.err.println(message);

    private static final AtomicReference<Consumer<String>> GLOBAL_CONSUMER =
            new AtomicReference<>(DEFAULT_GLOBAL_CONSUMER);

    private InnerErrorMessageHandler() {
        // Utility class
    }

    /**
     * Sets the process-wide global consumer used to report internal handler failures.
     *
     * @param globalConsumer non-null global consumer
     * @throws NullPointerException if {@code globalConsumer} is {@code null}
     */
    public static void setGlobalConsumer(final @Nonnull Consumer<String> globalConsumer) {
        GLOBAL_CONSUMER.set(Objects.requireNonNull(globalConsumer, "globalConsumer must not be null"));
    }

    /**
     * Returns the currently configured process-wide global consumer.
     *
     * @return current global consumer
     */
    public static Consumer<String> getGlobalConsumer() {
        return GLOBAL_CONSUMER.get();
    }

    /**
     * Restores the default global consumer ({@code System.err::println}).
     */
    public static void resetGlobalConsumer() {
        GLOBAL_CONSUMER.set(DEFAULT_GLOBAL_CONSUMER);
    }

    /**
     * Emits one internal-failure message to the configured global consumer.
     * <p>
     * If the global consumer throws at runtime, a best-effort fallback to the default
     * {@code System.err} consumer is attempted. This method never throws.
     *
     * @param message message to emit; {@code null} is ignored
     */
    public static void consume(final String message) {
        if (message == null) {
            return;
        }
        if (safeConsume(GLOBAL_CONSUMER.get(), message)) {
            return;
        }
        safeConsume(DEFAULT_GLOBAL_CONSUMER, message);
    }

    /**
     * Emits one internal-failure message to a local consumer, with fallback to the global consumer.
     * <p>
     * If {@code localConsumer} is null or throws at runtime, the message is forwarded to
     * {@link #consume(String)}. This method never throws.
     *
     * @param localConsumer local consumer to try first, nullable
     * @param message message to emit; {@code null} is ignored
     */
    public static void consume(final Consumer<String> localConsumer, final String message) {
        if (message == null) {
            return;
        }
        if (safeConsume(localConsumer, message)) {
            return;
        }
        consume(message);
    }

    private static boolean safeConsume(final Consumer<String> consumer, final String message) {
        if (consumer == null) {
            return false;
        }
        try {
            consumer.accept(message);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
