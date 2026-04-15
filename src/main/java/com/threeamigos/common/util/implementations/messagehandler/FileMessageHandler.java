package com.threeamigos.common.util.implementations.messagehandler;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * MessageHandler implementation that writes log messages to a file.
 * Supports optional async dispatch with a background worker and shutdown hook.
 */
public class FileMessageHandler extends AbstractOutputMessageHandler {

    private final PrintWriter writer;
    private final Object writeLock = new Object();

    public FileMessageHandler(final String filename) {
        this(filename, false, 0, false);
    }

    public FileMessageHandler(final String filename, final boolean async, final int queueCapacity) {
        this(filename, async, queueCapacity, false);
    }

    public FileMessageHandler(final String filename, final boolean async, final int queueCapacity, final boolean registerShutdownHook) {
        this(prepareFilePath(filename), async, queueCapacity, registerShutdownHook);
    }

    private FileMessageHandler(final Path filePath, final boolean async, final int queueCapacity,
                               final boolean registerShutdownHook) {
        try {
            this.writer = openWriter(filePath);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to open log file for writing: " + filePath, e);
        }
        initializeOutputDispatch(async, queueCapacity, registerShutdownHook,
                "FileMessageHandler-async", "FileMessageHandler-shutdown");
    }

    @Override
    protected void handleInfoMessageImpl(final String message) {
        writeLine(format("INFO ", message));
    }

    @Override
    protected void handleWarnMessageImpl(final String message) {
        writeLine(format("WARN ", message));
    }

    @Override
    protected void handleErrorMessageImpl(final String message) {
        writeLine(format("ERROR", message));
    }

    @Override
    protected void handleDebugMessageImpl(final String message) {
        writeLine(format("DEBUG", message));
    }

    @Override
    protected void handleTraceMessageImpl(final String message) {
        writeLine(format("TRACE", message));
    }

    @Override
    protected void handleExceptionImpl(final Exception exception) {
        writeLine(format("EXCEP", ExceptionMessageFormatter.detail(exception)));
        dispatch(() -> {
            synchronized (writeLock) {
                exception.printStackTrace(writer);
                writer.flush();
            }
        });
    }

    @Override
    protected void handleExceptionImpl(final String message, final Exception exception) {
        writeLine(format("EXCEP", ExceptionMessageFormatter.withPrefix(message, exception)));
        dispatch(() -> {
            synchronized (writeLock) {
                exception.printStackTrace(writer);
                writer.flush();
            }
        });
    }

    private String format(String level, String message) {
        String date = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return String.format("[%s] [%s] %s", date, level, message);
    }

    private void writeLine(String line) {
        dispatch(() -> {
            synchronized (writeLock) {
                writer.println(line);
                writer.flush();
            }
        });
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

    protected PrintWriter openWriter(Path filePath) throws IOException {
        return new PrintWriter(new BufferedWriter(new FileWriter(filePath.toFile(), true)));
    }

    @Override
    protected void closeOutput() {
        synchronized (writeLock) {
            writer.flush();
            writer.close();
        }
    }
}
