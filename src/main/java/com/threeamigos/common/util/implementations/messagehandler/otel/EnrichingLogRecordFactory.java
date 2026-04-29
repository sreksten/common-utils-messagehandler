package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.*;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Stefano Reksten
 */
public class EnrichingLogRecordFactory implements LogRecordFactory {
    private final LogRecordFactory delegate;
    private final Resource resource;
    private final InstrumentationScope scope;
    private final List<KeyValue> commonAttributes;

    public EnrichingLogRecordFactory(
            LogRecordFactory delegate,
            Resource resource,
            InstrumentationScope scope,
            List<KeyValue> commonAttributes) {
        this.delegate = delegate;
        this.resource = resource;
        this.scope = scope;
        this.commonAttributes = commonAttributes;
    }

    @Override
    public LogRecord create() {
        return enrich(delegate.create());
    }

    @Override
    public LogRecord create(SeverityNumber severityNumber, String message) {
        return enrich(delegate.create(severityNumber, message));
    }

    @Override
    public LogRecord create(String message, Throwable throwable) {
        return enrich(delegate.create(message, throwable));
    }

    @Override
    public LogRecord create(Throwable throwable) {
        return enrich(delegate.create(throwable));
    }

    private LogRecord enrich(LogRecord record) {
        if (!(record instanceof LogRecordImpl)) {
            return record;
        }
        LogRecordImpl mutable = (LogRecordImpl) record;
        if (resource != null) {
            mutable.setResource(resource);
        }
        if (scope != null) {
            mutable.setInstrumentationScope(scope);
        }

        if (commonAttributes != null && !commonAttributes.isEmpty()) {
            List<KeyValue> merged = new ArrayList<>(mutable.getAttributes());
            merged.addAll(commonAttributes);
            mutable.setAttributes(merged);
        }
        return mutable;
    }
}