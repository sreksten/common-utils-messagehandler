package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
        OpenTelemetryAttributeValidator.setLogTrapForTests(null);
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

        List<LoggedError> loggedErrors = trapLogs(() -> {
            OpenTelemetryAttributeValidator.handle("boom");
            OpenTelemetryAttributeValidator.handleBundled("valueMustNotBeNull");
            OpenTelemetryAttributeValidator.handleBundled("missing.bundle.key");
        });

        assertTrue(hasMessageWithThrowable(loggedErrors, "boom", IllegalArgumentException.class));
        assertTrue(hasMessageWithThrowable(loggedErrors,
                MessageHandlerResourceBundle.get("valueMustNotBeNull"), IllegalArgumentException.class));
        assertTrue(hasMessageWithThrowable(loggedErrors, "missing.bundle.key", RuntimeException.class));
    }

    @Test
    @DisplayName("test helper should be able to switch lenient flag")
    void testHelperShouldSwitchLenientFlag() {
        setLenient(false);
        assertFalse(OpenTelemetryAttributeValidator.isLenientMode());
        setLenient(true);
        assertTrue(OpenTelemetryAttributeValidator.isLenientMode());
    }

    @Test
    @DisplayName("report should create default throwable when null and fallback when trap fails")
    void reportShouldCreateDefaultThrowableWhenNullAndFallbackWhenTrapFails() {
        setLenient(true);

        List<LoggedError> trapped = new ArrayList<>();
        OpenTelemetryAttributeValidator.setLogTrapForTests((message, throwable) -> {
            if ("explode".equals(message)) {
                throw new RuntimeException("trap-failure");
            }
            trapped.add(new LoggedError(message, throwable));
        });
        OpenTelemetryAttributeValidator.report("null-throwable", null);
        OpenTelemetryAttributeValidator.report("explode", new IllegalStateException("x"));
        OpenTelemetryAttributeValidator.setLogTrapForTests(null);

        assertTrue(hasMessageWithThrowable(trapped, "null-throwable", IllegalArgumentException.class));
    }

    @Test
    @DisplayName("lenient bundled resolution should include args when key is missing")
    void lenientBundledResolutionShouldIncludeArgsWhenKeyIsMissing() {
        setLenient(true);

        List<LoggedError> loggedErrors = trapLogs(() ->
                OpenTelemetryAttributeValidator.reportBundled("missing.bundle.with.args", "a", 1));

        assertTrue(loggedErrors.stream().anyMatch(record ->
                record.message != null && record.message.contains("missing.bundle.with.args")));
    }

    @Test
    @DisplayName("strict mode missing bundled key with args should not throw")
    void strictModeMissingBundledKeyWithArgsShouldNotThrow() {
        setLenient(false);
        assertDoesNotThrow(() -> OpenTelemetryAttributeValidator.reportBundled("missing.bundle.with.args.strict", "x"));
    }

    private static void setLenient(final boolean value) {
        OpenTelemetryAttributeValidator.setLenientModeForTests(value);
    }

    private static List<LoggedError> trapLogs(final Runnable call) {
        List<LoggedError> trapped = new ArrayList<>();
        OpenTelemetryAttributeValidator.setLogTrapForTests((message, throwable) ->
                trapped.add(new LoggedError(message, throwable)));
        try {
            assertDoesNotThrow(call::run);
            return new ArrayList<>(trapped);
        } finally {
            OpenTelemetryAttributeValidator.setLogTrapForTests(null);
        }
    }

    private static boolean hasMessageWithThrowable(final List<LoggedError> records,
                                                   final String expectedMessage,
                                                   final Class<? extends Throwable> throwableClass) {
        return records.stream().anyMatch(record ->
                expectedMessage.equals(record.message)
                        && record.throwable != null
                        && throwableClass.isInstance(record.throwable));
    }

    private static final class LoggedError {
        private final String message;
        private final Throwable throwable;

        private LoggedError(final String message, final Throwable throwable) {
            this.message = message;
            this.throwable = throwable;
        }
    }
}
