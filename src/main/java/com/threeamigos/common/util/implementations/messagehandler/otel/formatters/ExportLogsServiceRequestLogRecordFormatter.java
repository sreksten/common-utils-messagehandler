package com.threeamigos.common.util.implementations.messagehandler.otel.formatters;

import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordImpl;
import com.threeamigos.common.util.implementations.messagehandler.otel.OpenTelemetryAttributeValidator;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Entity;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.List;

/**
 * A {@link LogRecordFormatter} that serializes a {@link LogRecord} as the full
 * <a href="https://opentelemetry.io/docs/specs/otlp/#otlphttp-json-encoding">OTLP JSON</a>
 * {@code ExportLogsServiceRequest} envelope.
 * <p>
 * The inner log record object is produced by {@link RawJsonRecordFormatter}.
 * For batch encoding, use {@link #formatBatch(List)}.
 *
 * @author Stefano Reksten
 */
public class ExportLogsServiceRequestLogRecordFormatter implements LogRecordFormatter {

    private static final String F_RESOURCE_LOGS = "resourceLogs";
    private static final String F_RESOURCE = "resource";
    private static final String F_SCHEMA_URL = "schemaUrl";
    private static final String F_SCOPE_LOGS = "scopeLogs";
    private static final String F_SCOPE = "scope";
    private static final String F_LOG_RECORDS = "logRecords";

    private static final String F_ATTRIBUTES = "attributes";
    private static final String F_DROPPED_ATTRIBUTES_COUNT = "droppedAttributesCount";
    private static final String F_NAME = "name";
    private static final String F_VERSION = "version";

    private final RawJsonRecordFormatter rawJsonRecordFormatter = new RawJsonRecordFormatter(false);

    /**
     * Serializes one record as one OTLP ExportLogsServiceRequest envelope.
     *
     * @param logRecord record to serialize
     * @return OTLP ExportLogsServiceRequest JSON containing one log record
     */
    @Nonnull
    @Override
    public String format(@Nonnull final LogRecord logRecord) {
        LogRecord safeLogRecord = logRecord;
        if (safeLogRecord == null) {
            OpenTelemetryAttributeValidator.handleBundled("logRecordMustNotBeNull");
            safeLogRecord = new LogRecordImpl();
        }
        return formatBatch(Collections.singletonList(safeLogRecord));
    }

    /**
     * Serializes multiple log records as a single OTLP ExportLogsServiceRequest envelope.
     * <p>
     * Each input record is encoded as one {@code resourceLogs[]} entry with one
     * {@code scopeLogs[]} child containing one element in {@code logRecords[]}.
     *
     * @param logRecords records to serialize
     * @return OTLP ExportLogsServiceRequest JSON
     */
    @Nonnull
    public String formatBatch(@Nonnull final List<LogRecord> logRecords) {
        if (logRecords == null) {
            OpenTelemetryAttributeValidator.handleBundled("logRecordMustNotBeNull");
            return "{\"" + F_RESOURCE_LOGS + "\":[]}";
        }
        StringBuilder sb = new StringBuilder(96 + (logRecords.size() * 256));
        sb.append("{\"").append(F_RESOURCE_LOGS).append("\":[");
        boolean firstResourceLog = true;
        for (LogRecord logRecord : logRecords) {
            LogRecord safeLogRecord = logRecord;
            if (safeLogRecord == null) {
                OpenTelemetryAttributeValidator.handleBundled("logRecordMustNotBeNull");
                safeLogRecord = new LogRecordImpl();
            }
            if (!firstResourceLog) {
                sb.append(',');
            }
            appendResourceLogEntry(sb, safeLogRecord);
            firstResourceLog = false;
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * Appends one {@code resourceLogs[]} element for the provided record.
     *
     * @param sb target JSON builder
     * @param logRecord record to append
     */
    private void appendResourceLogEntry(final StringBuilder sb, final LogRecord logRecord) {
        sb.append('{');
        if (logRecord.getResource() != null) {
            appendResourceBlock(sb, logRecord.getResource());
            sb.append(',');
        }
        sb.append("\"").append(F_SCOPE_LOGS).append("\":[{");
        if (logRecord.getInstrumentationScope() != null) {
            appendScopeBlock(sb, logRecord.getInstrumentationScope());
            sb.append(',');
        }
        sb.append("\"").append(F_LOG_RECORDS).append("\":[");
        sb.append(rawJsonRecordFormatter.format(logRecord));
        sb.append("]}]}");
    }

    private static void appendResourceBlock(final StringBuilder sb, final Resource resource) {
        sb.append('"').append(F_RESOURCE).append("\":{");
        boolean resourceFirst = true;
        List<KeyValue> attributes = RawJsonRecordFormatter.mergedResourceAttributes(resource);
        if (!attributes.isEmpty()) {
            resourceFirst = false;
            sb.append('"').append(F_ATTRIBUTES).append("\":");
            RawJsonRecordFormatter.appendKeyValueArrayInline(sb, attributes);
        }
        List<Entity> entities = resource.getEntities() != null ? resource.getEntities() : Collections.<Entity>emptyList();
        if (!entities.isEmpty()) {
            if (!resourceFirst) {
                sb.append(',');
            }
            appendEntityRefs(sb, entities);
        }
        sb.append('}');
        if (resource.getSchemaUrl() != null) {
            sb.append(",\"").append(F_SCHEMA_URL).append("\":\"").append(RawJsonRecordFormatter.escape(resource.getSchemaUrl())).append('"');
        }
    }

    private static void appendEntityRefs(final StringBuilder sb,
                                         final List<Entity> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        RawJsonRecordFormatter.appendEntityRefsInline(sb, entities);
    }

    private static void appendScopeBlock(final StringBuilder sb, final InstrumentationScope scope) {
        sb.append('"').append(F_SCOPE).append("\":{");
        boolean scopeFirst = true;
        if (scope.getName() != null) {
            sb.append('"').append(F_NAME).append("\":\"").append(RawJsonRecordFormatter.escape(scope.getName())).append('"');
            scopeFirst = false;
        }
        if (scope.getVersion() != null) {
            if (!scopeFirst) {
                sb.append(',');
            }
            sb.append('"').append(F_VERSION).append("\":\"").append(RawJsonRecordFormatter.escape(scope.getVersion())).append('"');
            scopeFirst = false;
        }
        List<KeyValue> attributes = scope.getAttributes() != null ? scope.getAttributes() : Collections.emptyList();
        if (!attributes.isEmpty()) {
            if (!scopeFirst) {
                sb.append(',');
            }
            sb.append('"').append(F_ATTRIBUTES).append("\":");
            RawJsonRecordFormatter.appendKeyValueArrayInline(sb, attributes);
            scopeFirst = false;
        }
        int droppedAttributesCount = scope.getDroppedAttributesCount();
        if (droppedAttributesCount != 0) {
            if (!scopeFirst) {
                sb.append(',');
            }
            sb.append('"').append(F_DROPPED_ATTRIBUTES_COUNT).append("\":").append(droppedAttributesCount);
        }
        sb.append('}');
        if (scope.getSchemaUrl() != null) {
            sb.append(",\"").append(F_SCHEMA_URL).append("\":\"").append(RawJsonRecordFormatter.escape(scope.getSchemaUrl())).append('"');
        }
    }
}
