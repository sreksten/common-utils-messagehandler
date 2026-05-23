package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.file.DailyRotationPolicy;
import com.threeamigos.common.util.implementations.messagehandler.file.SizeRotationPolicy;
import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordFactoryImpl;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ConsoleLogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.file.RotationPolicy;

import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * MessageHandler implementation that writes log messages to a file.
 * Supports optional async dispatch with a background worker and shutdown hook.
 * <p>
 * Two additional features are available via dedicated constructors:
 * <ul>
 *   <li><strong>Log rotation</strong>: pass a {@link RotationPolicy} to rotate by size
 *       ({@link SizeRotationPolicy}) or by calendar day ({@link DailyRotationPolicy}).</li>
 *   <li><strong>Re-open on external rotation</strong>: pass {@code reopenOnExternalRotation=true}
 *       to have the handler silently re-create the log file when an external tool (e.g.
 *       {@code logrotate}) has moved or deleted it.</li>
 * </ul>
 */
public class FileMessageHandler extends AbstractOutputMessageHandler {

    private static final int LINE_SEPARATOR_BYTES_LENGTH =
            System.lineSeparator().getBytes(StandardCharsets.UTF_8).length;

    private PrintWriter writer;
    private final Path filePath;
    private final Object writeLock = new Object();
    private final RotationPolicy rotationPolicy;
    private final boolean reopenOnExternalRotation;
    private long bytesWritten = 0;
    private volatile Consumer<String> errorConsumer = System.err::println;
    private volatile boolean closeOnWriteError = false;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Creates a synchronous {@code FileMessageHandler} that writes to the given file on the calling thread.
     * <p>
     * Parent directories are created automatically if they do not exist. Appends to the file if it
     * already exists.
     *
     * @param filename path to the log file; must not be {@code null} or blank
     * @throws IllegalArgumentException if the path is null, blank, points to a directory,
     *                                  is not writable, or cannot be created
     */
    public FileMessageHandler(final @Nonnull String filename) {
        this(new LogRecordFactoryImpl(), new ConsoleLogRecordFormatter(), filename, false, 0, false, null, true);
    }

    /**
     * Creates a synchronous {@code FileMessageHandler} that writes to the given file on the calling thread.
     * <p>
     * Parent directories are created automatically if they do not exist. Appends to the file if it
     * already exists.
     *
     * @param filename path to the log file; must not be {@code null} or blank
     * @param formatter formatter to use for log records; must not be {@code null}
     * @throws IllegalArgumentException if the path is null, blank, points to a directory,
     *                                  is not writable, or cannot be created
     */
    public FileMessageHandler(final @Nonnull String filename, final @Nonnull LogRecordFormatter formatter) {
        this(new LogRecordFactoryImpl(), formatter, filename, false, 0, false, null, true);
    }

    /**
     * Creates a synchronous {@code FileMessageHandler} that writes to the given file on the calling thread.
     * <p>
     * Parent directories are created automatically if they do not exist. Appends to the file if it
     * already exists.
     *
     * @param filename path to the log file; must not be {@code null} or blank
     * @param rotationPolicy rotation policy to use; must not be {@code null}
     * @throws IllegalArgumentException if the path is null, blank, points to a directory,
     *                                  is not writable, or cannot be created
     */
    public FileMessageHandler(final @Nonnull String filename, final RotationPolicy rotationPolicy) {
        this(new LogRecordFactoryImpl(), new ConsoleLogRecordFormatter(), filename, false, 0, false, rotationPolicy, true);
    }

    /**
     * Creates a synchronous {@code FileMessageHandler} that writes to the given file on the calling thread.
     * <p>
     * Parent directories are created automatically if they do not exist. Appends to the file if it
     * already exists.
     *
     * @param filename path to the log file; must not be {@code null} or blank
     * @param formatter formatter to use for log records; must not be {@code null}
     * @param rotationPolicy rotation policy to use; must not be {@code null}
     * @throws IllegalArgumentException if the path is null, blank, points to a directory,
     *                                  is not writable, or cannot be created
     */
    public FileMessageHandler(final @Nonnull String filename, final @Nonnull LogRecordFormatter formatter, final RotationPolicy rotationPolicy) {
        this(new LogRecordFactoryImpl(), formatter, filename, false, 0, false, rotationPolicy, true);
    }

    /**
     * Creates a synchronous {@code FileMessageHandler} that writes to the given file on the calling thread.
     * <p>
     * Parent directories are created automatically if they do not exist. Appends to the file if it
     * already exists.
     *
     * @param filename path to the log file; must not be {@code null} or blank
     * @throws IllegalArgumentException if the path is null, blank, points to a directory,
     *                                  is not writable, or cannot be created
     */
    public FileMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                              final @Nonnull LogRecordFormatter logRecordFormatter,
                              final @Nonnull String filename) {
        this(logRecordFactory, logRecordFormatter, filename, false, 0, false, null, false);
    }

    /**
     * Creates a {@code FileMessageHandler} with optional asynchronous dispatch.
     * <p>
     * Parent directories are created automatically if they do not exist. Appends to the file if it
     * already exists.
     *
     * @param filename      path to the log file; must not be {@code null} or blank
     * @param async         {@code true} to dispatch writes via a background worker thread
     * @param queueCapacity maximum number of queued write tasks when async; {@code 0} or negative means unbounded
     * @throws IllegalArgumentException if the path is invalid or the file cannot be opened for writing
     */
    public FileMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                              final @Nonnull LogRecordFormatter logRecordFormatter,
                              final @Nonnull String filename,
                              final boolean async, final int queueCapacity) {
        this(logRecordFactory, logRecordFormatter, filename, async, queueCapacity, false, null, false);
    }

    /**
     * Creates a {@code FileMessageHandler} with optional asynchronous dispatch and an optional
     * JVM shutdown hook.
     * <p>
     * Parent directories are created automatically if they do not exist. Appends to the file if it
     * already exists.
     *
     * @param filename             path to the log file; must not be {@code null} or blank
     * @param async                {@code true} to dispatch writes via a background worker thread
     * @param queueCapacity        maximum number of queued write tasks when async; {@code 0} or negative means unbounded
     * @param registerShutdownHook {@code true} to register a JVM shutdown hook that flushes and
     *                             closes the file when the JVM exits
     * @throws IllegalArgumentException if the path is invalid or the file cannot be opened for writing
     */
    public FileMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                              final @Nonnull LogRecordFormatter logRecordFormatter,
                              final @Nonnull String filename,
                              final boolean async, final int queueCapacity,
                              final boolean registerShutdownHook) {
        this(logRecordFactory, logRecordFormatter, filename, async, queueCapacity, registerShutdownHook, null, false);
    }

    // -------------------------------------------------------------------------
    // Constructors — new API with rotation / re-open support
    // -------------------------------------------------------------------------

    /**
     * Creates a synchronous {@code FileMessageHandler} with log rotation.
     *
     * @param filename       path to the log file; must not be {@code null} or blank
     * @param rotationPolicy policy that decides when and how to rotate the file;
     *                       {@code null} disables rotation
     * @throws IllegalArgumentException if the path is invalid or the file cannot be opened for writing
     */
    public FileMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                              final @Nonnull LogRecordFormatter logRecordFormatter,
                              final @Nonnull String filename,
                              final RotationPolicy rotationPolicy) {
        this(logRecordFactory, logRecordFormatter, filename, false, 0, false, rotationPolicy, false);
    }

    /**
     * Creates a {@code FileMessageHandler} with optional asynchronous dispatch and log rotation.
     *
     * @param filename       path to the log file; must not be {@code null} or blank
     * @param async          {@code true} to dispatch writes via a background worker thread
     * @param queueCapacity  maximum number of queued write tasks when async; {@code 0} or negative means unbounded
     * @param rotationPolicy policy that decides when and how to rotate the file;
     *                       {@code null} disables rotation
     * @throws IllegalArgumentException if the path is invalid or the file cannot be opened for writing
     */
    public FileMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                              final @Nonnull LogRecordFormatter logRecordFormatter,
                              final @Nonnull String filename,
                              final boolean async, final int queueCapacity,
                              final RotationPolicy rotationPolicy) {
        this(logRecordFactory, logRecordFormatter, filename, async, queueCapacity, false, rotationPolicy, false);
    }

    /**
     * Creates a {@code FileMessageHandler} with full control over all options.
     *
     * @param filename                 path to the log file; must not be {@code null} or blank
     * @param async                    {@code true} to dispatch writes via a background worker thread
     * @param queueCapacity            maximum number of queued write tasks when async; {@code 0} or negative means unbounded
     * @param registerShutdownHook     {@code true} to register a JVM shutdown hook
     * @param rotationPolicy           policy that decides when and how to rotate the file;
     *                                 {@code null} disables rotation
     * @param reopenOnExternalRotation {@code true} to silently re-create the log file if it has
     *                                 been deleted or moved by an external tool (e.g. {@code logrotate})
     * @throws IllegalArgumentException if the path is invalid or the file cannot be opened for writing
     */
    public FileMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                              final @Nonnull LogRecordFormatter logRecordFormatter,
                              final @Nonnull String filename,
                              final boolean async, final int queueCapacity,
                              final boolean registerShutdownHook, final RotationPolicy rotationPolicy,
                              final boolean reopenOnExternalRotation) {
        this(logRecordFactory, logRecordFormatter,
                prepareFilePath(filename), async, queueCapacity, registerShutdownHook,
                rotationPolicy, reopenOnExternalRotation);
    }

    private FileMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                               final @Nonnull LogRecordFormatter logRecordFormatter,
                               final @Nonnull Path filePath, final boolean async, final int queueCapacity,
                               final boolean registerShutdownHook, final RotationPolicy rotationPolicy,
                               final boolean reopenOnExternalRotation) {
        super(logRecordFactory, logRecordFormatter);
        this.filePath = filePath;
        this.rotationPolicy = rotationPolicy;
        this.reopenOnExternalRotation = reopenOnExternalRotation;
        try {
            this.writer = openWriter(filePath);
            this.bytesWritten = resolveCurrentFileSize(filePath);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to open log file for writing: " + filePath, e);
        }
        initializeOutputDispatch(async, queueCapacity, registerShutdownHook,
                "FileMessageHandler-async", "FileMessageHandler-shutdown");
    }

    // -------------------------------------------------------------------------
    // Error handling configuration
    // -------------------------------------------------------------------------

    /**
     * Sets the consumer that receives write-error notifications.
     * <p>
     * The consumer is invoked with a localized error message string whenever a write error
     * ({@link PrintWriter#checkError()}), a re-open failure, or a rotation failure occurs.
     * Defaults to {@code System.err::println}. Useful in tests or environments without a console.
     *
     * @param errorConsumer the non-null error notification handler
     */
    public void setErrorConsumer(@Nonnull final Consumer<String> errorConsumer) {
        Objects.requireNonNull(errorConsumer, "errorConsumer must not be null");
        this.errorConsumer = errorConsumer;
    }

    /**
     * Controls whether the handler closes itself automatically after an unrecoverable write error.
     * <p>
     * When {@code true}, a write error detected by {@link PrintWriter#checkError()} (and not
     * resolved by the automatic recovery attempt) triggers {@link #close()}. In async mode the
     * close is dispatched on a new thread to avoid a deadlock with the worker thread.
     *
     * @param closeOnWriteError {@code true} to auto-close on unrecoverable write error
     */
    public void setCloseOnWriteError(final boolean closeOnWriteError) {
        this.closeOnWriteError = closeOnWriteError;
    }

    // -------------------------------------------------------------------------
    // MessageHandler implementation
    // -------------------------------------------------------------------------


    @Override
    public void handleMessage(@Nonnull SeverityNumber level, @Nonnull String message) {
        LogRecord logRecord = createLogRecord(level, message);
        writeMessage(logRecordFormatter.format(logRecord));
    }

    @Override
    protected void handleExceptionInternal(@Nonnull String message, @Nonnull Throwable throwable) {
        LogRecord logRecord = createLogRecord(message, throwable);
        writeMessage(logRecordFormatter.format(logRecord));
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void writeMessage(String message) {
        dispatch(() -> {
            synchronized (writeLock) {
                reopenIfNeeded();
                writer.println(message);
                bytesWritten += message.getBytes(StandardCharsets.UTF_8).length
                        + LINE_SEPARATOR_BYTES_LENGTH;
                checkWriteError();
                rotateIfNeeded();
            }
        });
    }

    private void checkWriteError() {
        if (!writer.checkError()) {
            return;
        }
        // Layer 1: attempt recovery by reopening the writer
        try {
            writer.close();
            writer = openWriter(filePath);
            bytesWritten = resolveCurrentFileSize(filePath);
        } catch (IOException ignored) {
            // Recovery failed; fall through to notification
        }
        // Layer 2: notify via the configurable consumer
        errorConsumer.accept(MessageHandlerResourceBundle.get("fileWriteError"));
        // Layer 3: optionally close
        if (closeOnWriteError) {
            if (isAsync()) {
                new Thread(this::close, "FileMessageHandler-close-on-error").start();
            } else {
                close();
            }
        }
    }

    /**
     * Re-opens the log file if it no longer exists at its expected path.
     * <p>
     * Called under {@code writeLock} before each write when {@code reopenOnExternalRotation} is
     * {@code true}. If the file has been moved or deleted by an external rotator, a new empty
     * file is created at the original path and the writer is replaced.
     */
    private void reopenIfNeeded() {
        if (!reopenOnExternalRotation) {
            return;
        }
        if (!Files.exists(filePath)) {
            try {
                writer.close();
                writer = openWriter(filePath);
                bytesWritten = resolveCurrentFileSize(filePath);
            } catch (IOException e) {
                errorConsumer.accept(MessageHandlerResourceBundle.get("fileReopenError"));
            }
        }
    }

    /**
     * Rotates the log file if the current {@link RotationPolicy} says it should be rotated.
     * <p>
     * Called under {@code writeLock} after each write when a {@code rotationPolicy} is configured.
     * The current file is moved to the path returned by {@link RotationPolicy#rotatedFilePath},
     * a new file is opened at the original path, and {@link RotationPolicy#onRotated()} is called
     * to let the policy reset its state.
     */
    private void rotateIfNeeded() {
        if (rotationPolicy == null || !rotationPolicy.shouldRotate(filePath, bytesWritten)) {
            return;
        }
        Path dest = rotationPolicy.rotatedFilePath(filePath);
        try {
            writer.flush();
            writer.close();
            Files.move(filePath, dest);
            writer = openWriter(filePath);
            bytesWritten = resolveCurrentFileSize(filePath);
            rotationPolicy.onRotated();
        } catch (IOException e) {
            errorConsumer.accept(MessageHandlerResourceBundle.get("fileRotationError"));
        }
    }

    private static long resolveCurrentFileSize(final Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            return 0L;
        }
        return Files.size(filePath);
    }

    private static Path prepareFilePath(final String filename) {
        Objects.requireNonNull(filename, "File path cannot be null or empty");
        if (filename.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }
        Path filePath = Paths.get(filename);
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(filePath) && Files.isDirectory(filePath)) {
                throw new IllegalArgumentException("Path points to a directory: " + filePath);
            }
            if (!Files.exists(filePath)) {
                Files.createFile(filePath);
            }
            if (!Files.isWritable(filePath)) {
                throw new IllegalArgumentException("File is not writable: " + filePath);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to prepare log file: " + filePath, e);
        }
        return filePath;
    }

    /**
     * Opens a buffered, append-mode {@link java.io.PrintWriter} for the given file path,
     * always using UTF-8 encoding regardless of the platform default charset.
     * <p>
     * Exposed as a protected method so that tests can override it to inject a writer that
     * does not touch the file system (e.g., a {@link java.io.StringWriter}-backed writer).
     *
     * @param filePath the validated, non-null path to the log file
     * @return a non-null, buffered {@link java.io.PrintWriter} open for appending in UTF-8
     * @throws IOException if the file cannot be opened
     */
    protected PrintWriter openWriter(Path filePath) throws IOException {
        return new PrintWriter(Files.newBufferedWriter(filePath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND));
    }

    /**
     * Flushes any buffered output to the underlying file.
     * <p>
     * The flush is submitted via {@link #dispatch(Runnable)}, so in asynchronous mode it is
     * enqueued behind any previously dispatched write tasks and executes when the worker thread
     * reaches it. In synchronous mode it executes immediately on the calling thread.
     */
    public void flush() {
        dispatch(() -> {
            synchronized (writeLock) {
                writer.flush();
            }
        });
    }

    @Override
    protected void closeOutput() {
        synchronized (writeLock) {
            writer.flush();
            writer.close();
        }
    }
}
