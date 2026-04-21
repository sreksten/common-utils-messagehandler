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
import java.util.Objects;

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
        Objects.requireNonNull(severityNumber, MessageHandlerResourceBundle.get("valueMustNotBeNull"));
        Objects.requireNonNull(message, MessageHandlerResourceBundle.get("nullMessageProvided"));
        LogRecordImpl logRecord = new LogRecordImpl();
        logRecord.setSeverityNumber(severityNumber);
        logRecord.setSeverityText(severityNumber.name());
        logRecord.setBody(AnyValueImpl.ofString(message));
        return logRecord;
    }

    @Override
    public LogRecord create(String message, Throwable throwable) {
        Objects.requireNonNull(message, MessageHandlerResourceBundle.get("nullMessageProvided"));
        Objects.requireNonNull(throwable, MessageHandlerResourceBundle.get("nullExceptionProvided"));
        LogRecordImpl logRecord = new LogRecordImpl();
        setErrorSeverity(logRecord);
        logRecord.setBody(AnyValueImpl.ofString(message));
        addThrowableDetails(logRecord, throwable);
        return logRecord;
    }

    @Override
    public LogRecord create(Throwable throwable) {
        Objects.requireNonNull(throwable, MessageHandlerResourceBundle.get("nullExceptionProvided"));
        LogRecordImpl logRecord = new LogRecordImpl();
        setErrorSeverity(logRecord);
        String throwableMessage = throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
        logRecord.setBody(AnyValueImpl.ofString(throwableMessage));
        addThrowableDetails(logRecord, throwable);
        return logRecord;
    }

    private static void setErrorSeverity(final LogRecordImpl logRecord) {
        logRecord.setSeverityNumber(SeverityNumber.ERROR);
        logRecord.setSeverityText(SeverityNumber.ERROR.name());
    }

    private static void addThrowableDetails(final LogRecordImpl logRecord, final Throwable throwable) {
        logRecord.setEventName(Names.EVENT_EXCEPTION.getValue());
        List<KeyValue> attributes = new ArrayList<>(3);
        attributes.add(KeyValueImpl.of(Names.ATTR_EXCEPTION_TYPE, AnyValueImpl.ofString(throwable.getClass().getName())));
        if (throwable.getMessage() != null) {
            attributes.add(KeyValueImpl.of(Names.ATTR_EXCEPTION_MESSAGE, AnyValueImpl.ofString(throwable.getMessage())));
        }
        attributes.add(KeyValueImpl.of(Names.ATTR_EXCEPTION_STACKTRACE, AnyValueImpl.ofString(stackTraceAsString(throwable))));
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
