package com.threeamigos.common.util.implementations.messagehandler;

/**
 * Internal utility for converting a {@link Throwable} to a human-readable string.
 * <p>
 * Used by all output-oriented {@link com.threeamigos.common.util.interfaces.messagehandler.MessageHandler}
 * implementations to produce a consistent text representation when writing or displaying exceptions.
 * <p>
 * This class is package-private and not part of the public API.
 */
final class ThrowableMessageFormatter {

    private ThrowableMessageFormatter() {
    }

    /**
     * Returns a human-readable detail string for the given exception.
     * <p>
     * Uses {@link Exception#getMessage()} when available; falls back to
     * {@link Exception#toString()} when the message is {@code null} (e.g., a bare
     * {@link NullPointerException} thrown without a message).
     *
     * @param throwable the Throwable to describe; must not be {@code null}
     * @return a non-null string describing the exception
     */
    static String detail(final Throwable throwable) {
        String message = throwable.getMessage();
        return message != null ? message : throwable.toString();
    }

    /**
     * Returns a string combining a contextual prefix with the exception detail.
     * <p>
     * The result has the form {@code "<message>: <detail>"} where {@code <detail>}
     * is produced by {@link #detail(Throwable)}.
     * <p>
     * If {@code message} is {@code null}, blank, or already equal to {@code <detail>},
     * this method returns {@code <detail>} to avoid duplicate output such as
     * {@code "boom: boom"}.
     *
     * @param message   a non-null contextual prefix to prepend
     * @param throwable the Throwable to describe; must not be {@code null}
     * @return a non-null string of the form {@code "<message>: <detail>"}
     */
    static String withPrefix(final String message, final Throwable throwable) {
        String detail = detail(throwable);
        if (message == null || message.isEmpty() || message.equals(detail)) {
            return detail;
        }
        return message + ": " + detail;
    }
}
