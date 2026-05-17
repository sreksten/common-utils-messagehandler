package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.file.DailyRotationPolicy;
import com.threeamigos.common.util.implementations.messagehandler.file.SizeRotationPolicy;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Examples taken from the README.md file that can be run as tests (those are NOT real unit tests, but handling them
 * as such is convenient for demonstration purposes).
 * NOTE: <code>System.out</code> and <code>System.err</code> are two different streams (usually two
 * different OS pipes). IntelliJ captures them separately and merges them in the Run/Test console.
 * That merge is not a strict total-order guarantee across the two streams, so stderr lines can
 * appear before earlier stdout lines “randomly”.
 *
 * @author Stefano Reksten
 */
@DisplayName("Readme examples")
@Disabled
public class ReadmeUnitTest {

    @Test
    @DisplayName("Baseline logging example - 1")
    public void baselineLoggingExample1() {
        System.out.println("Application started");
        System.out.println("Warning! Cache is near capacity");
        System.err.println("Error... Payment failed");

        try {
            throw new IllegalStateException("Boom");
        } catch (Exception ex) {
            System.err.println("An exception happened! It says, " + ex.getMessage()); // Ditto
            ex.printStackTrace(System.err); //  Ditto
        }
    }

    @Test
    @DisplayName("Baseline logging example - 2")
    public void baselineLoggingExample2() {
        System.out.println("[INFO] Application started");
        System.out.println("[WARN] Cache is near capacity");
        System.err.println("[ERROR] Payment failed");

        try {
            throw new IllegalStateException("Boom");
        } catch (Exception ex) {
            System.err.println("[ERROR] " + ex.getMessage());
            ex.printStackTrace(System.err);
        }
    }

    @Test
    @DisplayName("ConsoleMessageHandler example")
    void consoleMessageHandlerExample() {
        try (MessageHandler handler = new ConsoleMessageHandler()) {
            //  Logging a String.
            handler.info("Application started");
            // Using a Supplier<String>. If warn is not enabled, the formatting would not be done
            // because the String provider would not be queried.
            handler.warn(() -> String.format("Cache is near capacity: %d%%", 85));
            handler.error("Payment failed");

            try {
                throw new IllegalStateException("Boom");
            } catch (Exception ex) {
                handler.exception("Failure while processing checkout", ex);
            }
        }
    }

    @Test
    @DisplayName("Severity filtering example")
    void severityFilteringExample() {
        AbstractMessageHandler handler = new ConsoleMessageHandler();

        handler.debug("Not shown by default");
        handler.setDebugEnabled(true);
        handler.debug("Now visible");

        handler.setEnabled(SeverityNumber.INFO3, false);
        handler.log(SeverityNumber.INFO3, "Filtered out");
        handler.log(SeverityNumber.INFO, "Still visible");
    }

    @Test
    @DisplayName("FileMessageHandler example 1")
    void fileMessageHandlerExample1() {
        final String logFileName = "/tmp/app.log";
        clearOldFiles(logFileName);

        // "Real" program execution
        try (MessageHandler handler = new FileMessageHandler("/tmp/app.log")) {
            handler.info("Started");
            handler.error("Example error");
        }
        // Program ends here

        // Let's verify the outcome. We should have a file...
        File file = new File("/tmp/app.log");
        assertTrue(file.exists());

        // ...that should contain the two logged messages.
        assertFileContains(file, "Started", "Example error");

        // All clear; let's delete the file to clean up afterward.
        assertTrue(file.delete());
    }

    @Test
    @DisplayName("FileMessageHandler rotation by size example 1")
    void shouldNotRotateFileBySize() {
        final String logFileName = "/tmp/app.log";
        clearOldFiles(logFileName);

        // "Real" program execution
        FileMessageHandler handler = new FileMessageHandler(
                "/tmp/app.log",
                new SizeRotationPolicy(5L * 1024L * 1024L) // 5 MB
        );
        handler.info("Will rotate when threshold is reached");
        handler.close();
        // Program ends here

        // Let's verify the outcome. We should have a file...
        File file = new File("/tmp/app.log");
        assertTrue(file.exists());

        // ...that should contain the logged message.
        assertFileContains(file, "Will rotate when threshold is reached");

        // All clear; let's delete the file to clean up afterward.
        assertTrue(file.delete());
    }

    @Test
    @DisplayName("FileMessageHandler rotation by size example 2")
    void shouldRotateFileBySize() {
        final String logFileName = "/tmp/app.log";
        clearOldFiles(logFileName);

        // "Real" program execution
        FileMessageHandler handler = new FileMessageHandler(
                "/tmp/app.log",
                new SizeRotationPolicy(20L) // 20 bytes (yikes!)
        );
        handler.info("Will rotate when threshold is reached");
        handler.info("This message should result in a new file being created");
        handler.info("Hey! New file.");
        handler.close();
        // Program ends here

        // Let's verify the outcome. We should have a file...
        File file = new File("/tmp/app.log");
        assertTrue(file.exists());

        // ...that should contain only the first logged message.
        assertFileContains(file, "Hey! New file.");

        // All clear; let's delete the file to clean up afterward.
        assertTrue(file.delete());

        // Also, in the directory we should have already rotated files
        File directory = file.getParentFile();
        assertTrue(directory.exists());
        for (File rotatedFile : Objects.requireNonNull(directory.listFiles())) {
            if (rotatedFile.getName().contains("app.log") && rotatedFile.getName().length() > "app.log".length()) {
                assertFileContains(rotatedFile, "Will rotate when threshold is reached",
                        "This message should result in a new file being created");

                // All clear; let's delete the file to clean up afterward.
                assertTrue(rotatedFile.delete());
            }
        }
    }

    @Test
    @DisplayName("FileMessageHandler rotation by date example")
    void shouldRotateFileByDate() {
        final String logFileName = "/tmp/app.log";
        clearOldFiles(logFileName);

        // "Real" program execution
        FileMessageHandler handler = new FileMessageHandler(
                "/tmp/app.log",
                new DailyRotationPolicy()
        );
        handler.info("Will rotate when date is changed");
        handler.close();
        // Program ends here

        // Let's verify the outcome. We should have a file...
        File file = new File("/tmp/app.log");
        assertTrue(file.exists());

        // ...that should contain the logged message.
        assertFileContains(file, "Will rotate when date is changed");

        // All clear; let's delete the file to clean up afterward.
        assertTrue(file.delete());
    }

    @Test
    @DisplayName("InMemoryMessageHandler example")
    void inMemoryMessageHandlerExample() {
        InMemoryMessageHandler messageHandler = new InMemoryMessageHandler();
        messageHandler.info("This is a test message");
        messageHandler.close();

        assertTrue(messageHandler.getAllInfoMessages().stream().anyMatch(m -> m.contains("This is a test message")));

        String lastMessage = messageHandler.getLastMessage();
        assertNotNull(lastMessage);
        assertTrue(lastMessage.contains("This is a test message"));
    }

    @Test
    @DisplayName("CompositeMessageHandler example")
    void compositeMessageHandlerExample() {
        InMemoryMessageHandler messageHandler1 = new InMemoryMessageHandler();
        InMemoryMessageHandler messageHandler2 = new InMemoryMessageHandler();

        CompositeMessageHandler compositeHandler = new CompositeMessageHandler(messageHandler1, messageHandler2);
        compositeHandler.info("This is a test message from composite handler");
        compositeHandler.close();

        assertTrue(messageHandler1.getAllInfoMessages().stream().anyMatch(m ->
                m.contains("This is a test message from composite handler")));
        assertTrue(messageHandler2.getAllInfoMessages().stream().anyMatch(m ->
                m.contains("This is a test message from composite handler")));
    }

    @Test
    @DisplayName("SwingMessageHandler example")
    void swingMessageHandlerExample() {
        SwingMessageHandler swingMessageHandler = new SwingMessageHandler();
        swingMessageHandler.info("This is a test message from Swing handler");
        swingMessageHandler.error("This is a test error message from Swing handler");
    }

    private void clearOldFiles(String logFileName) {
        File directory = new File(logFileName).getParentFile();
        for (File rotatedFile : Objects.requireNonNull(directory.listFiles())) {
            if (rotatedFile.getName().contains("app.log") && rotatedFile.getName().length() > "app.log".length()) {
                assertTrue(rotatedFile.delete());
            }
        }
    }

    private void assertFileContains(File file, String ... strings) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(file.getName() + ": " + line);
                assertTrue(Arrays.stream(strings).anyMatch(line::endsWith));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
