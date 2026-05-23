package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AbstractMessageHandler unit tests")
@Tag("unit")
@Tag("messageHandler")
class AbstractMessageHandlerUnitTest {

    private static final class ProbeMessageHandler extends AbstractMessageHandler {
        String lastLevel;
        String lastMessage;
        Throwable lastException;
        int callCount;

        private void record(final String level, final String message, final Throwable exception) {
            this.lastLevel = level;
            this.lastMessage = message;
            this.lastException = exception;
            this.callCount++;
        }

        @Override
        public void handleMessage(@Nonnull SeverityNumber level, @Nonnull String message) {
            record(level.name(), message, null);
        }

        @Override
        protected void handleExceptionInternal(@Nonnull String message, @Nonnull Throwable throwable) {
            record("EXCEPTION", message, throwable);
        }
    }

    @Test
    @DisplayName("info(String) should forward when enabled")
    void infoShouldForwardWhenEnabled() {
        ProbeMessageHandler sut = new ProbeMessageHandler();

        sut.info("msg");

        assertEquals(1, sut.callCount);
        assertEquals("INFO", sut.lastLevel);
        assertEquals("msg", sut.lastMessage);
        assertNull(sut.lastException);
    }

    @Test
    @DisplayName("info(Supplier) should not evaluate supplier when level is disabled")
    void infoSupplierShouldNotEvaluateWhenDisabled() {
        ProbeMessageHandler sut = new ProbeMessageHandler();
        sut.setInfoEnabled(false);
        AtomicBoolean evaluated = new AtomicBoolean(false);

        sut.info(() -> {
            evaluated.set(true);
            return "msg";
        });

        assertFalse(evaluated.get());
        assertEquals(0, sut.callCount);
    }

    @Test
    @DisplayName("supplier log should ignore null message produced by supplier")
    void supplierLogShouldIgnoreNullProducedMessage() {
        ProbeMessageHandler sut = new ProbeMessageHandler();

        sut.info(() -> null);

        assertEquals(0, sut.callCount);
    }

    @Test
    @DisplayName("warn/error/fatal/debug/trace should forward to matching impl methods")
    void levelsShouldForwardToMatchingImplMethods() {
        ProbeMessageHandler sut = new ProbeMessageHandler();
        sut.setDebugEnabled(true);
        sut.setTraceEnabled(true);

        sut.warn("w");
        assertEquals("WARN", sut.lastLevel);
        sut.error("e");
        assertEquals("ERROR", sut.lastLevel);
        sut.fatal("f");
        assertEquals("FATAL", sut.lastLevel);
        sut.debug("d");
        assertEquals("DEBUG", sut.lastLevel);
        sut.trace("t");
        assertEquals("TRACE", sut.lastLevel);

        assertEquals(5, sut.callCount);
    }

    @Test
    @DisplayName("disabled levels should drop messages")
    void disabledLevelsShouldDropMessages() {
        ProbeMessageHandler sut = new ProbeMessageHandler();
        sut.setWarnEnabled(false);
        sut.setErrorEnabled(false);
        sut.setFatalEnabled(false);
        sut.setDebugEnabled(false);
        sut.setTraceEnabled(false);

        sut.warn("w");
        sut.error("e");
        sut.fatal("f");
        sut.debug("d");
        sut.trace("t");

        assertEquals(0, sut.callCount);
    }

    @Test
    @DisplayName("message-based APIs should ignore null values")
    void messageApisShouldIgnoreNullValues() {
        ProbeMessageHandler sut = new ProbeMessageHandler();
        sut.setDebugEnabled(true);
        sut.setTraceEnabled(true);

        assertDoesNotThrow(() -> sut.info((String) null));
        assertDoesNotThrow(() -> sut.warn((String) null));
        assertDoesNotThrow(() -> sut.error((String) null));
        assertDoesNotThrow(() -> sut.fatal((String) null));
        assertDoesNotThrow(() -> sut.debug((String) null));
        assertDoesNotThrow(() -> sut.trace((String) null));
        assertEquals(0, sut.callCount);
    }

    @Test
    @DisplayName("supplier-based APIs should ignore null suppliers")
    void supplierApisShouldIgnoreNullSuppliers() {
        ProbeMessageHandler sut = new ProbeMessageHandler();
        sut.setDebugEnabled(true);
        sut.setTraceEnabled(true);

        assertDoesNotThrow(() -> sut.info((java.util.function.Supplier<String>) null));
        assertDoesNotThrow(() -> sut.warn((java.util.function.Supplier<String>) null));
        assertDoesNotThrow(() -> sut.error((java.util.function.Supplier<String>) null));
        assertDoesNotThrow(() -> sut.fatal((java.util.function.Supplier<String>) null));
        assertDoesNotThrow(() -> sut.debug((java.util.function.Supplier<String>) null));
        assertDoesNotThrow(() -> sut.trace((java.util.function.Supplier<String>) null));
        assertEquals(0, sut.callCount);
    }

    @Test
    @DisplayName("exception(Exception) should forward when enabled")
    void exceptionShouldForwardWhenEnabled() {
        ProbeMessageHandler sut = new ProbeMessageHandler();
        IllegalArgumentException ex = new IllegalArgumentException("boom");

        sut.exception(ex);

        assertEquals(1, sut.callCount);
        assertEquals("EXCEPTION", sut.lastLevel);
        assertEquals(ex, sut.lastException);
        assertEquals("boom", sut.lastMessage);
    }

    @Test
    @DisplayName("exception methods should ignore null message or throwable and honor exception flag")
    void exceptionMethodsShouldIgnoreNullMessageOrThrowableAndHonorExceptionFlag() {
        ProbeMessageHandler sut = new ProbeMessageHandler();

        assertDoesNotThrow(() -> sut.exception((Exception) null));
        assertDoesNotThrow(() -> sut.exception(null, new RuntimeException("x")));
        assertDoesNotThrow(() -> sut.exception("prefix", null));
        assertEquals(0, sut.callCount);

        RuntimeException ex = new RuntimeException("boom");
        sut.exception("prefix", ex);
        assertEquals("EXCEPTION", sut.lastLevel);
        assertEquals("prefix", sut.lastMessage);
        assertEquals(ex, sut.lastException);

        sut.setErrorEnabled(false);
        sut.exception(new RuntimeException("dropped"));
        assertEquals(1, sut.callCount);
        assertTrue(sut.lastException == ex);

        assertDoesNotThrow(() -> sut.exception((Exception) null));
        assertDoesNotThrow(() -> sut.exception("still-validated", null));
        assertEquals(1, sut.callCount);
    }

    @Test
    @DisplayName("enabled levels should be backed by AtomicInteger")
    void enabledLevelsShouldBeBackedByAtomicInteger() throws Exception {
        Field enabledLevelsField = AbstractMessageHandler.class.getDeclaredField("enabledLevels");
        assertEquals(AtomicInteger.class, enabledLevelsField.getType());
    }

    @Test
    @DisplayName("tracer field should be volatile for cross-thread visibility")
    void tracerFieldShouldBeVolatileForCrossThreadVisibility() throws Exception {
        Field tracerField = AbstractMessageHandler.class.getDeclaredField("tracer");
        assertTrue(Modifier.isVolatile(tracerField.getModifiers()));
    }

    @Test
    @DisplayName("setEnabled should toggle a single level and keep other levels unchanged")
    void setEnabledShouldToggleSingleLevelAndValidateNull() {
        ProbeMessageHandler sut = new ProbeMessageHandler();

        assertTrue(sut.isInfoEnabled());
        assertFalse(sut.isDebugEnabled());

        sut.setEnabled(SeverityNumber.DEBUG, true);
        assertTrue(sut.isDebugEnabled());
        assertTrue(sut.isInfoEnabled());

        sut.setEnabled(SeverityNumber.DEBUG, false);
        assertFalse(sut.isDebugEnabled());
        assertTrue(sut.isInfoEnabled());

        sut.setInfoEnabled(false);
        assertDoesNotThrow(() -> sut.setEnabled(null, true));
        assertTrue(sut.isInfoEnabled());
    }

    @Test
    @DisplayName("collection enable/disable should update levels and snapshots consistently")
    void collectionEnableDisableShouldUpdateLevelsAndSnapshotsConsistently() {
        ProbeMessageHandler sut = new ProbeMessageHandler();

        sut.disable(Arrays.asList(SeverityNumber.INFO, SeverityNumber.WARN));
        sut.enable(Arrays.asList(SeverityNumber.DEBUG, SeverityNumber.TRACE));

        assertFalse(sut.isInfoEnabled());
        assertFalse(sut.isWarnEnabled());
        assertTrue(sut.isDebugEnabled());
        assertTrue(sut.isTraceEnabled());

        Set<SeverityNumber> enabled = new HashSet<>(Arrays.asList(sut.getEnabledLevels()));
        Set<SeverityNumber> disabled = new HashSet<>(Arrays.asList(sut.getDisabledLevels()));

        assertTrue(enabled.contains(SeverityNumber.DEBUG));
        assertTrue(enabled.contains(SeverityNumber.TRACE));
        assertTrue(disabled.contains(SeverityNumber.INFO));
        assertTrue(disabled.contains(SeverityNumber.WARN));
    }

    @Test
    @DisplayName("isEnabled should default null level to info")
    void isEnabledShouldDefaultNullLevelToInfo() {
        ProbeMessageHandler sut = new ProbeMessageHandler();
        assertEquals(sut.isInfoEnabled(), sut.isEnabled(null));
    }

    @Test
    @DisplayName("log should default null level to info")
    void logShouldDefaultNullLevelToInfo() {
        ProbeMessageHandler sut = new ProbeMessageHandler();

        sut.log(null, "msg");

        assertEquals(1, sut.callCount);
        assertEquals("INFO", sut.lastLevel);
        assertEquals("msg", sut.lastMessage);
    }
}
