package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OpenTelemetryAttributeValidator error handling unit tests")
@Tag("unit")
@Tag("messageHandler")
class OpenTelemetryAttributeValidatorErrorHandlingUnitTest {

    private static final boolean ORIGINAL_LENIENT = OpenTelemetryAttributeValidator.isLenientMode();

    @AfterEach
    void restoreLenientMode() {
        setLenient(ORIGINAL_LENIENT);
    }

    @Test
    @DisplayName("strict mode should throw for direct and bundled handling")
    void strictModeShouldThrowForHandleAndHandleBundled() {
        setLenient(false);

        IllegalArgumentException direct = assertThrows(IllegalArgumentException.class,
                () -> OpenTelemetryAttributeValidator.handle("boom"));
        assertEquals("boom", direct.getMessage());

        IllegalArgumentException bundled = assertThrows(IllegalArgumentException.class,
                () -> OpenTelemetryAttributeValidator.handleBundled("valueMustNotBeNull"));
        assertNotNull(bundled.getMessage());
    }

    @Test
    @DisplayName("strict mode should throw original exception when bundled key is missing")
    void strictModeShouldThrowOriginalWhenBundledKeyMissing() {
        setLenient(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> OpenTelemetryAttributeValidator.handleBundled("missing.bundle.key"));
        assertNotNull(ex.getMessage());
    }

    @Test
    @DisplayName("lenient mode should log and continue for direct and bundled handling")
    void lenientModeShouldContinueForHandleAndHandleBundled() {
        setLenient(true);

        List<LogRecord> loggerRecords = captureLogs(() -> {
            OpenTelemetryAttributeValidator.handle("boom");
            OpenTelemetryAttributeValidator.handleBundled("valueMustNotBeNull");
            OpenTelemetryAttributeValidator.handleBundled("missing.bundle.key");
        });

        assertTrue(hasSevereMessageWithThrowable(loggerRecords, "boom"));
        assertTrue(hasSevereMessageWithThrowable(loggerRecords, MessageHandlerResourceBundle.get("valueMustNotBeNull")));
        assertTrue(hasSevereMessageWithThrowable(loggerRecords, "missing.bundle.key"));
        assertTrue(loggerRecords.stream().anyMatch(record ->
                record.getLevel() == Level.SEVERE
                        && record.getMessage() != null
                        && record.getMessage().contains("missing.bundle.key")
                        && record.getThrown() != null));
    }

    @Test
    @DisplayName("test helper should be able to switch lenient flag")
    void testHelperShouldSwitchLenientFlag() {
        setLenient(false);
        assertFalse(OpenTelemetryAttributeValidator.isLenientMode());
        setLenient(true);
        assertTrue(OpenTelemetryAttributeValidator.isLenientMode());
    }

    private static void setLenient(final boolean value) {
        OpenTelemetryAttributeValidator.setLenientModeForTests(value);
    }

    private static List<LogRecord> captureLogs(final Runnable call) {
        Logger logger = Logger.getLogger(OpenTelemetryAttributeValidator.class.getName());
        RecordingHandler handler = new RecordingHandler();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        Level previousLevel = logger.getLevel();
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(handler);
        try {
            assertDoesNotThrow(call::run);
            return new ArrayList<>(handler.records);
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(previousUseParentHandlers);
            logger.setLevel(previousLevel);
        }
    }

    private static boolean hasSevereMessageWithThrowable(final List<LogRecord> records, final String expectedMessage) {
        return records.stream().anyMatch(record ->
                record.getLevel() == Level.SEVERE
                        && expectedMessage.equals(record.getMessage())
                        && record.getThrown() != null);
    }

    private static final class RecordingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(final LogRecord record) {
            if (record != null) {
                records.add(record);
            }
        }

        @Override
        public void flush() {
            // no-op
        }

        @Override
        public void close() {
            // no-op
        }
    }
}
