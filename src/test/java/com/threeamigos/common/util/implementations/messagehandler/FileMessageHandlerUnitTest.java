package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileMessageHandler unit tests")
@Tag("unit")
@Tag("messageHandler")
class FileMessageHandlerUnitTest {

    private static class FailingOpenWriterFileMessageHandler extends FileMessageHandler {
        private FailingOpenWriterFileMessageHandler(String filename) {
            super(filename);
        }

        @Override
        protected PrintWriter openWriter(Path filePath) throws IOException {
            throw new IOException("forced");
        }
    }

    private static class ThrowingAwaitTerminationFileMessageHandler extends FileMessageHandler {
        private ThrowingAwaitTerminationFileMessageHandler(String filename) {
            super(filename, true, 1, false);
        }

        @Override
        protected void awaitTermination() throws InterruptedException {
            throw new InterruptedException("forced");
        }
    }

    private static class ThrowingRemoveHookFileMessageHandler extends FileMessageHandler {
        private ThrowingRemoveHookFileMessageHandler(String filename) {
            super(filename, true, 1, true);
        }

        @Override
        protected void removeShutdownHook(Thread hook) {
            throw new IllegalStateException("forced");
        }
    }

    private static class ErrorCheckWriterFileMessageHandler extends FileMessageHandler {
        private ErrorCheckWriterFileMessageHandler(String filename) {
            super(filename);
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

    private static class InterruptingWriteFileMessageHandler extends FileMessageHandler {
        private InterruptingWriteFileMessageHandler(String filename) {
            super(filename, true, 4096, false);
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
        assertThrows(NullPointerException.class, () -> new FileMessageHandler(null));
        assertThrows(IllegalArgumentException.class, () -> new FileMessageHandler(" "));
    }

    @Test
    @DisplayName("Should reject directory path")
    void shouldRejectDirectoryPath() throws IOException {
        Path dir = Files.createTempDirectory("fmh-dir");
        assertThrows(IllegalArgumentException.class, () -> new FileMessageHandler(dir.toString()));
    }

    @Test
    @DisplayName("Should reject non-writable file")
    void shouldRejectNonWritableFile() throws IOException {
        Path file = Files.createTempFile("fmh-ro", ".log");
        file.toFile().setWritable(false);
        try {
            assertThrows(IllegalArgumentException.class, () -> new FileMessageHandler(file.toString()));
        } finally {
            file.toFile().setWritable(true);
        }
    }

    @Test
    @DisplayName("Should write messages to file and close cleanly")
    void shouldWriteMessagesToFile() throws Exception {
        Path file = Files.createTempFile("fmh", ".log");
        Files.deleteIfExists(file);
        try (FileMessageHandler handler = new FileMessageHandler(file.toString())) {
            handler.handleInfoMessage("info");
            handler.handleWarnMessage("warn");
            handler.handleErrorMessage("error");
            handler.handleDebugMessage("debug");
            handler.handleTraceMessage("trace");
            handler.handleException(new RuntimeException("boom"));
            handler.handleException("prefix", new RuntimeException("kaboom"));
        }
        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.stream().anyMatch(l -> l.contains("INFO ") && l.endsWith("info")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("WARN ") && l.endsWith("warn")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("ERROR") && l.endsWith("error")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("DEBUG") && l.endsWith("debug")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("TRACE") && l.endsWith("trace")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("EXCEP") && l.contains("boom")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("EXCEP") && l.contains("prefix: kaboom")));
    }

    @Test
    @DisplayName("Async with shutdown hook should register hook and close cleanly")
    void asyncWithShutdownHookRegistersHook() throws Exception {
        Path file = Files.createTempFile("fmh", ".log");
        Files.deleteIfExists(file);
        FileMessageHandler handler = new FileMessageHandler(file.toString(), true, 10, true);
        try {
            handler.handleInfoMessage("hello");
        } finally {
            handler.close();
        }
    }

    @Test
    @DisplayName("Async with full queue should fallback to synchronous execution")
    void asyncQueueFullFallsBackToSync() throws Exception {
        Path file = Files.createTempFile("fmh", ".log");
        Files.deleteIfExists(file);
        try (FileMessageHandler handler = new FileMessageHandler(file.toString(), true, 1)) {
            handler.handleInfoMessage(() -> {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                }
                return "background";
            });
            handler.handleInfoMessage("sync");
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
        try (FileMessageHandler handler = new FileMessageHandler(file.toString(), true, 0)) {
            handler.handleInfoMessage("msg1");
            handler.handleInfoMessage("msg2");
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
        try (FileMessageHandler handler = new FileMessageHandler(file.toString(), true, 1)) {
            // Fill the queue with a blocking task
            handler.handleInfoMessage(() -> {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                }
                return "blocking";
            });
            // This should run synchronously due to full queue
            handler.handleInfoMessage("sync-fallback");
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
        try (FileMessageHandler handler = new FileMessageHandler(file.toString(), true, 50)) {
            for (int i = 0; i < count; i++) {
                int idx = i;
                handler.handleInfoMessage(() -> "msg-" + idx);
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
        FileMessageHandler handler = new FileMessageHandler(file.toString());
        handler.handleInfoMessage("once");
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
            assertThrows(IllegalArgumentException.class, () -> new FileMessageHandler(file.toString()));
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
        FileMessageHandler handler = new FileMessageHandler(file.toString(), true, 200);
        for (int i = 0; i < count; i++) {
            handler.handleInfoMessage("queued-" + i);
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
        try (FileMessageHandler handler = new FileMessageHandler(fileName)) {
            handler.handleInfoMessage("relative");
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
            handler.handleInfoMessage("trigger-error-check");
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

        try (FileMessageHandler handler = new FileMessageHandler(file.toString(), true, 2048, false)) {
            ExecutorService producerPool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                futures.add(producerPool.submit(() -> {
                    start.await();
                    for (int i = 0; i < messagesPerThread; i++) {
                        final int messageIndex = i;
                        handler.handleInfoMessage(() -> "stress-file-" + threadId + "-" + messageIndex);
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
        try (FileMessageHandler handler = new FileMessageHandler(file.toString())) {
            handler.handleInfoMessage("before-flush");
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
        try (FileMessageHandler handler = new FileMessageHandler(file.toString(), true, 64)) {
            handler.handleInfoMessage("before-flush");
            handler.flush();
        }
        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.stream().anyMatch(l -> l.contains("before-flush")));
    }

    @Test
    @DisplayName("Should drain queued writes when worker is interrupted mid-write")
    void shouldDrainQueuedWritesWhenWorkerIsInterruptedMidWrite() throws Exception {
        Path file = Files.createTempFile("fmh-interrupt-mid-write", ".log");
        Files.deleteIfExists(file);
        final int messages = 1200;

        try (FileMessageHandler handler = new InterruptingWriteFileMessageHandler(file.toString())) {
            for (int i = 0; i < messages; i++) {
                handler.handleInfoMessage("mid-write-" + i);
            }
        }

        long infoLines;
        try (Stream<String> lines = Files.lines(file)) {
            infoLines = lines.filter(line -> line.contains("[INFO ]") && line.contains("mid-write-")).count();
        }

        assertEquals(messages, infoLines, "Queued writes were not drained after worker interruption");
    }
}
