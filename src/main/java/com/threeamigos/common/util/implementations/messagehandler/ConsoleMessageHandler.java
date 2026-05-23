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
 * <p>
 * Default constructors use {@link ConsoleLogRecordFormatter}, which is human-readable text output
 * (not JSON). To emit JSON, provide a custom formatter such as
 * {@link com.threeamigos.common.util.implementations.messagehandler.otel.formatters.RawJsonRecordFormatter}.
 *
 * @author Stefano Reksten
 */
public class ConsoleMessageHandler extends AbstractOutputMessageHandler {

    private static final Object OUT_PRINT_LOCK = new Object();
    private static final Object ERR_PRINT_LOCK = new Object();

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
     * Creates a {@code ConsoleMessageHandler} with optional asynchronous dispatch.
     * <p>
     * Convenience default: when {@code async} is {@code true}, a JVM shutdown hook is
     * registered automatically to close the handler and flush queued messages.
     *
     * @param async whether to dispatch logging to a background worker
     * @param queueCapacity capacity for the async queue; 0 or negative => unbounded
     */
    public ConsoleMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                                 final @Nonnull LogRecordFormatter logRecordFormatter,
                                 boolean async, int queueCapacity) {
        this(logRecordFactory, logRecordFormatter, async, queueCapacity, true);
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
        Object printLock = level.compareTo(SeverityNumber.ERROR) >= 0 ? ERR_PRINT_LOCK : OUT_PRINT_LOCK;
        print(stream, logRecordFormatter.format(logRecord), printLock);
    }

    @Override
    protected void handleExceptionInternal(@Nonnull String message, @Nonnull Throwable throwable) {
        LogRecord logRecord = createLogRecord(message, throwable);
        String formatted = logRecordFormatter.format(logRecord);
        dispatch(() -> {
            synchronized (ERR_PRINT_LOCK) {
                System.err.println(formatted);
            }
        });
    }

    private void print(PrintStream stream, String formatted, Object printLock) {
        Runnable task = () -> {
            synchronized (printLock) {
                stream.println(formatted);
            }
        };
        dispatch(task);
    }
}
