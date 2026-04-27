package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;

import java.util.function.Supplier;

/**
 *
 * @author Stefano Reksten
 */
public class VoidMessageHandler implements MessageHandler {

    @Override
    public void handleMessage(@Nonnull SeverityNumber level, @Nonnull String message) {
    }

    @Override
    public void handleThrowable(@Nonnull String message, @Nonnull Throwable throwable) {
    }

    @Override
    public void debug(@Nonnull String message) {
    }

    @Override
    public void debug(@Nonnull Supplier<String> messageSupplier) {
    }

    @Override
    public void error(@Nonnull String message) {
    }

    @Override
    public void error(@Nonnull Supplier<String> messageSupplier) {
    }

    @Override
    public void fatal(@Nonnull String message) {
    }

    @Override
    public void fatal(@Nonnull Supplier<String> messageSupplier) {
    }

    @Override
    public void info(@Nonnull String message) {
    }

    @Override
    public void info(@Nonnull Supplier<String> messageSupplier) {
    }

    @Override
    public void exception(@Nonnull Throwable throwable) {
    }

    @Override
    public void exception(@Nonnull String message, @Nonnull Throwable throwable) {
    }

    @Override
    public void trace(@Nonnull String message) {
    }

    @Override
    public void trace(@Nonnull Supplier<String> messageSupplier) {
    }

    @Override
    public void warn(@Nonnull String message) {
    }

    @Override
    public void warn(@Nonnull Supplier<String> messageSupplier) {
    }
}
