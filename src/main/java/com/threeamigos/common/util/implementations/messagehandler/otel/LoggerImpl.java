package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.Context;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Logger;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;

/**
 * Logger implementation returned by {@link LoggerProviderImpl}.
 * <p>
 * Package-private on purpose: callers should get loggers only via {@link LoggerProviderImpl}.
 *
 * @author Stefano Reksten
 */
final class LoggerImpl implements Logger {

    private final LoggerProviderImpl owner;
    private final InstrumentationScope instrumentationScope;

    LoggerImpl(final LoggerProviderImpl owner,
               final InstrumentationScope instrumentationScope) {
        this.owner = owner;
        this.instrumentationScope = instrumentationScope;
    }

    @Override
    public void emit(final LogRecord logRecord) {
        if (logRecord == null) {
            OpenTelemetryAttributeValidator.handleBundled("logRecordMustNotBeNull");
            return;
        }
        if (!isEnabled(null, logRecord.getSeverityNumber(), logRecord.getEventName())) {
            return;
        }
        LogRecord enrichedRecord = owner.enrichForLogger(instrumentationScope, logRecord);
        owner.emit(enrichedRecord);
    }

    @Override
    public boolean isEnabled(final Context context,
                             final SeverityNumber severityNumber,
                             final String eventName) {
        return owner.isEnabled();
    }
}
