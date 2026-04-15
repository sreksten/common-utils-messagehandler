package com.threeamigos.common.util.interfaces.messagehandler;

/**
 * Master interface for application-level message and exception handling.
 * <p>
 * Aggregates all per-level handler interfaces into a single contract:
 * <ul>
 *   <li>{@link InfoMessageHandler} / {@link SupplierInfoMessageHandler} — informational messages</li>
 *   <li>{@link WarnMessageHandler} / {@link SupplierWarnMessageHandler} — warnings</li>
 *   <li>{@link ErrorMessageHandler} / {@link SupplierErrorMessageHandler} — non-fatal errors</li>
 *   <li>{@link DebugMessageHandler} / {@link SupplierDebugMessageHandler} — debug output</li>
 *   <li>{@link TraceMessageHandler} / {@link SupplierTraceMessageHandler} — fine-grained trace output</li>
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
 * The default implementation base class is
 * {@link com.threeamigos.common.util.implementations.messagehandler.AbstractMessageHandler}.
 * Ready-to-use implementations include:
 * <ul>
 *   <li>{@link com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler}</li>
 *   <li>{@link com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler}</li>
 *   <li>{@link com.threeamigos.common.util.implementations.messagehandler.JULMessageHandler}</li>
 *   <li>{@link com.threeamigos.common.util.implementations.messagehandler.SwingMessageHandler}</li>
 *   <li>{@link com.threeamigos.common.util.implementations.messagehandler.CompositeMessageHandler}
 *       — fan-out to multiple handlers simultaneously</li>
 *   <li>{@link com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler}
 *       — useful for testing</li>
 * </ul>
 *
 * @author Stefano Reksten
 */
public interface MessageHandler extends InfoMessageHandler, SupplierInfoMessageHandler,
        WarnMessageHandler, SupplierWarnMessageHandler,
        ErrorMessageHandler, SupplierErrorMessageHandler,
        DebugMessageHandler, SupplierDebugMessageHandler,
        TraceMessageHandler, SupplierTraceMessageHandler,
        ExceptionHandler, ExceptionWithMessageHandler {
}
