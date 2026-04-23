package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("InMemoryMessageHandler unit test")
@Tag("unit")
@Tag("messageHandler")
class InMemoryMessageHandlerUnitTest {

    private static final String FIRST_MESSAGE = "First message";
    private static final String SECOND_MESSAGE = "Second message";

    @Test
    @DisplayName("Should throw an exception if a null info message is provided")
    void shouldThrowAnExceptionIfANullInfoMessageIsProvided() {
        // Given
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        // When
        String infoMessage = null;
        // Then
        assertThrows(NullPointerException.class, () -> sut.info(infoMessage));
    }

    @Test
    @DisplayName("Should store all info messages")
    void shouldStoreAllInfoMessages() {
        //Given
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        //When
        sut.info(FIRST_MESSAGE);
        sut.info(SECOND_MESSAGE);
        //Then
        assertEquals(2, sut.getAllMessages().size(), "Wrong all messages size");
        assertEquals(2, sut.getAllInfoMessages().size(), "Wrong info messages size");
        assertEquals(0, sut.getAllWarnMessages().size(), "Wrong warn messages size");
        assertEquals(0, sut.getAllErrorMessages().size(), "Wrong error messages size");
        assertEquals(0, sut.getAllFatalMessages().size(), "Wrong fatal messages size");
        assertEquals(0, sut.getAllDebugMessages().size(), "Wrong debug messages size");
        assertEquals(0, sut.getAllTraceMessages().size(), "Wrong trace messages size");
        assertEquals(0, sut.getAllExceptionMessages().size(), "Wrong exception messages size");
        assertEquals(0, sut.getAllThrowables().size(), "Wrong exceptions size");
        assertEquals(SECOND_MESSAGE, sut.getLastMessage(), "Wrong last message");
    }

    @Test
    @DisplayName("Should throw an exception if a null warn message is provided")
    void shouldThrowAnExceptionIfANullWarnMessageIsProvided() {
        // Given
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        // When
        String warnMessage = null;
        // Then
        assertThrows(NullPointerException.class, () -> sut.warn(warnMessage));
    }

    @Test
    @DisplayName("Should store all warn messages")
    void shouldStoreAllWarnMessages() {
        //Given
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        //When
        sut.warn(FIRST_MESSAGE);
        sut.warn(SECOND_MESSAGE);
        //Then
        assertEquals(2, sut.getAllMessages().size(), "Wrong all messages size");
        assertEquals(0, sut.getAllInfoMessages().size(), "Wrong info messages size");
        assertEquals(2, sut.getAllWarnMessages().size(), "Wrong warn messages size");
        assertEquals(0, sut.getAllErrorMessages().size(), "Wrong error messages size");
        assertEquals(0, sut.getAllFatalMessages().size(), "Wrong fatal messages size");
        assertEquals(0, sut.getAllDebugMessages().size(), "Wrong debug messages size");
        assertEquals(0, sut.getAllTraceMessages().size(), "Wrong trace messages size");
        assertEquals(0, sut.getAllExceptionMessages().size(), "Wrong exception messages size");
        assertEquals(0, sut.getAllThrowables().size(), "Wrong exceptions size");
        assertEquals(SECOND_MESSAGE, sut.getLastMessage(), "Wrong last message");
    }

    @Test
    @DisplayName("Should throw an exception if a null error message is provided")
    void shouldThrowAnExceptionIfANullErrorMessageIsProvided() {
        // Given
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        // When
        String errorMessage = null;
        // Then
        assertThrows(NullPointerException.class, () -> sut.error(errorMessage));
    }

    @Test
    @DisplayName("Should store all error messages")
    void shouldStoreAllErrorMessages() {
        //Given
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        //When
        sut.error(FIRST_MESSAGE);
        sut.error(SECOND_MESSAGE);
        //Then
        assertEquals(2, sut.getAllMessages().size(), "Wrong all messages size");
        assertEquals(0, sut.getAllInfoMessages().size(), "Wrong info messages size");
        assertEquals(0, sut.getAllWarnMessages().size(), "Wrong warn messages size");
        assertEquals(2, sut.getAllErrorMessages().size(), "Wrong error messages size");
        assertEquals(0, sut.getAllFatalMessages().size(), "Wrong fatal messages size");
        assertEquals(0, sut.getAllDebugMessages().size(), "Wrong debug messages size");
        assertEquals(0, sut.getAllTraceMessages().size(), "Wrong trace messages size");
        assertEquals(0, sut.getAllExceptionMessages().size(), "Wrong exception messages size");
        assertEquals(0, sut.getAllThrowables().size(), "Wrong exceptions size");
        assertEquals(SECOND_MESSAGE, sut.getLastMessage(), "Wrong last message");
    }

    @Test
    @DisplayName("Should throw an exception if a null fatal message is provided")
    void shouldThrowAnExceptionIfANullFatalMessageIsProvided() {
        // Given
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        // When
        String fatalMessage = null;
        // Then
        assertThrows(NullPointerException.class, () -> sut.fatal(fatalMessage));
    }

    @Test
    @DisplayName("Should store all fatal messages")
    void shouldStoreAllFatalMessages() {
        //Given
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        //When
        sut.fatal(FIRST_MESSAGE);
        sut.fatal(SECOND_MESSAGE);
        //Then
        assertEquals(2, sut.getAllMessages().size(), "Wrong all messages size");
        assertEquals(0, sut.getAllInfoMessages().size(), "Wrong info messages size");
        assertEquals(0, sut.getAllWarnMessages().size(), "Wrong warn messages size");
        assertEquals(0, sut.getAllErrorMessages().size(), "Wrong error messages size");
        assertEquals(2, sut.getAllFatalMessages().size(), "Wrong fatal messages size");
        assertEquals(0, sut.getAllDebugMessages().size(), "Wrong debug messages size");
        assertEquals(0, sut.getAllTraceMessages().size(), "Wrong trace messages size");
        assertEquals(0, sut.getAllExceptionMessages().size(), "Wrong exception messages size");
        assertEquals(0, sut.getAllThrowables().size(), "Wrong exceptions size");
        assertEquals(SECOND_MESSAGE, sut.getLastMessage(), "Wrong last message");
    }

    @Test
    @DisplayName("Should throw an exception if a null debug message is provided")
    void shouldThrowAnExceptionIfANullDebugMessageIsProvided() {
        // Given
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        sut.setDebugEnabled(true);
        // When
        String debugMessage = null;
        // Then
        assertThrows(NullPointerException.class, () -> sut.debug(debugMessage));
    }

    @Test
    @DisplayName("Should store all debug messages")
    void shouldStoreAllDebugMessages() {
        //Given
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        sut.setDebugEnabled(true);
        //When
        sut.debug(FIRST_MESSAGE);
        sut.debug(SECOND_MESSAGE);
        //Then
        assertEquals(2, sut.getAllMessages().size(), "Wrong all messages size");
        assertEquals(0, sut.getAllInfoMessages().size(), "Wrong info messages size");
        assertEquals(0, sut.getAllWarnMessages().size(), "Wrong warn messages size");
        assertEquals(0, sut.getAllErrorMessages().size(), "Wrong error messages size");
        assertEquals(0, sut.getAllFatalMessages().size(), "Wrong fatal messages size");
        assertEquals(2, sut.getAllDebugMessages().size(), "Wrong debug messages size");
        assertEquals(0, sut.getAllTraceMessages().size(), "Wrong trace messages size");
        assertEquals(0, sut.getAllExceptionMessages().size(), "Wrong exception messages size");
        assertEquals(0, sut.getAllThrowables().size(), "Wrong exceptions size");
        assertEquals(SECOND_MESSAGE, sut.getLastMessage(), "Wrong last message");
    }

    @Test
    @DisplayName("Should throw an exception if a null trace message is provided")
    void shouldThrowAnExceptionIfANullTraceMessageIsProvided() {
        // Given
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        sut.setTraceEnabled(true);
        // When
        String traceMessage = null;
        // Then
        assertThrows(NullPointerException.class, () -> sut.trace(traceMessage));
    }

    @Test
    @DisplayName("Should store all trace messages")
    void shouldStoreAllTraceMessages() {
        //Given
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        sut.setTraceEnabled(true);
        //When
        sut.trace(FIRST_MESSAGE);
        sut.trace(SECOND_MESSAGE);
        //Then
        assertEquals(2, sut.getAllMessages().size(), "Wrong all messages size");
        assertEquals(0, sut.getAllInfoMessages().size(), "Wrong info messages size");
        assertEquals(0, sut.getAllWarnMessages().size(), "Wrong warn messages size");
        assertEquals(0, sut.getAllErrorMessages().size(), "Wrong error messages size");
        assertEquals(0, sut.getAllFatalMessages().size(), "Wrong fatal messages size");
        assertEquals(0, sut.getAllDebugMessages().size(), "Wrong debug messages size");
        assertEquals(2, sut.getAllTraceMessages().size(), "Wrong trace messages size");
        assertEquals(0, sut.getAllExceptionMessages().size(), "Wrong exception messages size");
        assertEquals(0, sut.getAllThrowables().size(), "Wrong exceptions size");
        assertEquals(SECOND_MESSAGE, sut.getLastMessage(), "Wrong last message");
    }

    @Test
    @DisplayName("Should throw an exception if a null exception is provided")
    void shouldThrowAnExceptionIfANullExceptionIsProvided() {
        // Given
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        // When
        Exception exception = null;
        // Then
        assertThrows(NullPointerException.class, () -> sut.exception(exception));
    }

    @Test
    @DisplayName("Should store all exception messages")
    void shouldStoreAllExceptionMessages() {
        // Given
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        Exception firstException = new IllegalArgumentException("My IllegalArgumentException");
        Exception secondException = new ClassNotFoundException("My ClassNotFoundException");
        // When
        sut.exception(firstException);
        sut.exception(secondException);
        // Then
        assertEquals(2, sut.getAllMessages().size(), "Wrong all messages size");
        assertEquals(0, sut.getAllInfoMessages().size(), "Wrong info messages size");
        assertEquals(0, sut.getAllWarnMessages().size(), "Wrong warn messages size");
        assertEquals(0, sut.getAllErrorMessages().size(), "Wrong error messages size");
        assertEquals(0, sut.getAllFatalMessages().size(), "Wrong fatal messages size");
        assertEquals(0, sut.getAllDebugMessages().size(), "Wrong debug messages size");
        assertEquals(0, sut.getAllTraceMessages().size(), "Wrong trace messages size");
        assertEquals(2, sut.getAllExceptionMessages().size(), "Wrong exception messages size");
        assertEquals(2, sut.getAllThrowables().size(), "Wrong exceptions size");
        assertEquals("My ClassNotFoundException", sut.getLastMessage(), "Wrong last message");
    }

    @Test
    @DisplayName("Should handle null exception message by using exception.toString()")
    void shouldHandleNullExceptionMessage() {
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        Exception ex = new Exception((String) null);

        sut.exception(ex);

        assertEquals(1, sut.getAllExceptionMessages().size());
        assertEquals(ex.toString(), sut.getAllExceptionMessages().get(0));
        assertEquals(ex, sut.getAllThrowables().get(0));
    }

    @Test
    @DisplayName("Should evict oldest when maxEntries exceeded")
    void shouldEvictOldestWhenMaxEntriesExceeded() {
        InMemoryMessageHandler handler = new InMemoryMessageHandler(2);

        handler.info("one");
        handler.info("two");
        handler.info("three");

        assertEquals(2, handler.getAllMessages().size());
        assertEquals("two", handler.getAllMessages().get(0));
        assertEquals("three", handler.getAllMessages().get(1));
        assertEquals(2, handler.getMaxEntries());
    }

    @Test
    @DisplayName("Should reject non-positive maxEntries")
    void shouldRejectNonPositiveMaxEntries() {
        assertThrows(IllegalArgumentException.class, () -> new InMemoryMessageHandler(0));
        assertThrows(IllegalArgumentException.class, () -> new InMemoryMessageHandler(-5));
    }

    @Test
    @DisplayName("Supplier should not be evaluated when level disabled")
    void supplierShouldNotBeEvaluatedWhenLevelDisabled() {
        InMemoryMessageHandler sut = new InMemoryMessageHandler();

        sut.setInfoEnabled(false);
        sut.info((java.util.function.Supplier<String>) () -> {
            throw new AssertionError("Supplier should not be called");
        });
        sut.setInfoEnabled(true);

        sut.setWarnEnabled(false);
        sut.warn((java.util.function.Supplier<String>) () -> {
            throw new AssertionError("Supplier should not be called");
        });
        sut.setWarnEnabled(true);

        sut.setErrorEnabled(false);
        sut.error((java.util.function.Supplier<String>) () -> {
            throw new AssertionError("Supplier should not be called");
        });
        sut.setErrorEnabled(true);

        sut.setFatalEnabled(false);
        sut.fatal((java.util.function.Supplier<String>) () -> {
            throw new AssertionError("Supplier should not be called");
        });
        sut.setFatalEnabled(true);

        sut.setDebugEnabled(false);
        sut.debug((java.util.function.Supplier<String>) () -> {
            throw new AssertionError("Supplier should not be called");
        });
        sut.setDebugEnabled(true);

        sut.setTraceEnabled(false);
        sut.trace((java.util.function.Supplier<String>) () -> {
            throw new AssertionError("Supplier should not be called");
        });
        sut.setTraceEnabled(true);

        assertEquals(0, sut.getAllMessages().size());
        assertEquals(0, sut.getAllInfoMessages().size());
        assertEquals(0, sut.getAllWarnMessages().size());
        assertEquals(0, sut.getAllErrorMessages().size());
        assertEquals(0, sut.getAllFatalMessages().size());
        assertEquals(0, sut.getAllDebugMessages().size());
        assertEquals(0, sut.getAllTraceMessages().size());
        assertEquals(0, sut.getAllExceptionMessages().size());
        assertEquals(0, sut.getAllThrowables().size());
    }

    @Test
    @DisplayName("Should store exception with message")
    void shouldStoreExceptionWithMessage() {
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        Exception exception = new RuntimeException("boom");

        sut.exception("prefix", exception);

        assertEquals(1, sut.getAllExceptionMessages().size());
        assertEquals("prefix: boom", sut.getAllExceptionMessages().get(0));
        assertEquals(1, sut.getAllThrowables().size());
        assertEquals(exception, sut.getAllThrowables().get(0));
        assertEquals("prefix: boom", sut.getLastMessage());
    }

    @Test
    @DisplayName("Should store exception with message when exception message is null")
    void shouldStoreExceptionWithMessageWhenExceptionMessageIsNull() {
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        Exception exception = new RuntimeException((String) null);

        sut.exception("prefix", exception);

        assertEquals(1, sut.getAllExceptionMessages().size());
        assertEquals("prefix: " + exception.toString(), sut.getAllExceptionMessages().get(0));
        assertEquals("prefix: " + exception.toString(), sut.getLastMessage());
    }

    @Test
    @DisplayName("Should store throwable detail when message is empty")
    void shouldStoreThrowableDetailWhenMessageIsEmpty() {
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        RuntimeException exception = new RuntimeException("boom");

        sut.handleThrowable("", exception);

        assertEquals(1, sut.getAllExceptionMessages().size());
        assertEquals("boom", sut.getAllExceptionMessages().get(0));
        assertEquals("boom", sut.getLastMessage());
    }

    @Test
    @DisplayName("Should store throwable detail when message is null")
    void shouldStoreThrowableDetailWhenMessageIsNull() {
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        RuntimeException exception = new RuntimeException("boom");

        sut.handleThrowable(null, exception);

        assertEquals(1, sut.getAllExceptionMessages().size());
        assertEquals("boom", sut.getAllExceptionMessages().get(0));
        assertEquals("boom", sut.getLastMessage());
    }

    @Test
    @DisplayName("Snapshot should expose an immutable and internally consistent view")
    void snapshotShouldExposeImmutableAndConsistentView() {
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        sut.setDebugEnabled(true);
        sut.setTraceEnabled(true);

        sut.info("info");
        sut.warn("warn");
        sut.error("error");
        sut.fatal("fatal");
        sut.debug("debug");
        sut.trace("trace");
        sut.exception("prefix", new RuntimeException("boom"));

        InMemoryMessageHandler.Snapshot snapshot = sut.snapshot();

        assertEquals(7, snapshot.getAllMessages().size());
        assertEquals(1, snapshot.getAllInfoMessages().size());
        assertEquals(1, snapshot.getAllWarnMessages().size());
        assertEquals(1, snapshot.getAllErrorMessages().size());
        assertEquals(1, snapshot.getAllFatalMessages().size());
        assertEquals(1, snapshot.getAllDebugMessages().size());
        assertEquals(1, snapshot.getAllTraceMessages().size());
        assertEquals(1, snapshot.getAllExceptionMessages().size());
        assertEquals(1, snapshot.getAllThrowables().size());
        assertEquals("prefix: boom", snapshot.getLastMessage());

        assertThrows(UnsupportedOperationException.class, () -> snapshot.getAllMessages().add("new"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getAllThrowables().add(new RuntimeException("x")));
    }

    @Test
    @DisplayName("Clear should reset all lists and lastMessage to null")
    void clearShouldResetAllListsAndLastMessage() {
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        sut.info("info");
        sut.warn("warn");
        sut.error("error");
        sut.fatal("fatal");
        sut.debug("debug");
        sut.trace("trace");
        sut.exception(new RuntimeException("ex"));
        sut.exception("prefix", new RuntimeException("ex2"));

        sut.clear();

        assertEquals(0, sut.getAllMessages().size());
        assertEquals(0, sut.getAllInfoMessages().size());
        assertEquals(0, sut.getAllWarnMessages().size());
        assertEquals(0, sut.getAllErrorMessages().size());
        assertEquals(0, sut.getAllFatalMessages().size());
        assertEquals(0, sut.getAllDebugMessages().size());
        assertEquals(0, sut.getAllTraceMessages().size());
        assertEquals(0, sut.getAllExceptionMessages().size());
        assertEquals(0, sut.getAllThrowables().size());
        assertEquals(null, sut.getLastMessage());
    }

    @Test
    @DisplayName("Clear should allow new messages to be added after reset")
    void clearShouldAllowNewMessagesAfterReset() {
        InMemoryMessageHandler sut = new InMemoryMessageHandler();
        sut.info("before");
        sut.clear();

        sut.info("after");

        assertEquals(1, sut.getAllMessages().size());
        assertEquals(1, sut.getAllInfoMessages().size());
        assertEquals("after", sut.getLastMessage());
    }

    @Test
    @DisplayName("MessageHandler.close() default no-op should do nothing on a non-output handler")
    void messageHandlerCloseDefaultNoOpShouldDoNothing() {
        MessageHandler handler = new InMemoryMessageHandler();
        assertDoesNotThrow(handler::close);
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("Snapshot should remain internally consistent under concurrent writes")
    void snapshotShouldRemainInternallyConsistentUnderConcurrentWrites() throws Exception {
        final int messageCount = 20_000;
        InMemoryMessageHandler handler = new InMemoryMessageHandler(messageCount + 10);
        CountDownLatch start = new CountDownLatch(1);

        Thread writer = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < messageCount; i++) {
                    handler.info("snapshot-msg-" + i);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "inmemory-snapshot-writer");
        writer.start();
        start.countDown();

        while (writer.isAlive()) {
            InMemoryMessageHandler.Snapshot snapshot = handler.snapshot();
            assertEquals(snapshot.getAllInfoMessages().size(), snapshot.getAllMessages().size());
            if (!snapshot.getAllMessages().isEmpty()) {
                assertEquals(snapshot.getAllMessages().get(snapshot.getAllMessages().size() - 1), snapshot.getLastMessage());
            }
        }

        writer.join();
        InMemoryMessageHandler.Snapshot finalSnapshot = handler.snapshot();
        assertEquals(messageCount, finalSnapshot.getAllInfoMessages().size());
        assertEquals(messageCount, finalSnapshot.getAllMessages().size());
    }
}
