package com.threeamigos.common.util.implementations.messagehandler.otel.formatters;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
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
    private static final String F_ENTITIES = "entities";
    private static final String F_TYPE = "type";
    private static final String F_ID = "id";
    private static final String F_DESCRIPTION = "description";

    private final RawJsonRecordFormatter rawJsonRecordFormatter = new RawJsonRecordFormatter(false);

    @Nonnull
    @Override
    public String format(@Nonnull final LogRecord logRecord) {
        LogRecord safeLogRecord = logRecord;
        if (safeLogRecord == null) {
            OpenTelemetryAttributeValidator.handleBundled("logRecordMustNotBeNull");
            safeLogRecord = new LogRecordImpl();
        }
        StringBuilder sb = new StringBuilder("{\"").append(F_RESOURCE_LOGS).append("\":[{");
        if (safeLogRecord.getResource() != null) {
            appendResourceBlock(sb, safeLogRecord.getResource());
            sb.append(',');
        }
        sb.append("\"").append(F_SCOPE_LOGS).append("\":[{");
        if (safeLogRecord.getInstrumentationScope() != null) {
            appendScopeBlock(sb, safeLogRecord.getInstrumentationScope());
            sb.append(',');
        }
        sb.append("\"").append(F_LOG_RECORDS).append("\":[");
        sb.append(rawJsonRecordFormatter.format(safeLogRecord));
        sb.append("]}]}]}");
        return sb.toString();
    }

    private static void appendResourceBlock(final StringBuilder sb, final Resource resource) {
        sb.append('"').append(F_RESOURCE).append("\":{");
        boolean resourceFirst = true;
        List<KeyValue> attributes = resource.getAttributes() != null ? resource.getAttributes() : Collections.emptyList();
        if (!attributes.isEmpty()) {
            resourceFirst = false;
            sb.append('"').append(F_ATTRIBUTES).append("\":");
            RawJsonRecordFormatter.appendKeyValueArrayInline(sb, attributes);
        }
        List<Entity> entities = resource.getEntities() != null ? resource.getEntities() : Collections.emptyList();
        if (!entities.isEmpty()) {
            if (!resourceFirst) {
                sb.append(',');
            }
            appendEntities(sb, entities);
        }
        sb.append('}');
        if (resource.getSchemaUrl() != null) {
            sb.append(",\"").append(F_SCHEMA_URL).append("\":\"").append(RawJsonRecordFormatter.escape(resource.getSchemaUrl())).append('"');
        }
    }

    private static void appendEntities(final StringBuilder sb, final List<Entity> entities) {
        sb.append('"').append(F_ENTITIES).append("\":[");
        boolean firstEntity = true;
        for (Entity entity : entities) {
            if (entity == null) {
                continue;
            }
            if (!firstEntity) {
                sb.append(',');
            }
            appendEntity(sb, entity);
            firstEntity = false;
        }
        sb.append(']');
    }

    private static void appendEntity(final StringBuilder sb, final Entity entity) {
        sb.append('{');
        boolean firstField = true;
        if (entity.getType() != null) {
            sb.append('"').append(F_TYPE).append("\":\"")
                    .append(RawJsonRecordFormatter.escape(entity.getType())).append('"');
            firstField = false;
        }
        List<KeyValue> id = entity.getId() != null ? entity.getId() : Collections.emptyList();
        if (!id.isEmpty()) {
            if (!firstField) {
                sb.append(',');
            }
            sb.append('"').append(F_ID).append("\":");
            RawJsonRecordFormatter.appendKeyValueArrayInline(sb, id);
            firstField = false;
        }
        List<KeyValue> description = entity.getDescription() != null
                ? entity.getDescription() : Collections.emptyList();
        if (!description.isEmpty()) {
            if (!firstField) {
                sb.append(',');
            }
            sb.append('"').append(F_DESCRIPTION).append("\":");
            RawJsonRecordFormatter.appendKeyValueArrayInline(sb, description);
            firstField = false;
        }
        if (entity.getSchemaUrl() != null) {
            if (!firstField) {
                sb.append(',');
            }
            sb.append('"').append(F_SCHEMA_URL).append("\":\"")
                    .append(RawJsonRecordFormatter.escape(entity.getSchemaUrl())).append('"');
        }
        sb.append('}');
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
