package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;

/**
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
        LogRecordImpl logRecord = new LogRecordImpl();
        logRecord.setSeverityNumber(severityNumber);
        logRecord.setSeverityText(severityNumber.name());
        logRecord.setBody(AnyValueImpl.ofString(message));
        return logRecord;
    }

    @Override
    public LogRecord create(String message, Throwable throwable) {
        LogRecordImpl logRecord = new LogRecordImpl();
        logRecord.setSeverityNumber(SeverityNumber.ERROR);
        logRecord.setSeverityText(SeverityNumber.ERROR.name());
        logRecord.setBody(AnyValueImpl.ofString(message));
        return logRecord;
    }

    @Override
    public LogRecord create(Throwable throwable) {
        LogRecordImpl logRecord = new LogRecordImpl();
        logRecord.setSeverityNumber(SeverityNumber.ERROR);
        logRecord.setSeverityText(SeverityNumber.ERROR.name());
        logRecord.setBody(AnyValueImpl.ofString(throwable.getMessage()));
        return logRecord;
    }
}
