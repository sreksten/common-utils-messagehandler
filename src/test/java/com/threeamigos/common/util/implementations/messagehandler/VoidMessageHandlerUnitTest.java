package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("VoidMessageHandler unit tests")
@Tag("unit")
@Tag("messageHandler")
class VoidMessageHandlerUnitTest {

    @Test
    @DisplayName("all methods should be safe no-ops")
    void allMethodsShouldBeSafeNoOps() {
        VoidMessageHandler sut = new VoidMessageHandler();
        RuntimeException throwable = new RuntimeException("boom");

        assertDoesNotThrow(() -> {
            sut.handleMessage(SeverityNumber.INFO, "message");
            sut.handleThrowable("error", throwable);
            sut.debug("debug");
            sut.debug(() -> "debug");
            sut.error("error");
            sut.error(() -> "error");
            sut.fatal("fatal");
            sut.fatal(() -> "fatal");
            sut.info("info");
            sut.info(() -> "info");
            sut.exception(throwable);
            sut.exception("exception", throwable);
            sut.trace("trace");
            sut.trace(() -> "trace");
            sut.warn("warn");
            sut.warn(() -> "warn");
        });
    }
}
