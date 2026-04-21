package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordFactoryImpl;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.RawJsonRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileMessageHandler unit tests")
@Tag("unit")
@Tag("messageHandler")
class FileMessageHandlerUnitTest {

    private static final LogRecordFactory FACTORY = new LogRecordFactoryImpl();
    private static final LogRecordFormatter DEFAULT_FORMATTER = logRecord -> {
        String severity = logRecord.getSeverityText() == null ? "UNSPEC" : logRecord.getSeverityText();
        if (severity.length() < 5) {
            severity = String.format("%-5s", severity);
        } else if (severity.length() > 5) {
            severity = severity.substring(0, 5);
        }
        return "[" + logRecord.getTimestamp() + "] [" + severity + "] " + stringifyBody(logRecord.getBody());
    };

    private static String stringifyBody(final AnyValue body) {
        if (body == null || body.getType() == null) {
            return "";
        }
        if (body.getType() == AnyValue.Type.STRING) {
            return body.asString() == null ? "" : body.asString();
        }
        return String.valueOf(body.asString());
    }

    private static class FailingOpenWriterFileMessageHandler extends FileMessageHandler {
        private FailingOpenWriterFileMessageHandler(String filename) {
            super(FACTORY, DEFAULT_FORMATTER, filename);
        }

        @Override
        protected PrintWriter openWriter(Path filePath) throws IOException {
            throw new IOException("forced");
        }
    }

    private static class ThrowingAwaitTerminationFileMessageHandler extends FileMessageHandler {
        private ThrowingAwaitTerminationFileMessageHandler(String filename) {
            super(FACTORY, DEFAULT_FORMATTER, filename, true, 1, false);
        }

        @Override
        protected void awaitTermination() throws InterruptedException {
            throw new InterruptedException("forced");
        }
    }

    private static class ThrowingRemoveHookFileMessageHandler extends FileMessageHandler {
        private ThrowingRemoveHookFileMessageHandler(String filename) {
            super(FACTORY, DEFAULT_FORMATTER, filename, true, 1, true);
        }

        @Override
        protected void removeShutdownHook(Thread hook) {
            throw new IllegalStateException("forced");
        }
    }

    private static class ErrorCheckWriterFileMessageHandler extends FileMessageHandler {
        private ErrorCheckWriterFileMessageHandler(String filename) {
            super(FACTORY, DEFAULT_FORMATTER, filename);
        }

        @Override
        protected PrintWriter openWriter(Path filePath) throws IOException {
            return new PrintWriter(new java.io.StringWriter()) {
                @Override
                public boolean checkError() {
                    return true;
                }
            };
        }
    }

    /**
     * Succeeds when the file exists (initial open at construction time),
     * throws when the file is absent (simulates a re-open failure after external deletion).
     */
    private static class FailingReopenFileMessageHandler extends FileMessageHandler {
        private FailingReopenFileMessageHandler(String filename) {
            super(FACTORY, DEFAULT_FORMATTER, filename, false, 0, false, null, true);
        }

        @Override
        protected PrintWriter openWriter(Path filePath) throws IOException {
            if (Files.exists(filePath)) {
                return super.openWriter(filePath);
            }
            throw new IOException("forced reopen failure");
        }
    }

    private static class AsyncErrorFileMessageHandler extends FileMessageHandler {
        private AsyncErrorFileMessageHandler(String filename) {
            super(FACTORY, DEFAULT_FORMATTER, filename, true, 64);
        }

        @Override
        protected PrintWriter openWriter(Path filePath) throws IOException {
            return new PrintWriter(new java.io.StringWriter()) {
                @Override
                public boolean checkError() {
                    return true;
                }
            };
        }
    }

    /**
     * First call (construction) returns an error writer; any subsequent call (recovery) throws IOException.
     * Covers the {@code catch (IOException ignored)} block in {@code checkWriteError()}.
     * <p>
     * Uses a flag without an explicit initializer so that the parent constructor's call to
     * {@code openWriter()} can set it before subclass field initializers run (Java field initializers
     * execute after {@code super()} returns, so an explicit {@code = false} would reset the flag).
     */
    private static class FailingRecoveryFileMessageHandler extends FileMessageHandler {
        // No explicit initializer — stays false (default) until the first openWriter() call sets it
        private boolean constructionComplete;

        private FailingRecoveryFileMessageHandler(String filename) {
            super(FACTORY, DEFAULT_FORMATTER, filename);
        }

        @Override
        protected PrintWriter openWriter(Path filePath) throws IOException {
            if (!constructionComplete) {
                constructionComplete = true;
                return new PrintWriter(new java.io.StringWriter()) {
                    @Override
                    public boolean checkError() {
                        return true;
                    }
                };
            }
            throw new IOException("forced recovery failure");
        }
    }

    /**
     * First call (construction) returns a real writer; the second call (post-rotation re-open) throws.
     * Uses {@link Files#exists} as a discriminator: the file exists at construction time but is
     * absent after {@code Files.move()} in {@code rotateIfNeeded()}.
     * Covers the {@code catch (IOException e)} block in {@code rotateIfNeeded()}.
     */
    private static class FailingRotationFileMessageHandler extends FileMessageHandler {
        private FailingRotationFileMessageHandler(String filename) {
            super(FACTORY, DEFAULT_FORMATTER, filename, new SizeRotationPolicy(1));
        }

        @Override
        protected PrintWriter openWriter(Path filePath) throws IOException {
            if (Files.exists(filePath)) {
                return super.openWriter(filePath);
            }
            throw new IOException("forced post-rotation re-open failure");
        }
    }

    private static class InterruptingWriteFileMessageHandler extends FileMessageHandler {
        private InterruptingWriteFileMessageHandler(String filename) {
            super(FACTORY, DEFAULT_FORMATTER, filename, true, 4096, false);
        }

        @Override
        protected PrintWriter openWriter(Path filePath) throws IOException {
            return new PrintWriter(new BufferedWriter(new FileWriter(filePath.toFile(), true))) {
                private boolean interrupted;

                @Override
                public void println(String x) {
                    super.println(x);
                    if (!interrupted) {
                        interrupted = true;
                        Thread.currentThread().interrupt();
                    }
                }
            };
        }
    }

    @Test
    @DisplayName("Should reject null or empty path")
    void shouldRejectNullOrEmptyPath() {
        assertThrows(NullPointerException.class, () -> new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, null));
        assertThrows(IllegalArgumentException.class, () -> new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, " "));
    }

    @Test
    @DisplayName("Should reject directory path")
    void shouldRejectDirectoryPath() throws IOException {
        Path dir = Files.createTempDirectory("fmh-dir");
        assertThrows(IllegalArgumentException.class, () -> new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, dir.toString()));
    }

    @Test
    @DisplayName("Should reject non-writable file")
    void shouldRejectNonWritableFile() throws IOException {
        Path file = Files.createTempFile("fmh-ro", ".log");
        file.toFile().setWritable(false);
        try {
            assertThrows(IllegalArgumentException.class, () -> new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, file.toString()));
        } finally {
            file.toFile().setWritable(true);
        }
    }

    @Test
    @DisplayName("Should write messages to file and close cleanly")
    void shouldWriteMessagesToFile() throws Exception {
        Path file = Files.createTempFile("fmh", ".log");
        Files.deleteIfExists(file);
        try (FileMessageHandler handler = new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, file.toString())) {
            handler.info("info");
            handler.warn("warn");
            handler.error("error");
            handler.fatal("fatal");
            handler.debug("debug");
            handler.trace("trace");
            handler.exception(new RuntimeException("boom"));
            handler.exception("prefix", new RuntimeException("kaboom"));
        }
        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.stream().anyMatch(l -> l.contains("INFO ") && l.endsWith("info")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("WARN ") && l.endsWith("warn")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("ERROR") && l.endsWith("error")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("FATAL") && l.endsWith("fatal")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("DEBUG") && l.endsWith("debug")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("TRACE") && l.endsWith("trace")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("boom")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("prefix")));
    }

    @Test
    @DisplayName("Async with shutdown hook should register hook and close cleanly")
    void asyncWithShutdownHookRegistersHook() throws Exception {
        Path file = Files.createTempFile("fmh", ".log");
        Files.deleteIfExists(file);
        FileMessageHandler handler = new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, file.toString(), true, 10, true);
        try {
            handler.info("hello");
        } finally {
            handler.close();
        }
    }

    @Test
    @DisplayName("Async with full queue should fallback to synchronous execution")
    void asyncQueueFullFallsBackToSync() throws Exception {
        Path file = Files.createTempFile("fmh", ".log");
        Files.deleteIfExists(file);
        try (FileMessageHandler handler = new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, file.toString(), true, 1)) {
            handler.info(() -> {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                }
                return "background";
            });
            handler.info("sync");
            Thread.sleep(300); // allow background to finish
        }
        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.stream().anyMatch(l -> l.contains("sync")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("background")));
    }

    @Test
    @DisplayName("Async with non-positive capacity should behave as unbounded")
    void asyncWithNonPositiveCapacityBehavesUnbounded() throws Exception {
        Path file = Files.createTempFile("fmh", ".log");
        Files.deleteIfExists(file);
        try (FileMessageHandler handler = new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, file.toString(), true, 0)) {
            handler.info("msg1");
            handler.info("msg2");
            Thread.sleep(100);
        }
        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.stream().anyMatch(l -> l.contains("msg1")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("msg2")));
    }

    @Test
    @DisplayName("Overflow should execute synchronously (dispatch fallback)")
    void overflowShouldExecuteSynchronously() throws Exception {
        Path file = Files.createTempFile("fmh", ".log");
        Files.deleteIfExists(file);
        try (FileMessageHandler handler = new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, file.toString(), true, 1)) {
            // Fill the queue with a blocking task
            handler.info(() -> {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                }
                return "blocking";
            });
            // This should run synchronously due to full queue
            handler.info("sync-fallback");
            Thread.sleep(250);
        }
        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.stream().anyMatch(l -> l.contains("sync-fallback")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("blocking")));
    }

    @Test
    @DisplayName("Drain loop should handle high volume without losing messages")
    void drainLoopHighVolume() throws Exception {
        Path file = Files.createTempFile("fmh", ".log");
        Files.deleteIfExists(file);
        int count = 200;
        try (FileMessageHandler handler = new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, file.toString(), true, 50)) {
            for (int i = 0; i < count; i++) {
                int idx = i;
                handler.info(() -> "msg-" + idx);
            }
            Thread.sleep(500);
        }
        List<String> lines = Files.readAllLines(file);
        assertEquals(count, lines.stream().filter(l -> l.contains("INFO ")).count());
    }

    @Test
    @DisplayName("Should be safe to close multiple times")
    void shouldAllowIdempotentClose() throws Exception {
        Path file = Files.createTempFile("fmh", ".log");
        Files.deleteIfExists(file);
        FileMessageHandler handler = new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, file.toString());
        handler.info("once");
        handler.close();
        handler.close(); // should not throw
        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.stream().anyMatch(l -> l.contains("once")));
    }

    @Test
    @DisplayName("Should reject when parent directory is not writable")
    void shouldRejectNonWritableDirectory() throws Exception {
        Path dir = Files.createTempDirectory("fmh-nw");
        dir.toFile().setWritable(false);
        Path file = dir.resolve("log.log");
        try {
            assertThrows(IllegalArgumentException.class, () -> new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, file.toString()));
        } finally {
            dir.toFile().setWritable(true);
        }
    }

    @Test
    @DisplayName("Should throw if writer cannot be opened")
    void shouldThrowIfWriterCannotBeOpened() throws Exception {
        Path file = Files.createTempFile("fmh-open-fail", ".log");
        assertThrows(IllegalArgumentException.class, () -> new FailingOpenWriterFileMessageHandler(file.toString()));
    }

    @Test
    @DisplayName("Close should keep interrupt flag when awaitTermination is interrupted")
    void closeShouldKeepInterruptFlagWhenAwaitTerminationIsInterrupted() throws Exception {
        Path file = Files.createTempFile("fmh-await", ".log");
        ThrowingAwaitTerminationFileMessageHandler handler = new ThrowingAwaitTerminationFileMessageHandler(file.toString());
        assertFalse(Thread.currentThread().isInterrupted());
        try {
            handler.close();
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("Close should ignore IllegalStateException when removing shutdown hook")
    void closeShouldIgnoreIllegalStateExceptionWhenRemovingShutdownHook() throws Exception {
        Path file = Files.createTempFile("fmh-hook", ".log");
        ThrowingRemoveHookFileMessageHandler handler = new ThrowingRemoveHookFileMessageHandler(file.toString());
        assertDoesNotThrow(handler::close);
    }

    @Test
    @DisplayName("Close should drain pending queued messages")
    void closeShouldDrainPendingQueuedMessages() throws Exception {
        Path file = Files.createTempFile("fmh-drain", ".log");
        Files.deleteIfExists(file);
        int count = 500;
        FileMessageHandler handler = new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, file.toString(), true, 200);
        for (int i = 0; i < count; i++) {
            handler.info("queued-" + i);
        }
        handler.close();

        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.stream().anyMatch(l -> l.contains("queued-0")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("queued-" + (count - 1))));
    }

    @Test
    @DisplayName("Should support relative filename without parent directory")
    void shouldSupportRelativeFilenameWithoutParentDirectory() throws Exception {
        String fileName = "fmh-relative-" + System.nanoTime() + ".log";
        Path file = Paths.get(fileName);
        Files.deleteIfExists(file);
        try (FileMessageHandler handler = new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, fileName)) {
            handler.info("relative");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("Should print to System.err when writer reports a write error")
    void shouldPrintToSystemErrWhenWriterReportsError() throws Exception {
        Path file = Files.createTempFile("fmh-check-error", ".log");
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent, true, StandardCharsets.UTF_8.name()));
        try (FileMessageHandler handler = new ErrorCheckWriterFileMessageHandler(file.toString())) {
            handler.info("trigger-error-check");
        } finally {
            System.setErr(originalErr);
        }
        String errOutput = errContent.toString(StandardCharsets.UTF_8.name());
        assertFalse(errOutput.isEmpty(), "System.err should contain a write-error notification");
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    @DisplayName("Should keep all info messages under high concurrent load")
    void shouldKeepAllInfoMessagesUnderHighConcurrentLoad() throws Exception {
        final int threadCount = 16;
        final int messagesPerThread = 1500;
        final int expectedMessages = threadCount * messagesPerThread;

        Path file = Files.createTempFile("fmh-stress", ".log");
        Files.deleteIfExists(file);

        try (FileMessageHandler handler = new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, file.toString(), true, 2048, false)) {
            ExecutorService producerPool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                futures.add(producerPool.submit(() -> {
                    start.await();
                    for (int i = 0; i < messagesPerThread; i++) {
                        final int messageIndex = i;
                        handler.info(() -> "stress-file-" + threadId + "-" + messageIndex);
                    }
                    return null;
                }));
            }

            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
            producerPool.shutdown();
            assertTrue(producerPool.awaitTermination(10, TimeUnit.SECONDS));
        }

        long infoLines;
        try (Stream<String> lines = Files.lines(file)) {
            infoLines = lines.filter(line -> line.contains("[INFO ]")).count();
        }

        assertEquals(expectedMessages, infoLines, "Some file info messages were lost under stress");
    }

    @Test
    @DisplayName("Flush should not throw in sync mode and content should be readable after close")
    void flushShouldNotThrowInSyncMode() throws Exception {
        Path file = Files.createTempFile("fmh-flush-sync", ".log");
        Files.deleteIfExists(file);
        try (FileMessageHandler handler = new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, file.toString())) {
            handler.info("before-flush");
            handler.flush();
        }
        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.stream().anyMatch(l -> l.contains("before-flush")));
    }

    @Test
    @DisplayName("Flush should not throw in async mode and content should be readable after close")
    void flushShouldNotThrowInAsyncMode() throws Exception {
        Path file = Files.createTempFile("fmh-flush-async", ".log");
        Files.deleteIfExists(file);
        try (FileMessageHandler handler = new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, file.toString(), true, 64)) {
            handler.info("before-flush");
            handler.flush();
        }
        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.stream().anyMatch(l -> l.contains("before-flush")));
    }

    // -------------------------------------------------------------------------
    // Rotation and re-open tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Size-based rotation should archive the current file and create a new one")
    void sizeBasedRotationShouldArchiveAndCreateNewFile() throws Exception {
        Path file = Files.createTempFile("fmh-size-rotate", ".log");
        Files.deleteIfExists(file);
        // Threshold low enough that a single formatted line exceeds it
        SizeRotationPolicy policy = new SizeRotationPolicy(1);
        try (FileMessageHandler handler = new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, file.toString(), policy)) {
            handler.info("first");
            handler.info("second");
        }
        // The new (current) log file must exist
        assertTrue(Files.exists(file), "Current log file should exist after rotation");
        // At least one rotated file must exist in the same directory
        Path dir = file.toAbsolutePath().getParent();
        String baseName = file.getFileName().toString();
        try (Stream<Path> candidates = Files.list(dir)) {
            long rotatedCount = candidates
                    .filter(p -> p.getFileName().toString().startsWith(baseName + "."))
                    .count();
            assertTrue(rotatedCount >= 1, "At least one rotated file should exist in the directory");
        }
    }

    @Test
    @DisplayName("Daily rotation should archive the current file using the open date")
    void dailyRotationShouldArchiveCurrentFileUsingOpenDate() throws Exception {
        Path file = Files.createTempFile("fmh-daily-rotate", ".log");
        Files.deleteIfExists(file);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        DailyRotationPolicy policy = new DailyRotationPolicy(yesterday);
        try (FileMessageHandler handler = new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, file.toString(), policy)) {
            handler.info("trigger rotation");
        }
        // After close the current log file is recreated (rotation happened on write)
        // The archived file should be named with yesterday's date
        Path dir = file.toAbsolutePath().getParent();
        String baseName = file.getFileName().toString(); // e.g. fmh-daily-rotate12345678.log
        int dotIndex = baseName.lastIndexOf('.');
        String expectedArchiveName;
        if (dotIndex > 0) {
            expectedArchiveName = baseName.substring(0, dotIndex) + "." + yesterday + baseName.substring(dotIndex);
        } else {
            expectedArchiveName = baseName + "." + yesterday;
        }
        assertTrue(Files.exists(dir.resolve(expectedArchiveName)),
                "Archived file should exist at: " + expectedArchiveName);
    }

    @Test
    @DisplayName("Re-open on external rotation should recreate the file and continue writing")
    void reopenOnExternalRotationShouldRecreateFileAndContinueWriting() throws Exception {
        Path file = Files.createTempFile("fmh-reopen", ".log");
        Files.deleteIfExists(file);
        try (FileMessageHandler handler = new FileMessageHandler(
                FACTORY, DEFAULT_FORMATTER, file.toString(), false, 0, false, null, true)) {
            handler.info("before-delete");
            // Simulate external rotation: delete the file
            Files.deleteIfExists(file);
            handler.info("after-delete");
        }
        assertTrue(Files.exists(file), "Log file should have been re-created after deletion");
        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.stream().anyMatch(l -> l.contains("after-delete")),
                "Message written after re-open should appear in the file");
    }

    @Test
    @DisplayName("Re-open failure should print to System.err and not throw")
    void reopenFailureShouldPrintToSystemErrAndNotThrow() throws Exception {
        Path file = Files.createTempFile("fmh-reopen-fail", ".log");
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent, true, StandardCharsets.UTF_8.name()));
        try (FileMessageHandler handler = new FailingReopenFileMessageHandler(file.toString())) {
            handler.info("before-delete");
            Files.deleteIfExists(file);
            handler.info("after-delete-with-failing-reopen");
        } finally {
            System.setErr(originalErr);
        }
        String errOutput = errContent.toString(StandardCharsets.UTF_8.name());
        assertFalse(errOutput.isEmpty(), "System.err should contain a re-open error notification");
    }

    @Test
    @DisplayName("Without re-open flag, deleted file is not recreated by handler")
    void withoutReopenFlagDeletedFileIsNotRecreatedByHandler() throws Exception {
        Path file = Files.createTempFile("fmh-no-reopen", ".log");
        Files.deleteIfExists(file);
        try (FileMessageHandler handler = new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, file.toString())) {
            handler.info("before-delete");
            Files.deleteIfExists(file);
            // This write goes to the old (now-deleted) file descriptor — no re-open
            handler.info("after-delete");
        }
        // File should NOT exist because no re-open occurred and close() flushes to the old fd
        assertFalse(Files.exists(file), "File should not have been re-created without the re-open flag");
    }

    @Test
    @DisplayName("Rotation constructors: sync with RotationPolicy")
    void rotationConstructorSyncWithPolicy() throws Exception {
        Path file = Files.createTempFile("fmh-ctor-sync-policy", ".log");
        Files.deleteIfExists(file);
        try (FileMessageHandler handler = new FileMessageHandler(
                FACTORY, DEFAULT_FORMATTER, file.toString(), new SizeRotationPolicy(Long.MAX_VALUE))) {
            handler.info("ok");
        }
        assertTrue(Files.readAllLines(file).stream().anyMatch(l -> l.contains("ok")));
    }

    @Test
    @DisplayName("Rotation constructors: async with RotationPolicy")
    void rotationConstructorAsyncWithPolicy() throws Exception {
        Path file = Files.createTempFile("fmh-ctor-async-policy", ".log");
        Files.deleteIfExists(file);
        try (FileMessageHandler handler = new FileMessageHandler(
                FACTORY, DEFAULT_FORMATTER, file.toString(), true, 64, new SizeRotationPolicy(Long.MAX_VALUE))) {
            handler.info("ok");
        }
        assertTrue(Files.readAllLines(file).stream().anyMatch(l -> l.contains("ok")));
    }

    // -------------------------------------------------------------------------
    // Error consumer and closeOnWriteError tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("setErrorConsumer should accept a valid consumer")
    void setErrorConsumerAcceptsValidConsumer() throws Exception {
        Path file = Files.createTempFile("fmh-set-consumer", ".log");
        List<String> errors = new ArrayList<>();
        try (FileMessageHandler handler = new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, file.toString())) {
            handler.setErrorConsumer(errors::add);
            handler.info("ok");
        }
        assertTrue(errors.isEmpty(), "No error should have been reported for a successful write");
    }

    @Test
    @DisplayName("setErrorConsumer should throw NullPointerException for null argument")
    void setErrorConsumerNullThrowsNpe() throws Exception {
        Path file = Files.createTempFile("fmh-null-consumer", ".log");
        try (FileMessageHandler handler = new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, file.toString())) {
            assertThrows(NullPointerException.class, () -> handler.setErrorConsumer(null));
        }
    }

    @Test
    @DisplayName("checkWriteError: IOException during recovery should still notify errorConsumer")
    void checkWriteErrorRecoveryFailsNotifiesConsumer() throws Exception {
        Path file = Files.createTempFile("fmh-recovery-fail", ".log");
        List<String> errors = new ArrayList<>();
        try (FileMessageHandler handler = new FailingRecoveryFileMessageHandler(file.toString())) {
            handler.setErrorConsumer(errors::add);
            handler.info("trigger");
        }
        assertFalse(errors.isEmpty(), "errorConsumer should be notified when write-error recovery fails");
    }

    @Test
    @DisplayName("checkWriteError: closeOnWriteError=true in sync mode should close the handler")
    void checkWriteErrorCloseOnWriteErrorSyncClosesHandler() throws Exception {
        Path file = Files.createTempFile("fmh-close-on-error-sync", ".log");
        List<String> errors = new ArrayList<>();
        FileMessageHandler handler = new ErrorCheckWriterFileMessageHandler(file.toString());
        handler.setErrorConsumer(errors::add);
        handler.setCloseOnWriteError(true);
        handler.info("trigger");
        assertFalse(errors.isEmpty(), "errorConsumer should be notified on write error");
        assertThrows(IllegalStateException.class, () -> handler.info("after-close"),
                "Handler should be closed after write error with closeOnWriteError=true in sync mode");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("checkWriteError: closeOnWriteError=true in async mode should close the handler on a new thread")
    void checkWriteErrorCloseOnWriteErrorAsyncClosesHandler() throws Exception {
        Path file = Files.createTempFile("fmh-close-on-error-async", ".log");
        List<String> errors = new ArrayList<>();
        FileMessageHandler handler = new AsyncErrorFileMessageHandler(file.toString());
        handler.setErrorConsumer(errors::add);
        handler.setCloseOnWriteError(true);
        handler.info("trigger");
        // Poll until the worker thread has processed the write and the close thread has run
        long deadline = System.currentTimeMillis() + 5000;
        while (errors.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertFalse(errors.isEmpty(), "errorConsumer should be notified on write error in async mode");
        // Give the close thread time to finish
        Thread.sleep(500);
        assertThrows(IllegalStateException.class, () -> handler.info("after-close"),
                "Handler should be closed after write error with closeOnWriteError=true in async mode");
    }

    @Test
    @DisplayName("rotateIfNeeded: IOException when re-opening after rotation should notify errorConsumer")
    void rotateIfNeededFailureNotifiesErrorConsumer() throws Exception {
        Path file = Files.createTempFile("fmh-rotate-reopen-fail", ".log");
        List<String> errors = new ArrayList<>();
        try (FailingRotationFileMessageHandler handler = new FailingRotationFileMessageHandler(file.toString())) {
            handler.setErrorConsumer(errors::add);
            handler.info("trigger");
        }
        assertFalse(errors.isEmpty(), "errorConsumer should receive rotation-failure notification");
    }

    @Test
    @DisplayName("RawJsonRecordFormatter should produce NDJSON output in the log file")
    void jsonFormatterShouldProduceNdjsonOutput() throws Exception {
        Path file = Files.createTempFile("fmh-json", ".log");
        Files.deleteIfExists(file);
        try (FileMessageHandler handler = new FileMessageHandler(FACTORY, DEFAULT_FORMATTER, file.toString())) {
            handler.setLogRecordFormatter(new RawJsonRecordFormatter());
            handler.info("structured message");
        }
        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("{") && l.contains("\"severityText\":\"INFO\"")),
                "Log file should contain a JSON line with severity INFO");
        assertTrue(lines.stream().anyMatch(l -> l.contains("\"stringValue\":\"structured message\"")),
                "Log file should contain the message in JSON format");
    }

    @Test
    @DisplayName("Should drain queued writes when worker is interrupted mid-write")
    void shouldDrainQueuedWritesWhenWorkerIsInterruptedMidWrite() throws Exception {
        Path file = Files.createTempFile("fmh-interrupt-mid-write", ".log");
        Files.deleteIfExists(file);
        final int messages = 1200;

        try (FileMessageHandler handler = new InterruptingWriteFileMessageHandler(file.toString())) {
            for (int i = 0; i < messages; i++) {
                handler.info("mid-write-" + i);
            }
        }

        long infoLines;
        try (Stream<String> lines = Files.lines(file)) {
            infoLines = lines.filter(line -> line.contains("[INFO ]") && line.contains("mid-write-")).count();
        }

        assertEquals(messages, infoLines, "Queued writes were not drained after worker interruption");
    }
}
