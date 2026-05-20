package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordFactoryImpl;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ConsoleLogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;

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
        this(new LogRecordFactoryImpl(), new ConsoleLogRecordFormatter(), false, 0, false);
    }

    /**
     * Creates a synchronous {@code ConsoleMessageHandler} that writes directly on the calling thread.
     */
    public ConsoleMessageHandler(final @Nonnull LogRecordFormatter logRecordFormatter) {
        this(new LogRecordFactoryImpl(), logRecordFormatter, false, 0, false);
    }

    /**
     * Creates a synchronous {@code ConsoleMessageHandler} that writes directly on the calling thread.
     */
    public ConsoleMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                                 final @Nonnull LogRecordFormatter logRecordFormatter) {
        this(logRecordFactory, logRecordFormatter, false, 0, false);
    }

    /**
     * @param async whether to dispatch logging to a background worker
     * @param queueCapacity capacity for the async queue; 0 or negative => unbounded
     */
    public ConsoleMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                                 final @Nonnull LogRecordFormatter logRecordFormatter,
                                 boolean async, int queueCapacity) {
        this(logRecordFactory, logRecordFormatter, async, queueCapacity, false);
    }

    /**
     * @param async whether to dispatch logging to a background worker
     * @param queueCapacity capacity for the async queue; 0 or negative => unbounded
     * @param registerShutdownHook whether to register a JVM shutdown hook to close the handler
     */
    public ConsoleMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                                 final @Nonnull LogRecordFormatter logRecordFormatter,
                                 boolean async, int queueCapacity, boolean registerShutdownHook) {
        super(logRecordFactory, logRecordFormatter);
        initializeOutputDispatch(async, queueCapacity, registerShutdownHook,
                "ConsoleMessageHandler-async", "ConsoleMessageHandler-shutdown");
    }

    @Override
    public void handleMessage(@Nonnull SeverityNumber level, @Nonnull String message) {
        LogRecord logRecord = createLogRecord(level, message);
        PrintStream stream = level.compareTo(SeverityNumber.ERROR) >= 0 ? System.err : System.out;
        print(stream, logRecordFormatter.format(logRecord));
    }

    @Override
    protected void handleExceptionInternal(@Nonnull String message, @Nonnull Throwable throwable) {
        LogRecord logRecord = createLogRecord(message, throwable);
        String formatted = logRecordFormatter.format(logRecord);
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
