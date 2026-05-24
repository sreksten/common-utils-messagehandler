package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.file.DailyRotationPolicy;
import com.threeamigos.common.util.implementations.messagehandler.file.NoRotationPolicy;
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
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * MessageHandler implementation that writes log messages to a file.
 * Supports optional async dispatch with a background worker and shutdown hook.
 * <p>
 * Constructors that do not accept a custom formatter use
 * {@link ConsoleLogRecordFormatter}, which produces human-readable text output (not JSON).
 * To emit JSON lines, pass a custom formatter (for example
 * {@link com.threeamigos.common.util.implementations.messagehandler.otel.formatters.RawJsonRecordFormatter}).
 * For server deployments where console/file logs are ingested by downstream systems, prefer
 * creating handlers through a centralized bootstrap/factory so formatter and field schema remain
 * uniform across modules and environments.
 * <p>
 * A size-based rotation policy is enabled by default for constructors that do not accept an
 * explicit {@link RotationPolicy}. The default threshold is
 * {@value #DEFAULT_SIZE_ROTATION_MAX_BYTES} bytes.
 * <p>
 * Additional features are available via dedicated constructors:
 * <ul>
 *   <li><strong>Log rotation</strong>: pass a {@link RotationPolicy} to rotate by size
 *       ({@link SizeRotationPolicy}), by calendar day ({@link DailyRotationPolicy}), or disable
 *       rotation explicitly with {@link NoRotationPolicy}.</li>
 *   <li><strong>Re-open on external rotation</strong>: pass {@code reopenOnExternalRotation=true}
 *       to have the handler silently re-create the log file when an external tool (e.g.
 *       {@code logrotate}) has moved or deleted it.</li>
 *   <li><strong>Close-on-write-error</strong>: when enabled, asynchronous mode schedules
 *       at most one close request thread, preventing thread proliferation under persistent
 *       write failures.</li>
 *   <li><strong>Inter-process file locking</strong>: opt-in file locking can be enabled for
 *       multi-process or multi-handler scenarios writing to the same file path. Locking uses a
 *       sidecar lock file ({@code .lck}) and Java 8 {@link FileChannel}/{@link FileLock} APIs.</li>
 * </ul>
 */
public class FileMessageHandler extends AbstractOutputMessageHandler {

    private static final int LINE_SEPARATOR_BYTES_LENGTH =
            System.lineSeparator().getBytes(StandardCharsets.UTF_8).length;
    private static final String LOCK_FILE_SUFFIX = ".lck";
    private static final ConcurrentMap<Path, ReentrantLock> LOCK_GUARDS = new ConcurrentHashMap<Path, ReentrantLock>();
    /**
     * Default maximum file size (in bytes) used by no-policy constructors before size-based
     * rotation archives the current log file and starts a new one.
     */
    public static final long DEFAULT_SIZE_ROTATION_MAX_BYTES = 10L * 1024L * 1024L;

    private PrintWriter writer;
    private final Path filePath;
    private final Object writeLock = new Object();
    private final RotationPolicy rotationPolicy;
    private final boolean reopenOnExternalRotation;
    private final boolean interProcessLocking;
    private final Path lockFilePath;
    private final ReentrantLock lockGuard;
    private FileChannel lockFileChannel;
    private long bytesWritten = 0;
    private volatile Consumer<String> errorConsumer = InnerErrorMessageHandler::consume;
    private volatile boolean closeOnWriteError = false;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Creates a synchronous {@code FileMessageHandler} that writes to the given file on the calling thread.
     * <p>
     * Parent directories are created automatically if they do not exist. Appends to the file if it
     * already exists.
     * <p>
     * Uses a default {@link SizeRotationPolicy} with threshold
     * {@value #DEFAULT_SIZE_ROTATION_MAX_BYTES} bytes. To disable rotation, use a constructor
     * that accepts a {@link RotationPolicy} and pass {@link NoRotationPolicy}.
     *
     * @param filename path to the log file; must not be {@code null} or blank
     * @throws IllegalArgumentException if the path is null, blank, points to a directory,
     *                                  is not writable, or cannot be created
     */
    public FileMessageHandler(final @Nonnull String filename) {
        this(new LogRecordFactoryImpl(), new ConsoleLogRecordFormatter(), filename, false, 0, false,
                defaultSizeRotationPolicy(), true, false);
    }

    /**
     * Creates a synchronous {@code FileMessageHandler} that writes to the given file on the
     * calling thread, with optional inter-process locking.
     * <p>
     * Parent directories are created automatically if they do not exist. Appends to the file if it
     * already exists.
     * <p>
     * Uses a default {@link SizeRotationPolicy} with threshold
     * {@value #DEFAULT_SIZE_ROTATION_MAX_BYTES} bytes. To disable rotation, use a constructor
     * that accepts a {@link RotationPolicy} and pass {@link NoRotationPolicy}.
     *
     * @param filename path to the log file; must not be {@code null} or blank
     * @param interProcessLocking {@code true} to enable sidecar-file locking across JVM processes
     * @throws IllegalArgumentException if the path is null, blank, points to a directory,
     *                                  is not writable, or cannot be created
     */
    public FileMessageHandler(final @Nonnull String filename, final boolean interProcessLocking) {
        this(new LogRecordFactoryImpl(), new ConsoleLogRecordFormatter(), filename, false, 0, false,
                defaultSizeRotationPolicy(), true, interProcessLocking);
    }

    /**
     * Creates a synchronous {@code FileMessageHandler} that writes to the given file on the calling thread.
     * <p>
     * Parent directories are created automatically if they do not exist. Appends to the file if it
     * already exists.
     * <p>
     * Uses a default {@link SizeRotationPolicy} with threshold
     * {@value #DEFAULT_SIZE_ROTATION_MAX_BYTES} bytes. To disable rotation, use a constructor
     * that accepts a {@link RotationPolicy} and pass {@link NoRotationPolicy}.
     *
     * @param filename path to the log file; must not be {@code null} or blank
     * @param formatter formatter to use for log records; must not be {@code null}
     * @throws IllegalArgumentException if the path is null, blank, points to a directory,
     *                                  is not writable, or cannot be created
     */
    public FileMessageHandler(final @Nonnull String filename, final @Nonnull LogRecordFormatter formatter) {
        this(new LogRecordFactoryImpl(), formatter, filename, false, 0, false,
                defaultSizeRotationPolicy(), true, false);
    }

    /**
     * Creates a synchronous {@code FileMessageHandler} that writes to the given file on the
     * calling thread, with optional inter-process locking.
     * <p>
     * Parent directories are created automatically if they do not exist. Appends to the file if it
     * already exists.
     * <p>
     * Uses a default {@link SizeRotationPolicy} with threshold
     * {@value #DEFAULT_SIZE_ROTATION_MAX_BYTES} bytes. To disable rotation, use a constructor
     * that accepts a {@link RotationPolicy} and pass {@link NoRotationPolicy}.
     *
     * @param filename path to the log file; must not be {@code null} or blank
     * @param formatter formatter to use for log records; must not be {@code null}
     * @param interProcessLocking {@code true} to enable sidecar-file locking across JVM processes
     * @throws IllegalArgumentException if the path is null, blank, points to a directory,
     *                                  is not writable, or cannot be created
     */
    public FileMessageHandler(final @Nonnull String filename,
                              final @Nonnull LogRecordFormatter formatter,
                              final boolean interProcessLocking) {
        this(new LogRecordFactoryImpl(), formatter, filename, false, 0, false,
                defaultSizeRotationPolicy(), true, interProcessLocking);
    }

    /**
     * Creates a synchronous {@code FileMessageHandler} that writes to the given file on the calling thread.
     * <p>
     * Parent directories are created automatically if they do not exist. Appends to the file if it
     * already exists.
     *
     * @param filename path to the log file; must not be {@code null} or blank
     * @param rotationPolicy rotation policy to use; must not be {@code null}. Pass
     *                       {@link NoRotationPolicy} to disable rotation explicitly.
     * @throws NullPointerException if {@code rotationPolicy} is {@code null}
     * @throws IllegalArgumentException if the path is null, blank, points to a directory,
     *                                  is not writable, or cannot be created
     */
    public FileMessageHandler(final @Nonnull String filename, final RotationPolicy rotationPolicy) {
        this(new LogRecordFactoryImpl(), new ConsoleLogRecordFormatter(), filename, false, 0, false,
                rotationPolicy, true, false);
    }

    /**
     * Creates a synchronous {@code FileMessageHandler} that writes to the given file on the calling thread.
     * <p>
     * Parent directories are created automatically if they do not exist. Appends to the file if it
     * already exists.
     *
     * @param filename path to the log file; must not be {@code null} or blank
     * @param formatter formatter to use for log records; must not be {@code null}
     * @param rotationPolicy rotation policy to use; must not be {@code null}. Pass
     *                       {@link NoRotationPolicy} to disable rotation explicitly.
     * @throws NullPointerException if {@code rotationPolicy} is {@code null}
     * @throws IllegalArgumentException if the path is null, blank, points to a directory,
     *                                  is not writable, or cannot be created
     */
    public FileMessageHandler(final @Nonnull String filename, final @Nonnull LogRecordFormatter formatter, final RotationPolicy rotationPolicy) {
        this(new LogRecordFactoryImpl(), formatter, filename, false, 0, false,
                rotationPolicy, true, false);
    }

    /**
     * Creates a synchronous {@code FileMessageHandler} that writes to the given file on the calling thread.
     * <p>
     * Parent directories are created automatically if they do not exist. Appends to the file if it
     * already exists.
     * <p>
     * Uses a default {@link SizeRotationPolicy} with threshold
     * {@value #DEFAULT_SIZE_ROTATION_MAX_BYTES} bytes. To disable rotation, use a constructor
     * that accepts a {@link RotationPolicy} and pass {@link NoRotationPolicy}.
     *
     * @param filename path to the log file; must not be {@code null} or blank
     * @throws IllegalArgumentException if the path is null, blank, points to a directory,
     *                                  is not writable, or cannot be created
     */
    public FileMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                              final @Nonnull LogRecordFormatter logRecordFormatter,
                              final @Nonnull String filename) {
        this(logRecordFactory, logRecordFormatter, filename, false, 0, false,
                defaultSizeRotationPolicy(), false, false);
    }

    /**
     * Creates a {@code FileMessageHandler} with optional asynchronous dispatch.
     * <p>
     * Parent directories are created automatically if they do not exist. Appends to the file if it
     * already exists.
     * <p>
     * Convenience default: when {@code async} is {@code true}, a JVM shutdown hook is
     * registered automatically to close the handler and flush queued writes.
     * <p>
     * Uses a default {@link SizeRotationPolicy} with threshold
     * {@value #DEFAULT_SIZE_ROTATION_MAX_BYTES} bytes. To disable rotation, use a constructor
     * that accepts a {@link RotationPolicy} and pass {@link NoRotationPolicy}.
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
        this(logRecordFactory, logRecordFormatter, filename, async, queueCapacity, true,
                defaultSizeRotationPolicy(), false, false);
    }

    /**
     * Creates a {@code FileMessageHandler} with optional asynchronous dispatch and an optional
     * JVM shutdown hook.
     * <p>
     * Parent directories are created automatically if they do not exist. Appends to the file if it
     * already exists.
     * <p>
     * Uses a default {@link SizeRotationPolicy} with threshold
     * {@value #DEFAULT_SIZE_ROTATION_MAX_BYTES} bytes. To disable rotation, use a constructor
     * that accepts a {@link RotationPolicy} and pass {@link NoRotationPolicy}.
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
        this(logRecordFactory, logRecordFormatter, filename, async, queueCapacity, registerShutdownHook,
                defaultSizeRotationPolicy(), false, false);
    }

    /**
     * Creates a {@code FileMessageHandler} with optional asynchronous dispatch and optional
     * inter-process locking.
     * <p>
     * Parent directories are created automatically if they do not exist. Appends to the file if it
     * already exists.
     * <p>
     * Uses a default {@link SizeRotationPolicy} with threshold
     * {@value #DEFAULT_SIZE_ROTATION_MAX_BYTES} bytes.
     *
     * @param filename             path to the log file; must not be {@code null} or blank
     * @param async                {@code true} to dispatch writes via a background worker thread
     * @param queueCapacity        maximum number of queued write tasks when async; {@code 0} or negative means unbounded
     * @param registerShutdownHook {@code true} to register a JVM shutdown hook that flushes and
     *                             closes the file when the JVM exits
     * @param interProcessLocking  {@code true} to enable sidecar-file locking across JVM processes
     * @throws IllegalArgumentException if the path is invalid or the file cannot be opened for writing
     */
    public FileMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                              final @Nonnull LogRecordFormatter logRecordFormatter,
                              final @Nonnull String filename,
                              final boolean async, final int queueCapacity,
                              final boolean registerShutdownHook,
                              final boolean interProcessLocking) {
        this(logRecordFactory, logRecordFormatter, filename, async, queueCapacity, registerShutdownHook,
                defaultSizeRotationPolicy(), false, interProcessLocking);
    }

    // -------------------------------------------------------------------------
    // Constructors — new API with rotation / re-open support
    // -------------------------------------------------------------------------

    /**
     * Creates a synchronous {@code FileMessageHandler} with log rotation.
     *
     * @param filename       path to the log file; must not be {@code null} or blank
     * @param rotationPolicy policy that decides when and how to rotate the file; must not be
     *                       {@code null}. Pass {@link NoRotationPolicy} for explicit no-rotation.
     * @throws NullPointerException if {@code rotationPolicy} is {@code null}
     * @throws IllegalArgumentException if the path is invalid or the file cannot be opened for writing
     */
    public FileMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                              final @Nonnull LogRecordFormatter logRecordFormatter,
                              final @Nonnull String filename,
                              final RotationPolicy rotationPolicy) {
        this(logRecordFactory, logRecordFormatter, filename, false, 0, false,
                rotationPolicy, false, false);
    }

    /**
     * Creates a {@code FileMessageHandler} with optional asynchronous dispatch and log rotation.
     * <p>
     * Convenience default: when {@code async} is {@code true}, a JVM shutdown hook is
     * registered automatically to close the handler and flush queued writes.
     *
     * @param filename       path to the log file; must not be {@code null} or blank
     * @param async          {@code true} to dispatch writes via a background worker thread
     * @param queueCapacity  maximum number of queued write tasks when async; {@code 0} or negative means unbounded
     * @param rotationPolicy policy that decides when and how to rotate the file; must not be
     *                       {@code null}. Pass {@link NoRotationPolicy} for explicit no-rotation.
     * @throws NullPointerException if {@code rotationPolicy} is {@code null}
     * @throws IllegalArgumentException if the path is invalid or the file cannot be opened for writing
     */
    public FileMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                              final @Nonnull LogRecordFormatter logRecordFormatter,
                              final @Nonnull String filename,
                              final boolean async, final int queueCapacity,
                              final RotationPolicy rotationPolicy) {
        this(logRecordFactory, logRecordFormatter, filename, async, queueCapacity, true,
                rotationPolicy, false, false);
    }

    /**
     * Creates a {@code FileMessageHandler} with full control over all options.
     *
     * @param filename                 path to the log file; must not be {@code null} or blank
     * @param async                    {@code true} to dispatch writes via a background worker thread
     * @param queueCapacity            maximum number of queued write tasks when async; {@code 0} or negative means unbounded
     * @param registerShutdownHook     {@code true} to register a JVM shutdown hook
     * @param rotationPolicy           policy that decides when and how to rotate the file; must
     *                                 not be {@code null}. Pass {@link NoRotationPolicy} for
     *                                 explicit no-rotation.
     * @param reopenOnExternalRotation {@code true} to silently re-create the log file if it has
     *                                 been deleted or moved by an external tool (e.g. {@code logrotate})
     * @throws NullPointerException if {@code rotationPolicy} is {@code null}
     * @throws IllegalArgumentException if the path is invalid or the file cannot be opened for writing
     */
    public FileMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                              final @Nonnull LogRecordFormatter logRecordFormatter,
                              final @Nonnull String filename,
                              final boolean async, final int queueCapacity,
                              final boolean registerShutdownHook, final RotationPolicy rotationPolicy,
                              final boolean reopenOnExternalRotation) {
        this(logRecordFactory, logRecordFormatter, filename, async, queueCapacity, registerShutdownHook,
                rotationPolicy, reopenOnExternalRotation, false);
    }

    /**
     * Creates a {@code FileMessageHandler} with full control over all options, including optional
     * inter-process file locking.
     * <p>
     * When inter-process locking is enabled, each write acquires an exclusive lock on a sidecar
     * file in the same directory as the log file (for example {@code app.log.lck} for
     * {@code app.log}).
     *
     * @param filename                 path to the log file; must not be {@code null} or blank
     * @param async                    {@code true} to dispatch writes via a background worker thread
     * @param queueCapacity            maximum number of queued write tasks when async; {@code 0} or negative means unbounded
     * @param registerShutdownHook     {@code true} to register a JVM shutdown hook
     * @param rotationPolicy           policy that decides when and how to rotate the file; must
     *                                 not be {@code null}. Pass {@link NoRotationPolicy} for
     *                                 explicit no-rotation.
     * @param reopenOnExternalRotation {@code true} to silently re-create the log file if it has
     *                                 been deleted or moved by an external tool (e.g. {@code logrotate})
     * @param interProcessLocking      {@code true} to enable sidecar-file locking across JVM processes
     * @throws NullPointerException if {@code rotationPolicy} is {@code null}
     * @throws IllegalArgumentException if the path is invalid or the file cannot be opened for writing
     */
    public FileMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                              final @Nonnull LogRecordFormatter logRecordFormatter,
                              final @Nonnull String filename,
                              final boolean async, final int queueCapacity,
                              final boolean registerShutdownHook, final RotationPolicy rotationPolicy,
                              final boolean reopenOnExternalRotation,
                              final boolean interProcessLocking) {
        this(logRecordFactory, logRecordFormatter,
                prepareFilePath(filename), async, queueCapacity, registerShutdownHook,
                rotationPolicy, reopenOnExternalRotation, interProcessLocking);
    }

    private FileMessageHandler(final @Nonnull LogRecordFactory logRecordFactory,
                               final @Nonnull LogRecordFormatter logRecordFormatter,
                               final @Nonnull Path filePath, final boolean async, final int queueCapacity,
                               final boolean registerShutdownHook, final RotationPolicy rotationPolicy,
                               final boolean reopenOnExternalRotation,
                               final boolean interProcessLocking) {
        super(logRecordFactory, logRecordFormatter);
        this.filePath = filePath;
        this.rotationPolicy = Objects.requireNonNull(rotationPolicy, "rotationPolicy must not be null");
        this.reopenOnExternalRotation = reopenOnExternalRotation;
        this.interProcessLocking = interProcessLocking;
        if (interProcessLocking) {
            this.lockFilePath = resolveLockFilePath(filePath);
            this.lockGuard = LOCK_GUARDS.computeIfAbsent(this.lockFilePath, ignored -> new ReentrantLock());
        } else {
            this.lockFilePath = null;
            this.lockGuard = null;
        }
        PrintWriter openedWriter = null;
        try {
            openedWriter = openWriter(filePath);
            this.writer = openedWriter;
            if (interProcessLocking) {
                this.lockFileChannel = openLockFileChannel(this.lockFilePath);
            }
            this.bytesWritten = resolveCurrentFileSize(filePath);
        } catch (IOException e) {
            if (openedWriter != null) {
                openedWriter.close();
            }
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
     * Defaults to {@link InnerErrorMessageHandler#consume(String)} (which defaults to
     * {@code System.err::println}). Useful in tests or environments without a console.
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
     * <p>
     * In async mode, only the first failure schedules a close thread; subsequent failures will
     * not schedule additional close threads.
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
        writeMessage(getLogRecordFormatter().format(logRecord), level);
    }

    @Override
    protected void handleExceptionInternal(@Nonnull String message, @Nonnull Throwable throwable) {
        LogRecord logRecord = createLogRecord(message, throwable);
        writeMessage(getLogRecordFormatter().format(logRecord), SeverityNumber.ERROR);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void writeMessage(String message, SeverityNumber severityNumber) {
        dispatch(() -> {
            final boolean[] failed = new boolean[]{false};
            try {
                withInterProcessFileLock(() -> {
                    synchronized (writeLock) {
                        if (!reopenIfNeeded()) {
                            failed[0] = true;
                        }
                        writer.println(message);
                        if (interProcessLocking) {
                            // Ensure bytes are pushed before releasing the inter-process lock.
                            writer.flush();
                        }
                        bytesWritten += message.getBytes(StandardCharsets.UTF_8).length
                                + LINE_SEPARATOR_BYTES_LENGTH;
                        if (!checkWriteError()) {
                            failed[0] = true;
                        }
                        if (!rotateIfNeeded()) {
                            failed[0] = true;
                        }
                    }
                }, failed);
            } catch (RuntimeException ex) {
                recordOutputFailure();
                throw ex;
            }
            if (failed[0]) {
                recordOutputFailure();
            } else {
                recordOutputSuccess();
            }
        }, severityNumber);
    }

    private boolean checkWriteError() {
        if (!writer.checkError()) {
            return true;
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
        InnerErrorMessageHandler.consume(errorConsumer, MessageHandlerResourceBundle.get("fileWriteError"));
        // Layer 3: optionally close
        requestCloseOnErrorIfEnabled(closeOnWriteError, "FileMessageHandler-close-on-error");
        return false;
    }

    /**
     * Re-opens the log file if it no longer exists at its expected path.
     * <p>
     * Called under {@code writeLock} before each write when {@code reopenOnExternalRotation} is
     * {@code true}. If the file has been moved or deleted by an external rotator, a new empty
     * file is created at the original path and the writer is replaced.
     */
    private boolean reopenIfNeeded() {
        if (!reopenOnExternalRotation) {
            return true;
        }
        if (!Files.exists(filePath)) {
            try {
                writer.close();
                writer = openWriter(filePath);
                bytesWritten = resolveCurrentFileSize(filePath);
            } catch (IOException e) {
                InnerErrorMessageHandler.consume(errorConsumer, MessageHandlerResourceBundle.get("fileReopenError"));
                return false;
            }
        }
        return true;
    }

    /**
     * Rotates the log file if the current {@link RotationPolicy} says it should be rotated.
     * <p>
     * Called under {@code writeLock} after each write when a {@code rotationPolicy} is configured.
     * The current file is moved to the path returned by {@link RotationPolicy#rotatedFilePath},
     * a new file is opened at the original path, and {@link RotationPolicy#onRotated()} is called
     * to let the policy reset its state.
     */
    private boolean rotateIfNeeded() {
        if (!rotationPolicy.shouldRotate(filePath, bytesWritten)) {
            return true;
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
            InnerErrorMessageHandler.consume(errorConsumer, MessageHandlerResourceBundle.get("fileRotationError"));
            return false;
        }
        return true;
    }

    /**
     * Executes the provided write operation while holding the optional inter-process lock.
     * <p>
     * Locking is cooperative: it is effective only when all participating writers use this same
     * locking mode and lock-file path.
     */
    private void withInterProcessFileLock(final Runnable writeOperation, final boolean[] failed) {
        if (!interProcessLocking) {
            writeOperation.run();
            return;
        }

        lockGuard.lock();
        try (FileLock ignored = acquireInterProcessFileLock()) {
            writeOperation.run();
        } catch (OverlappingFileLockException | IOException e) {
            failed[0] = true;
            InnerErrorMessageHandler.consume(errorConsumer, MessageHandlerResourceBundle.get("fileLockError"));
            requestCloseOnErrorIfEnabled(closeOnWriteError, "FileMessageHandler-close-on-lock-error");
        } finally {
            lockGuard.unlock();
        }
    }

    private FileLock acquireInterProcessFileLock() throws IOException {
        ensureLockChannelOpen();
        return lockFileChannel.lock();
    }

    private void ensureLockChannelOpen() throws IOException {
        if (lockFileChannel == null || !lockFileChannel.isOpen()) {
            lockFileChannel = openLockFileChannel(lockFilePath);
        }
    }

    private static long resolveCurrentFileSize(final Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            return 0L;
        }
        return Files.size(filePath);
    }

    private static RotationPolicy defaultSizeRotationPolicy() {
        return new SizeRotationPolicy(DEFAULT_SIZE_ROTATION_MAX_BYTES);
    }

    private static Path resolveLockFilePath(final Path filePath) {
        Path absolute = filePath.toAbsolutePath().normalize();
        String lockFileName = absolute.getFileName().toString() + LOCK_FILE_SUFFIX;
        Path parent = absolute.getParent();
        return parent != null ? parent.resolve(lockFileName) : absolute.resolveSibling(lockFileName);
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
     * Opens (or creates) the sidecar lock file channel used for inter-process locking.
     *
     * @param lockFilePath lock file path (e.g. {@code app.log.lck})
     * @return an open writable channel for the lock file
     * @throws IOException if the lock file cannot be opened
     */
    protected FileChannel openLockFileChannel(final Path lockFilePath) throws IOException {
        return FileChannel.open(lockFilePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
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
            closeLockChannelQuietly();
            if (interProcessLocking) {
                LOCK_GUARDS.remove(lockFilePath);
            }
        }
    }

    private void closeLockChannelQuietly() {
        if (lockFileChannel == null) {
            return;
        }
        try {
            lockFileChannel.close();
        } catch (IOException ignored) {
            // No-op: channel is closing during shutdown path.
        }
    }
}
