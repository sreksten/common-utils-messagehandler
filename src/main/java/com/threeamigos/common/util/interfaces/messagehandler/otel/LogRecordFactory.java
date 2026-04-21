package com.threeamigos.common.util.interfaces.messagehandler.otel;

/**
 *
 * @author Stefano Reksten
 */
public interface LogRecordFactory {

    LogRecord create();

    LogRecord create(SeverityNumber severityNumber, String message);

    LogRecord create(String message, Throwable throwable);

    LogRecord create(Throwable throwable);
}
