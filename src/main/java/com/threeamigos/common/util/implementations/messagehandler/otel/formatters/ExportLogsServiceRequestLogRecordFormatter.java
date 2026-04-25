package com.threeamigos.common.util.implementations.messagehandler.otel.formatters;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import jakarta.annotation.Nonnull;

import java.util.Objects;

/**
 * A {@link LogRecordFormatter} that serializes a {@link LogRecord} as the full
 * <a href="https://opentelemetry.io/docs/specs/otlp/#otlphttp-json-encoding">OTLP JSON</a>
 * {@code ExportLogsServiceRequest} envelope.
 * <p>
 * The inner log record object is produced by {@link RawJsonRecordFormatter}.
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

    private final RawJsonRecordFormatter rawJsonRecordFormatter = new RawJsonRecordFormatter();

    @Nonnull
    @Override
    public String format(@Nonnull final LogRecord logRecord) {
        Objects.requireNonNull(logRecord, MessageHandlerResourceBundle.get("logRecordMustNotBeNull"));
        StringBuilder sb = new StringBuilder("{\"").append(F_RESOURCE_LOGS).append("\":[{");
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
        sb.append("]}]}]}");
        return sb.toString();
    }

    private static void appendResourceBlock(final StringBuilder sb, final Resource resource) {
        sb.append('"').append(F_RESOURCE).append("\":{");
        if (!resource.getAttributes().isEmpty()) {
            sb.append('"').append(F_ATTRIBUTES).append("\":");
            RawJsonRecordFormatter.appendKeyValueArrayInline(sb, resource.getAttributes());
        }
        sb.append('}');
        if (resource.getSchemaUrl() != null) {
            sb.append(",\"").append(F_SCHEMA_URL).append("\":\"").append(RawJsonRecordFormatter.escape(resource.getSchemaUrl())).append('"');
        }
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
        if (!scope.getAttributes().isEmpty()) {
            if (!scopeFirst) {
                sb.append(',');
            }
            sb.append('"').append(F_ATTRIBUTES).append("\":");
            RawJsonRecordFormatter.appendKeyValueArrayInline(sb, scope.getAttributes());
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
