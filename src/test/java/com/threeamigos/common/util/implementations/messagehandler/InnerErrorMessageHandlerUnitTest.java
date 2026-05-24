package com.threeamigos.common.util.implementations.messagehandler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("InnerErrorMessageHandler unit tests")
@Tag("unit")
@Tag("messageHandler")
class InnerErrorMessageHandlerUnitTest {

    @AfterEach
    void resetGlobalConsumerAfterEach() {
        InnerErrorMessageHandler.resetGlobalConsumer();
    }

    @Test
    @DisplayName("consume(String) should use the configured global consumer")
    void consumeShouldUseConfiguredGlobalConsumer() {
        List<String> captured = new ArrayList<String>();
        InnerErrorMessageHandler.setGlobalConsumer(captured::add);

        InnerErrorMessageHandler.consume("inner-failure");

        assertEquals(1, captured.size());
        assertEquals("inner-failure", captured.get(0));
    }

    @Test
    @DisplayName("consume(local, message) should prefer the local consumer when it succeeds")
    void consumeWithLocalConsumerShouldPreferLocalConsumer() {
        List<String> localCaptured = new ArrayList<String>();
        List<String> globalCaptured = new ArrayList<String>();
        InnerErrorMessageHandler.setGlobalConsumer(globalCaptured::add);

        InnerErrorMessageHandler.consume(localCaptured::add, "inner-failure");

        assertEquals(1, localCaptured.size());
        assertTrue(globalCaptured.isEmpty());
    }

    @Test
    @DisplayName("consume(local, message) should fallback to global when local consumer throws")
    void consumeWithFailingLocalConsumerShouldFallbackToGlobal() {
        List<String> globalCaptured = new ArrayList<String>();
        InnerErrorMessageHandler.setGlobalConsumer(globalCaptured::add);

        InnerErrorMessageHandler.consume(message -> {
            throw new IllegalStateException("local-consumer-failure");
        }, "inner-failure");

        assertEquals(1, globalCaptured.size());
        assertEquals("inner-failure", globalCaptured.get(0));
    }

    @Test
    @DisplayName("consume(String) should fallback to System.err when global consumer throws")
    void consumeShouldFallbackToSystemErrWhenGlobalConsumerThrows() throws Exception {
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(errContent, true, StandardCharsets.UTF_8.name()));
            InnerErrorMessageHandler.setGlobalConsumer(message -> {
                throw new IllegalStateException("global-consumer-failure");
            });

            assertDoesNotThrow(() -> InnerErrorMessageHandler.consume("inner-failure"));

            String fallbackOutput = errContent.toString(StandardCharsets.UTF_8.name());
            assertFalse(fallbackOutput.isEmpty());
            assertTrue(fallbackOutput.contains("inner-failure"));
        } finally {
            System.setErr(originalErr);
        }
    }
}
