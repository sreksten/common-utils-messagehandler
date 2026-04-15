package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;

import java.io.PrintStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * An implementation of the {@link MessageHandler} interface that uses the
 * console to print info, warning, trace, and debug messages to System.out and errors and
 * exceptions to System.err.
 *
 * @author Stefano Reksten
 */
public class ConsoleMessageHandler extends AbstractOutputMessageHandler {

    private static final Object PRINT_LOCK = new Object();

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
    protected void handleInfoMessageImpl(final String message) {
        print(System.out, format("INFO ", message));
    }

    @Override
    protected void handleWarnMessageImpl(final String message) {
        print(System.out, format("WARN ", message));
    }

    @Override
    protected void handleErrorMessageImpl(final String message) {
        print(System.err, format("ERROR", message));
    }

    @Override
    protected void handleDebugMessageImpl(final String message) {
        print(System.out, format("DEBUG", message));
    }

    @Override
    protected void handleTraceMessageImpl(final String message) {
        print(System.out, format("TRACE", message));
    }

    @Override
    protected void handleExceptionImpl(final Exception exception) {
        Runnable task = () -> {
            synchronized (PRINT_LOCK) {
                System.err.println(format("EXCEP", exception.getMessage()));
                exception.printStackTrace(System.err);
            }
        };
        dispatch(task);
    }

    @Override
    protected void handleExceptionImpl(final String message, final Exception exception) {
        Runnable task = () -> {
            synchronized (PRINT_LOCK) {
                System.err.println(format("EXCEP", message));
                System.err.println(format("EXCEP", exception.getMessage()));
                exception.printStackTrace(System.err);
            }
        };
        dispatch(task);
    }

    private String format(String level, String message) {
        String date = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return String.format("[%s] [%s] %s", date, level, message);
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
