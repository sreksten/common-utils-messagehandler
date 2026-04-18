package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.ContextInfo;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AbstractMessageHandler unit tests")
@Tag("unit")
@Tag("messageHandler")
class AbstractMessageHandlerUnitTest {

    private static final class ProbeMessageHandler extends AbstractMessageHandler {
        String lastMessage;
        Exception lastException;
        ContextInfo lastContextInfo;
        int callCount;

        private void record(String message, ContextInfo ctx) {
            lastMessage = message;
            lastContextInfo = ctx;
            callCount++;
        }

        private void record(Exception exception, ContextInfo ctx) {
            lastException = exception;
            lastContextInfo = ctx;
            callCount++;
        }

        private void record(String message, Exception exception, ContextInfo ctx) {
            lastMessage = message;
            lastException = exception;
            lastContextInfo = ctx;
            callCount++;
        }

        @Override protected void handleInfoMessageImpl(@Nonnull String msg, @Nonnull ContextInfo ctx)  { record(msg, ctx); }
        @Override protected void handleWarnMessageImpl(@Nonnull String msg, @Nonnull ContextInfo ctx)  { record(msg, ctx); }
        @Override protected void handleErrorMessageImpl(@Nonnull String msg, @Nonnull ContextInfo ctx) { record(msg, ctx); }
        @Override protected void handleFatalMessageImpl(@Nonnull String msg, @Nonnull ContextInfo ctx) { record(msg, ctx); }
        @Override protected void handleDebugMessageImpl(@Nonnull String msg, @Nonnull ContextInfo ctx) { record(msg, ctx); }
        @Override protected void handleTraceMessageImpl(@Nonnull String msg, @Nonnull ContextInfo ctx) { record(msg, ctx); }
        @Override protected void handleExceptionImpl(@Nonnull Exception ex, @Nonnull ContextInfo ctx)                   { record(ex, ctx); }
        @Override protected void handleExceptionImpl(@Nonnull String msg, @Nonnull Exception ex, @Nonnull ContextInfo ctx) { record(msg, ex, ctx); }
    }

    private ProbeMessageHandler sut;
    private ContextInfo ctx;

    @BeforeEach
    void setUp() {
        sut = new ProbeMessageHandler();
        ctx = new ContextInfoImpl();
        ctx.add("k", "v");
    }

    // =========================================================
    // info
    // =========================================================

    @Test
    @DisplayName("info(String, ContextInfo) should forward the supplied contextInfo to impl")
    void infoWithContextShouldForwardContextInfoToImpl() {
        sut.info("msg", ctx);
        assertSame(ctx, sut.lastContextInfo);
        assertEquals("msg", sut.lastMessage);
    }

    @Test
    @DisplayName("info(String, ContextInfo) should throw NPE on null message")
    void infoWithContextShouldThrowNpeOnNullMessage() {
        assertThrows(NullPointerException.class, () -> sut.info((String) null, ctx));
    }

    @Test
    @DisplayName("info(String, ContextInfo) should throw NPE on null contextInfo")
    void infoWithContextShouldThrowNpeOnNullContextInfo() {
        assertThrows(NullPointerException.class, () -> sut.info("msg", null));
    }

    @Test
    @DisplayName("info(String, ContextInfo) should be silent when info is disabled")
    void infoWithContextShouldBeSilentWhenDisabled() {
        sut.setInfoEnabled(false);
        sut.info("msg", ctx);
        assertEquals(0, sut.callCount);
    }

    @Test
    @DisplayName("info(Supplier, ContextInfo) should forward the supplied contextInfo to impl")
    void infoSupplierWithContextShouldForwardContextInfoToImpl() {
        sut.info(() -> "msg", ctx);
        assertSame(ctx, sut.lastContextInfo);
    }

    @Test
    @DisplayName("info(Supplier, ContextInfo) should throw NPE on null supplier")
    void infoSupplierWithContextShouldThrowNpeOnNullSupplier() {
        assertThrows(NullPointerException.class, () -> sut.info((java.util.function.Supplier<String>) null, ctx));
    }

    @Test
    @DisplayName("info(Supplier, ContextInfo) should be silent and not evaluate supplier when disabled")
    void infoSupplierWithContextShouldBeSilentWhenDisabled() {
        sut.setInfoEnabled(false);
        AtomicBoolean evaluated = new AtomicBoolean(false);
        sut.info(() -> { evaluated.set(true); return "msg"; }, ctx);
        assertEquals(0, sut.callCount);
        assertFalse(evaluated.get());
    }

    // =========================================================
    // warn
    // =========================================================

    @Test
    @DisplayName("warn(String, ContextInfo) should forward the supplied contextInfo to impl")
    void warnWithContextShouldForwardContextInfoToImpl() {
        sut.warn("msg", ctx);
        assertSame(ctx, sut.lastContextInfo);
        assertEquals("msg", sut.lastMessage);
    }

    @Test
    @DisplayName("warn(String, ContextInfo) should throw NPE on null message")
    void warnWithContextShouldThrowNpeOnNullMessage() {
        assertThrows(NullPointerException.class, () -> sut.warn((String) null, ctx));
    }

    @Test
    @DisplayName("warn(String, ContextInfo) should throw NPE on null contextInfo")
    void warnWithContextShouldThrowNpeOnNullContextInfo() {
        assertThrows(NullPointerException.class, () -> sut.warn("msg", null));
    }

    @Test
    @DisplayName("warn(String, ContextInfo) should be silent when warn is disabled")
    void warnWithContextShouldBeSilentWhenDisabled() {
        sut.setWarnEnabled(false);
        sut.warn("msg", ctx);
        assertEquals(0, sut.callCount);
    }

    @Test
    @DisplayName("warn(Supplier, ContextInfo) should forward the supplied contextInfo to impl")
    void warnSupplierWithContextShouldForwardContextInfoToImpl() {
        sut.warn(() -> "msg", ctx);
        assertSame(ctx, sut.lastContextInfo);
    }

    @Test
    @DisplayName("warn(Supplier, ContextInfo) should throw NPE on null supplier")
    void warnSupplierWithContextShouldThrowNpeOnNullSupplier() {
        assertThrows(NullPointerException.class, () -> sut.warn((java.util.function.Supplier<String>) null, ctx));
    }

    @Test
    @DisplayName("warn(Supplier, ContextInfo) should be silent and not evaluate supplier when disabled")
    void warnSupplierWithContextShouldBeSilentWhenDisabled() {
        sut.setWarnEnabled(false);
        AtomicBoolean evaluated = new AtomicBoolean(false);
        sut.warn(() -> { evaluated.set(true); return "msg"; }, ctx);
        assertEquals(0, sut.callCount);
        assertFalse(evaluated.get());
    }

    // =========================================================
    // error
    // =========================================================

    @Test
    @DisplayName("error(String, ContextInfo) should forward the supplied contextInfo to impl")
    void errorWithContextShouldForwardContextInfoToImpl() {
        sut.error("msg", ctx);
        assertSame(ctx, sut.lastContextInfo);
        assertEquals("msg", sut.lastMessage);
    }

    @Test
    @DisplayName("error(String, ContextInfo) should throw NPE on null message")
    void errorWithContextShouldThrowNpeOnNullMessage() {
        assertThrows(NullPointerException.class, () -> sut.error((String) null, ctx));
    }

    @Test
    @DisplayName("error(String, ContextInfo) should throw NPE on null contextInfo")
    void errorWithContextShouldThrowNpeOnNullContextInfo() {
        assertThrows(NullPointerException.class, () -> sut.error("msg", null));
    }

    @Test
    @DisplayName("error(String, ContextInfo) should be silent when error is disabled")
    void errorWithContextShouldBeSilentWhenDisabled() {
        sut.setErrorEnabled(false);
        sut.error("msg", ctx);
        assertEquals(0, sut.callCount);
    }

    @Test
    @DisplayName("error(Supplier, ContextInfo) should forward the supplied contextInfo to impl")
    void errorSupplierWithContextShouldForwardContextInfoToImpl() {
        sut.error(() -> "msg", ctx);
        assertSame(ctx, sut.lastContextInfo);
    }

    @Test
    @DisplayName("error(Supplier, ContextInfo) should throw NPE on null supplier")
    void errorSupplierWithContextShouldThrowNpeOnNullSupplier() {
        assertThrows(NullPointerException.class, () -> sut.error((java.util.function.Supplier<String>) null, ctx));
    }

    @Test
    @DisplayName("error(Supplier, ContextInfo) should be silent and not evaluate supplier when disabled")
    void errorSupplierWithContextShouldBeSilentWhenDisabled() {
        sut.setErrorEnabled(false);
        AtomicBoolean evaluated = new AtomicBoolean(false);
        sut.error(() -> { evaluated.set(true); return "msg"; }, ctx);
        assertEquals(0, sut.callCount);
        assertFalse(evaluated.get());
    }

    // =========================================================
    // fatal
    // =========================================================

    @Test
    @DisplayName("fatal(String, ContextInfo) should forward the supplied contextInfo to impl")
    void fatalWithContextShouldForwardContextInfoToImpl() {
        sut.fatal("msg", ctx);
        assertSame(ctx, sut.lastContextInfo);
        assertEquals("msg", sut.lastMessage);
    }

    @Test
    @DisplayName("fatal(String, ContextInfo) should throw NPE on null message")
    void fatalWithContextShouldThrowNpeOnNullMessage() {
        assertThrows(NullPointerException.class, () -> sut.fatal((String) null, ctx));
    }

    @Test
    @DisplayName("fatal(String, ContextInfo) should throw NPE on null contextInfo")
    void fatalWithContextShouldThrowNpeOnNullContextInfo() {
        assertThrows(NullPointerException.class, () -> sut.fatal("msg", null));
    }

    @Test
    @DisplayName("fatal(String, ContextInfo) should be silent when fatal is disabled")
    void fatalWithContextShouldBeSilentWhenDisabled() {
        sut.setFatalEnabled(false);
        sut.fatal("msg", ctx);
        assertEquals(0, sut.callCount);
    }

    @Test
    @DisplayName("fatal(Supplier, ContextInfo) should forward the supplied contextInfo to impl")
    void fatalSupplierWithContextShouldForwardContextInfoToImpl() {
        sut.fatal(() -> "msg", ctx);
        assertSame(ctx, sut.lastContextInfo);
    }

    @Test
    @DisplayName("fatal(Supplier, ContextInfo) should throw NPE on null supplier")
    void fatalSupplierWithContextShouldThrowNpeOnNullSupplier() {
        assertThrows(NullPointerException.class, () -> sut.fatal((java.util.function.Supplier<String>) null, ctx));
    }

    @Test
    @DisplayName("fatal(Supplier, ContextInfo) should be silent and not evaluate supplier when disabled")
    void fatalSupplierWithContextShouldBeSilentWhenDisabled() {
        sut.setFatalEnabled(false);
        AtomicBoolean evaluated = new AtomicBoolean(false);
        sut.fatal(() -> { evaluated.set(true); return "msg"; }, ctx);
        assertEquals(0, sut.callCount);
        assertFalse(evaluated.get());
    }

    // =========================================================
    // debug
    // =========================================================

    @Test
    @DisplayName("debug(String, ContextInfo) should forward the supplied contextInfo to impl")
    void debugWithContextShouldForwardContextInfoToImpl() {
        sut.debug("msg", ctx);
        assertSame(ctx, sut.lastContextInfo);
        assertEquals("msg", sut.lastMessage);
    }

    @Test
    @DisplayName("debug(String, ContextInfo) should throw NPE on null message")
    void debugWithContextShouldThrowNpeOnNullMessage() {
        assertThrows(NullPointerException.class, () -> sut.debug((String) null, ctx));
    }

    @Test
    @DisplayName("debug(String, ContextInfo) should throw NPE on null contextInfo")
    void debugWithContextShouldThrowNpeOnNullContextInfo() {
        assertThrows(NullPointerException.class, () -> sut.debug("msg", null));
    }

    @Test
    @DisplayName("debug(String, ContextInfo) should be silent when debug is disabled")
    void debugWithContextShouldBeSilentWhenDisabled() {
        sut.setDebugEnabled(false);
        sut.debug("msg", ctx);
        assertEquals(0, sut.callCount);
    }

    @Test
    @DisplayName("debug(Supplier, ContextInfo) should forward the supplied contextInfo to impl")
    void debugSupplierWithContextShouldForwardContextInfoToImpl() {
        sut.debug(() -> "msg", ctx);
        assertSame(ctx, sut.lastContextInfo);
    }

    @Test
    @DisplayName("debug(Supplier, ContextInfo) should throw NPE on null supplier")
    void debugSupplierWithContextShouldThrowNpeOnNullSupplier() {
        assertThrows(NullPointerException.class, () -> sut.debug((java.util.function.Supplier<String>) null, ctx));
    }

    @Test
    @DisplayName("debug(Supplier, ContextInfo) should be silent and not evaluate supplier when disabled")
    void debugSupplierWithContextShouldBeSilentWhenDisabled() {
        sut.setDebugEnabled(false);
        AtomicBoolean evaluated = new AtomicBoolean(false);
        sut.debug(() -> { evaluated.set(true); return "msg"; }, ctx);
        assertEquals(0, sut.callCount);
        assertFalse(evaluated.get());
    }

    // =========================================================
    // trace
    // =========================================================

    @Test
    @DisplayName("trace(String, ContextInfo) should forward the supplied contextInfo to impl")
    void traceWithContextShouldForwardContextInfoToImpl() {
        sut.trace("msg", ctx);
        assertSame(ctx, sut.lastContextInfo);
        assertEquals("msg", sut.lastMessage);
    }

    @Test
    @DisplayName("trace(String, ContextInfo) should throw NPE on null message")
    void traceWithContextShouldThrowNpeOnNullMessage() {
        assertThrows(NullPointerException.class, () -> sut.trace((String) null, ctx));
    }

    @Test
    @DisplayName("trace(String, ContextInfo) should throw NPE on null contextInfo")
    void traceWithContextShouldThrowNpeOnNullContextInfo() {
        assertThrows(NullPointerException.class, () -> sut.trace("msg", null));
    }

    @Test
    @DisplayName("trace(String, ContextInfo) should be silent when trace is disabled")
    void traceWithContextShouldBeSilentWhenDisabled() {
        sut.setTraceEnabled(false);
        sut.trace("msg", ctx);
        assertEquals(0, sut.callCount);
    }

    @Test
    @DisplayName("trace(Supplier, ContextInfo) should forward the supplied contextInfo to impl")
    void traceSupplierWithContextShouldForwardContextInfoToImpl() {
        sut.trace(() -> "msg", ctx);
        assertSame(ctx, sut.lastContextInfo);
    }

    @Test
    @DisplayName("trace(Supplier, ContextInfo) should throw NPE on null supplier")
    void traceSupplierWithContextShouldThrowNpeOnNullSupplier() {
        assertThrows(NullPointerException.class, () -> sut.trace((java.util.function.Supplier<String>) null, ctx));
    }

    @Test
    @DisplayName("trace(Supplier, ContextInfo) should be silent and not evaluate supplier when disabled")
    void traceSupplierWithContextShouldBeSilentWhenDisabled() {
        sut.setTraceEnabled(false);
        AtomicBoolean evaluated = new AtomicBoolean(false);
        sut.trace(() -> { evaluated.set(true); return "msg"; }, ctx);
        assertEquals(0, sut.callCount);
        assertFalse(evaluated.get());
    }

    // =========================================================
    // exception(Exception, ContextInfo)
    // =========================================================

    @Test
    @DisplayName("exception(Exception, ContextInfo) should forward the supplied contextInfo to impl")
    void exceptionWithContextShouldForwardContextInfoToImpl() {
        RuntimeException ex = new RuntimeException("boom");
        sut.exception(ex, ctx);
        assertSame(ctx, sut.lastContextInfo);
        assertSame(ex, sut.lastException);
    }

    @Test
    @DisplayName("exception(Exception, ContextInfo) should throw NPE on null exception")
    void exceptionWithContextShouldThrowNpeOnNullException() {
        assertThrows(NullPointerException.class, () -> sut.exception(null, ctx));
    }

    @Test
    @DisplayName("exception(Exception, ContextInfo) should throw NPE on null contextInfo")
    void exceptionWithContextShouldThrowNpeOnNullContextInfo() {
        assertThrows(NullPointerException.class, () -> sut.exception(new RuntimeException(), null));
    }

    @Test
    @DisplayName("exception(Exception, ContextInfo) should be silent when exception is disabled")
    void exceptionWithContextShouldBeSilentWhenDisabled() {
        sut.setExceptionEnabled(false);
        sut.exception(new RuntimeException(), ctx);
        assertEquals(0, sut.callCount);
    }

    // =========================================================
    // exception(String, Exception, ContextInfo)
    // =========================================================

    @Test
    @DisplayName("exception(String, Exception, ContextInfo) should forward the supplied contextInfo to impl")
    void exceptionWithMessageAndContextShouldForwardContextInfoToImpl() {
        RuntimeException ex = new RuntimeException("boom");
        sut.exception("pfx", ex, ctx);
        assertSame(ctx, sut.lastContextInfo);
        assertEquals("pfx", sut.lastMessage);
        assertSame(ex, sut.lastException);
    }

    @Test
    @DisplayName("exception(String, Exception, ContextInfo) should throw NPE on null message")
    void exceptionWithMessageAndContextShouldThrowNpeOnNullMessage() {
        assertThrows(NullPointerException.class, () -> sut.exception(null, new RuntimeException(), ctx));
    }

    @Test
    @DisplayName("exception(String, Exception, ContextInfo) should throw NPE on null exception")
    void exceptionWithMessageAndContextShouldThrowNpeOnNullException() {
        assertThrows(NullPointerException.class, () -> sut.exception("pfx", null, ctx));
    }

    @Test
    @DisplayName("exception(String, Exception, ContextInfo) should throw NPE on null contextInfo")
    void exceptionWithMessageAndContextShouldThrowNpeOnNullContextInfo() {
        assertThrows(NullPointerException.class, () -> sut.exception("pfx", new RuntimeException(), null));
    }

    @Test
    @DisplayName("exception(String, Exception, ContextInfo) should be silent when exception is disabled")
    void exceptionWithMessageAndContextShouldBeSilentWhenDisabled() {
        sut.setExceptionEnabled(false);
        sut.exception("pfx", new RuntimeException(), ctx);
        assertEquals(0, sut.callCount);
    }
}
