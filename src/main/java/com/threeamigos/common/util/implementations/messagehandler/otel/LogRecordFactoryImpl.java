package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * An implementation of {@link LogRecordFactory} that creates {@link LogRecord} instances.
 *
 * @author Stefano Reksten
 */
public class LogRecordFactoryImpl implements LogRecordFactory {

    @Override
    public LogRecord create() {
        return new LogRecordImpl();
    }

    @Override
    public LogRecord create(SeverityNumber severityNumber, String message) {
        if (severityNumber == null) {
            OpenTelemetryAttributeValidator.handleBundled("valueMustNotBeNull");
            severityNumber = SeverityNumber.UNSPECIFIED;
        }
        if (message == null) {
            OpenTelemetryAttributeValidator.handleBundled("nullMessageProvided");
            message = "";
        }
        LogRecordImpl logRecord = new LogRecordImpl();
        logRecord.setSeverityNumber(severityNumber);
        logRecord.setSeverityText(severityNumber.name());
        logRecord.setBody(AnyValueFactory.ofString(message));
        return logRecord;
    }

    @Override
    public LogRecord create(String message, Throwable throwable) {
        if (message == null) {
            OpenTelemetryAttributeValidator.handleBundled("nullMessageProvided");
            message = "";
        }
        if (throwable == null) {
            OpenTelemetryAttributeValidator.handleBundled("nullThrowableProvided");
        }
        LogRecordImpl logRecord = new LogRecordImpl();
        setErrorSeverity(logRecord);
        logRecord.setBody(AnyValueFactory.ofString(message));
        if (throwable != null) {
            addThrowableDetails(logRecord, throwable);
        }
        return logRecord;
    }

    @Override
    public LogRecord create(Throwable throwable) {
        if (throwable == null) {
            OpenTelemetryAttributeValidator.handleBundled("nullThrowableProvided");
            LogRecordImpl fallback = new LogRecordImpl();
            setErrorSeverity(fallback);
            fallback.setBody(AnyValueFactory.ofString(""));
            return fallback;
        }
        LogRecordImpl logRecord = new LogRecordImpl();
        setErrorSeverity(logRecord);
        String throwableMessage = throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
        logRecord.setBody(AnyValueFactory.ofString(throwableMessage));
        addThrowableDetails(logRecord, throwable);
        return logRecord;
    }

    private static void setErrorSeverity(final LogRecordImpl logRecord) {
        logRecord.setSeverityNumber(SeverityNumber.ERROR);
        logRecord.setSeverityText(SeverityNumber.ERROR.name());
    }

    private static void addThrowableDetails(final LogRecordImpl logRecord, final Throwable throwable) {
        logRecord.setEventName(OTelTags.EVENT_EXCEPTION.getValue());
        List<KeyValue> attributes = new ArrayList<>(3);
        attributes.add(KeyValueFactory.of(OTelTags.EXCEPTION_TYPE, AnyValueFactory.ofString(throwable.getClass().getName())));
        if (throwable.getMessage() != null) {
            attributes.add(KeyValueFactory.of(OTelTags.EXCEPTION_MESSAGE, AnyValueFactory.ofString(throwable.getMessage())));
        }
        attributes.add(KeyValueFactory.of(OTelTags.EXCEPTION_STACKTRACE, AnyValueFactory.ofString(stackTraceAsString(throwable))));
        logRecord.setAttributes(attributes);
    }

    private static String stackTraceAsString(final Throwable throwable) {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            throwable.printStackTrace(pw);
            return sw.toString();
        }
    }
}
