package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TracerMessageHandlerFactory unit tests")
@Tag("unit")
@Tag("messageHandler")
class TracerMessageHandlerFactoryUnitTest extends AbstractOtelValidatorLogTrapUnitTest {

    @Test
    @DisplayName("factory should create all handler backends")
    void factoryShouldCreateAllHandlerBackends() {
        LogRecordFactory enrichingFactory = new LogRecordFactoryImpl();
        TracerMessageHandlerFactory factory = new TracerMessageHandlerFactory(enrichingFactory, "target/factory-test.log");

        MessageHandler console = factory.createConsole();
        MessageHandler file = factory.createFile("target/factory-test-explicit.log");
        MessageHandler inMemory = factory.createInMemory();
        MessageHandler julByName = factory.createJUL("factory-jul-name");
        MessageHandler julByLogger = factory.createJUL(java.util.logging.Logger.getLogger("factory-jul"));
        MessageHandler log4jByName = factory.createLog4J("factory-log4j-name");
        MessageHandler log4jByLogger = factory.createLog4J(org.apache.logging.log4j.LogManager.getLogger("factory-log4j"));
        MessageHandler slf4jByName = factory.createSLF4J("factory-slf4j-name");
        MessageHandler slf4jByLogger = factory.createSLF4J(org.slf4j.LoggerFactory.getLogger("factory-slf4j"));
        MessageHandler swing = factory.createSwing();
        MessageHandler noop = factory.createVoid();

        assertTrue(console instanceof com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler);
        assertTrue(file instanceof com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler);
        assertTrue(inMemory instanceof StructuredBackendMessageHandler);
        assertTrue(julByName instanceof StructuredBackendMessageHandler);
        assertTrue(julByLogger instanceof StructuredBackendMessageHandler);
        assertTrue(log4jByName instanceof StructuredBackendMessageHandler);
        assertTrue(log4jByLogger instanceof StructuredBackendMessageHandler);
        assertTrue(slf4jByName instanceof StructuredBackendMessageHandler);
        assertTrue(slf4jByLogger instanceof StructuredBackendMessageHandler);
        assertTrue(swing instanceof StructuredBackendMessageHandler);
        assertTrue(noop instanceof com.threeamigos.common.util.implementations.messagehandler.VoidMessageHandler);

        assertDoesNotThrow(() -> {
            console.info("a");
            file.info("b");
            inMemory.info("c");
            julByName.info("d");
            julByLogger.info("e");
            log4jByName.info("f");
            log4jByLogger.info("g");
            slf4jByName.info("h");
            slf4jByLogger.info("i");
            // Avoid triggering UI calls in headless/CI environments.
            noop.info("k");
            file.close();
            console.close();
        });
    }
}
