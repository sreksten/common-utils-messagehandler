package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.RawJsonRecordFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("StructuredBackendMessageHandler unit tests")
@Tag("unit")
@Tag("messageHandler")
class StructuredBackendMessageHandlerUnitTest extends AbstractOtelValidatorLogTrapUnitTest {

    @Test
    @DisplayName("structured backend handler should format and forward message and throwable")
    void structuredBackendHandlerShouldFormatAndForwardMessageAndThrowable() {
        InMemoryMessageHandler backend = new InMemoryMessageHandler();
        StructuredBackendMessageHandler handler =
                new StructuredBackendMessageHandler(backend, new RawJsonRecordFormatter());

        handler.handleMessage(SeverityNumber.INFO, "hello");
        handler.exception("boom", new IllegalStateException("x"));
        handler.close();

        List<String> messages = backend.getAllMessages();
        assertTrue(messages.get(0).contains("\"severityText\":\"INFO\""));
        assertTrue(messages.get(0).contains("\"stringValue\":\"hello\""));
        assertTrue(messages.get(1).contains("\"stringValue\":\"boom\""));
    }
}
