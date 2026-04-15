package com.threeamigos.common.util.implementations.messagehandler;

final class ExceptionMessageFormatter {

    private ExceptionMessageFormatter() {
    }

    static String detail(final Exception exception) {
        String message = exception.getMessage();
        return message != null ? message : exception.toString();
    }

    static String withPrefix(final String message, final Exception exception) {
        return message + ": " + detail(exception);
    }
}
