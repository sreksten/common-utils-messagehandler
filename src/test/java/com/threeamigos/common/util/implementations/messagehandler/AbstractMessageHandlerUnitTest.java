package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        public void handleThrowable(@Nonnull String message, @Nonnull Throwable throwable) {
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
    @DisplayName("message-based APIs should reject null values")
    void messageApisShouldRejectNullValues() {
        ProbeMessageHandler sut = new ProbeMessageHandler();
        sut.setDebugEnabled(true);
        sut.setTraceEnabled(true);

        assertThrows(NullPointerException.class, () -> sut.info((String) null));
        assertThrows(NullPointerException.class, () -> sut.warn((String) null));
        assertThrows(NullPointerException.class, () -> sut.error((String) null));
        assertThrows(NullPointerException.class, () -> sut.fatal((String) null));
        assertThrows(NullPointerException.class, () -> sut.debug((String) null));
        assertThrows(NullPointerException.class, () -> sut.trace((String) null));
    }

    @Test
    @DisplayName("supplier-based APIs should reject null suppliers")
    void supplierApisShouldRejectNullSuppliers() {
        ProbeMessageHandler sut = new ProbeMessageHandler();
        sut.setDebugEnabled(true);
        sut.setTraceEnabled(true);

        assertThrows(NullPointerException.class, () -> sut.info((java.util.function.Supplier<String>) null));
        assertThrows(NullPointerException.class, () -> sut.warn((java.util.function.Supplier<String>) null));
        assertThrows(NullPointerException.class, () -> sut.error((java.util.function.Supplier<String>) null));
        assertThrows(NullPointerException.class, () -> sut.fatal((java.util.function.Supplier<String>) null));
        assertThrows(NullPointerException.class, () -> sut.debug((java.util.function.Supplier<String>) null));
        assertThrows(NullPointerException.class, () -> sut.trace((java.util.function.Supplier<String>) null));
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
    @DisplayName("exception methods should validate inputs and honor exception flag")
    void exceptionMethodsShouldValidateInputsAndHonorFlag() {
        ProbeMessageHandler sut = new ProbeMessageHandler();

        assertThrows(NullPointerException.class, () -> sut.exception((Exception) null));
        assertThrows(NullPointerException.class, () -> sut.exception(null, new RuntimeException("x")));
        assertThrows(NullPointerException.class, () -> sut.exception("prefix", null));

        RuntimeException ex = new RuntimeException("boom");
        sut.exception("prefix", ex);
        assertEquals("EXCEPTION", sut.lastLevel);
        assertEquals("prefix", sut.lastMessage);
        assertEquals(ex, sut.lastException);

        sut.setErrorEnabled(false);
        sut.exception(new RuntimeException("dropped"));
        assertEquals(1, sut.callCount);
        assertTrue(sut.lastException == ex);
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

        assertThrows(NullPointerException.class, () -> sut.setEnabled(null, true));
    }
}
