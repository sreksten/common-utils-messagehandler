package com.threeamigos.common.util.interfaces.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;

/**
 * Master interface for application-level message and exception handling.
 * <p>
 * Aggregates all per-level handler interfaces into a single contract:
 * <ul>
 *   <li>{@link InfoHandler} / {@link InfoSupplierHandler} — informational messages</li>
 *   <li>{@link WarnHandler} / {@link WarnSupplierHandler} — warnings</li>
 *   <li>{@link ErrorHandler} / {@link ErrorSupplierHandler} — non-fatal errors</li>
 *   <li>{@link FatalHandler} / {@link FatalSupplierHandler} — fatal errors</li>
 *   <li>{@link DebugHandler} / {@link DebugSupplierHandler} — debug output</li>
 *   <li>{@link TraceHandler} / {@link TraceSupplierHandler} — fine-grained trace output</li>
 *   <li>{@link ThrowableHandler} — exceptions without a contextual message</li>
 *   <li>{@link ThrowableWithMessageHandler} — exceptions paired with a contextual message</li>
 * </ul>
 * <p>
 * The {@code Supplier}-based variants allow callers to defer the construction of expensive
 * message strings until it is known that the level is enabled, e.g.:
 * <pre>{@code
 * handler.handleDebugMessage(() -> "expensive: " + computeDetails());
 * }</pre>
 * <p>
 * <p>
 * {@code MessageHandler} extends {@link AutoCloseable}, so any handler can be used in a
 * try-with-resources block. The default {@link #close()} implementation is a no-op; output-oriented
 * handlers ({@link com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler},
 * {@link com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler},
 * {@link com.threeamigos.common.util.implementations.messagehandler.JaegerMessageHandler},
 * {@link com.threeamigos.common.util.implementations.messagehandler.GrafanaMessageHandler}) override it
 * to flush and release their underlying resources.
 * <p>
 * Asynchronous dispatch is not part of this interface contract. It is optional and only available
 * in specific output-oriented implementations that provide async queue/worker semantics.
 * <p>
 * The default implementation base class is
 * {@link com.threeamigos.common.util.implementations.messagehandler.AbstractMessageHandler}.
 * Ready-to-use implementations include:
 * <ul>
 *   <li>{@link com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler}</li>
 *   <li>{@link com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler}</li>
 *   <li>{@link com.threeamigos.common.util.implementations.messagehandler.SwingMessageHandler}</li>
 *   <li>{@link com.threeamigos.common.util.implementations.messagehandler.CompositeMessageHandler}
 *       — fan-out to multiple handlers simultaneously</li>
 *   <li>{@link com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler}
 *       — useful for testing</li>
 *   <li>{@link com.threeamigos.common.util.implementations.messagehandler.JULMessageHandler}
 *       — Java Util Logging integration</li>
 *   <li>{@link com.threeamigos.common.util.implementations.messagehandler.Log4JMessageHandler}
 *       — Log4J integration</li>
 *   <li>{@link com.threeamigos.common.util.implementations.messagehandler.SLF4JMessageHandler}
 *       — SLF4J integration</li>
 * </ul>
 *
 * @author Stefano Reksten
 */
public interface MessageHandler extends
        InfoHandler, InfoSupplierHandler,
        WarnHandler, WarnSupplierHandler,
        ErrorHandler, ErrorSupplierHandler,
        FatalHandler, FatalSupplierHandler,
        DebugHandler, DebugSupplierHandler,
        TraceHandler, TraceSupplierHandler,
        ThrowableHandler, ThrowableWithMessageHandler,
        AutoCloseable {

    /**
     * Performs the actual message dispatch.
     * <p>
     * Called only when level handling is enabled
     * and the message has been validated as non-null.
     *
     * @param message the validated, non-null info message to handle
     */
    void handleMessage(final @Nonnull SeverityNumber level, final @Nonnull String message);

    /**
     * Releases any resources held by this handler.
     * <p>
     * The default implementation is a no-op. Output-oriented handlers
     * ({@link com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler},
     * {@link com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler},
     * {@link com.threeamigos.common.util.implementations.messagehandler.JaegerMessageHandler},
     * {@link com.threeamigos.common.util.implementations.messagehandler.GrafanaMessageHandler})
     * override this method to flush pending writes and close the underlying output resource.
     * <p>
     * This method does not declare {@code throws Exception}, narrowing the {@link AutoCloseable}
     * contract so that callers do not need a catch clause for checked exceptions in
     * try-with-resources blocks.
     */
    @Override
    default void close() {
        // No-op by default.
    }
}
