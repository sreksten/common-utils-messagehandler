package com.threeamigos.common.util.interfaces.messagehandler;

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
 *   <li>{@link ExceptionHandler} — exceptions without a contextual message</li>
 *   <li>{@link ExceptionWithMessageHandler} — exceptions paired with a contextual message</li>
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
 * {@link com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler}) override it
 * to flush and release their underlying resources.
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
        InfoHandler, InfoWithContextHandler, InfoSupplierHandler, InfoSupplierWithContextHandler,
        WarnHandler, WarnWithContextHandler, WarnSupplierHandler, WarnSupplierWithContextHandler,
        ErrorHandler, ErrorWithContextHandler, ErrorSupplierHandler, ErrorSupplierWithContextHandler,
        FatalHandler, FatalWithContextHandler, FatalSupplierHandler, FatalSupplierWithContextHandler,
        DebugHandler, DebugWithContextHandler, DebugSupplierHandler, DebugSupplierWithContextHandler,
        TraceHandler, TraceWithContextHandler, TraceSupplierHandler, TraceSupplierWithContextHandler,
        ExceptionHandler, ExceptionWithContextHandler, ExceptionWithMessageHandler, ExceptionWithMessageAndContextHandler,
        AutoCloseable {

    /**
     * Releases any resources held by this handler.
     * <p>
     * The default implementation is a no-op. Output-oriented handlers
     * ({@link com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler},
     * {@link com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler})
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
