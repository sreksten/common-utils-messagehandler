package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.ContextInfo;
import com.threeamigos.common.util.interfaces.messagehandler.LogLevelEnum;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;

import java.io.PrintStream;

/**
 * An implementation of the {@link MessageHandler} interface that uses the
 * console to print info, warning, trace, and debug messages to System.out and errors and
 * exceptions to System.err.
 *
 * @author Stefano Reksten
 */
public class ConsoleMessageHandler extends AbstractOutputMessageHandler {

    private static final Object PRINT_LOCK = new Object();

    /**
     * Creates a synchronous {@code ConsoleMessageHandler} that writes directly on the calling thread.
     */
    public ConsoleMessageHandler() {
        this(false, 0, false);
    }

    /**
     * @param async whether to dispatch logging to a background worker
     * @param queueCapacity capacity for the async queue; 0 or negative => unbounded
     */
    public ConsoleMessageHandler(boolean async, int queueCapacity) {
        this(async, queueCapacity, false);
    }

    /**
     * @param async whether to dispatch logging to a background worker
     * @param queueCapacity capacity for the async queue; 0 or negative => unbounded
     * @param registerShutdownHook whether to register a JVM shutdown hook to close the handler
     */
    public ConsoleMessageHandler(boolean async, int queueCapacity, boolean registerShutdownHook) {
        initializeOutputDispatch(async, queueCapacity, registerShutdownHook,
                "ConsoleMessageHandler-async", "ConsoleMessageHandler-shutdown");
    }

    @Override
    protected void handleInfoMessageImpl(final String message, final ContextInfo contextInfo) {
        print(System.out, getFormatter().format(LogLevelEnum.INFO, message, contextInfo));
    }

    @Override
    protected void handleWarnMessageImpl(final String message, final ContextInfo contextInfo) {
        print(System.out, getFormatter().format(LogLevelEnum.WARN, message, contextInfo));
    }

    @Override
    protected void handleErrorMessageImpl(final String message, final ContextInfo contextInfo) {
        print(System.err, getFormatter().format(LogLevelEnum.ERROR, message, contextInfo));
    }

    @Override
    protected void handleFatalMessageImpl(final String message, final ContextInfo contextInfo) {
        print(System.err, getFormatter().format(LogLevelEnum.FATAL, message, contextInfo));
    }

    @Override
    protected void handleDebugMessageImpl(final String message, final ContextInfo contextInfo) {
        print(System.out, getFormatter().format(LogLevelEnum.DEBUG, message, contextInfo));
    }

    @Override
    protected void handleTraceMessageImpl(final String message, final ContextInfo contextInfo) {
        print(System.out, getFormatter().format(LogLevelEnum.TRACE, message, contextInfo));
    }

    @Override
    protected void handleExceptionImpl(final Exception exception, final ContextInfo contextInfo) {
        String formatted = getFormatter().formatException(exception, contextInfo);
        dispatch(() -> {
            synchronized (PRINT_LOCK) {
                System.err.println(formatted);
            }
        });
    }

    @Override
    protected void handleExceptionImpl(final String message, final Exception exception, final ContextInfo contextInfo) {
        String formatted = getFormatter().formatException(message, exception, contextInfo);
        dispatch(() -> {
            synchronized (PRINT_LOCK) {
                System.err.println(formatted);
            }
        });
    }

    private void print(PrintStream stream, String formatted) {
        Runnable task = () -> {
            synchronized (PRINT_LOCK) {
                stream.println(formatted);
            }
        };
        dispatch(task);
    }
}
