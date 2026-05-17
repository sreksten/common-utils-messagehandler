package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ConsoleLogRecordFormatter;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ExportLogsServiceRequestLogRecordFormatter;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.RawJsonRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Entity;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Formatter coverage tests")
@Tag("unit")
@Tag("messageHandler")
class FormattersCoverageUnitTest {

    @AfterEach
    void cleanupLenient() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(false);
    }

    @Test
    @DisplayName("console formatter should cover null and runtime-exception branches in lenient mode")
    void consoleFormatterShouldCoverNullAndRuntimeBranchesInLenientMode() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        ConsoleLogRecordFormatter formatter = new ConsoleLogRecordFormatter();

        assertEquals("", formatter.format(null));

        LogRecord throwingRecord = new ThrowingLogRecord();
        assertEquals("", formatter.format(throwingRecord));
    }

    @Test
    @DisplayName("console formatter should cover trace/span and any-value helper branches")
    void consoleFormatterShouldCoverTraceSpanAndAnyValueHelperBranches() throws Exception {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        ConsoleLogRecordFormatter formatter = new ConsoleLogRecordFormatter();

        LogRecordImpl spanOnly = new LogRecordImpl();
        spanOnly.setTimestamp(Instant.parse("2026-05-01T10:00:00Z"));
        spanOnly.setSeverityText("INFO");
        spanOnly.setSpanId("89abcdef01234567");
        spanOnly.setBody(AnyValueFactory.ofString("x"));
        assertTrue(formatter.format(spanOnly).contains("[span_id=89abcdef01234567 trace_flags=00]"));

        LogRecordImpl traceOnly = new LogRecordImpl();
        traceOnly.setTimestamp(Instant.parse("2026-05-01T10:00:00Z"));
        traceOnly.setSeverityText("INFO");
        traceOnly.setTraceId("0123456789abcdef0123456789abcdef");
        traceOnly.setBody(AnyValueFactory.ofString("x"));
        assertTrue(formatter.format(traceOnly).contains("[trace_id=0123456789abcdef0123456789abcdef trace_flags=00]"));

        Method anyValueToString = ConsoleLogRecordFormatter.class
                .getDeclaredMethod("anyValueToString", AnyValue.class);
        anyValueToString.setAccessible(true);
        assertEquals("", anyValueToString.invoke(null, new Object[]{null}));

        AnyValue nullTypeValue = new AnyValue() {
            @Override
            public Type getType() {
                return null;
            }

            @Override
            public String asString() { return null; }

            @Override
            public boolean asBoolean() { return false; }

            @Override
            public long asLong() { return 0; }

            @Override
            public double asDouble() { return 0; }

            @Override
            public List<AnyValue> asArray() { return Collections.emptyList(); }

            @Override
            public List<KeyValue> asKvList() { return Collections.emptyList(); }

            @Override
            public byte[] asBytes() { return new byte[0]; }
        };
        assertEquals("", anyValueToString.invoke(null, nullTypeValue));
    }

    @Test
    @DisplayName("console formatter should cover null timestamp and blank trace token branches")
    void consoleFormatterShouldCoverNullTimestampAndBlankTraceTokenBranches() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        ConsoleLogRecordFormatter formatter = new ConsoleLogRecordFormatter();

        LogRecord nullTimestampRecord = stubLogRecordWithTimestamp(null, null);
        String out = formatter.format(nullTimestampRecord);

        assertTrue(out.contains("[INFO  ]"));
        assertTrue(out.endsWith(" x"));
        assertTrue(!out.contains("[trace_id="));
        assertTrue(!out.contains("[span_id="));
    }

    @Test
    @DisplayName("console formatter should cover normalizeOptionalToken blank branch")
    void consoleFormatterShouldCoverNormalizeOptionalTokenBlankBranch() throws Exception {
        Method normalizeOptionalToken = ConsoleLogRecordFormatter.class
                .getDeclaredMethod("normalizeOptionalToken", String.class);
        normalizeOptionalToken.setAccessible(true);
        assertEquals(null, normalizeOptionalToken.invoke(null, "   "));
    }

    @Test
    @DisplayName("raw formatter should cover null/runtime and private helper branches")
    void rawFormatterShouldCoverNullRuntimeAndPrivateHelperBranches() throws Exception {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        RawJsonRecordFormatter formatter = new RawJsonRecordFormatter();

        assertEquals("{}", formatter.format(null));
        assertEquals("{}", formatter.format(new ThrowingLogRecord()));

        Method escape = RawJsonRecordFormatter.class.getDeclaredMethod("escape", String.class);
        escape.setAccessible(true);
        assertEquals("", escape.invoke(null, new Object[]{null}));

        Method appendKeyValueArrayInline = RawJsonRecordFormatter.class
                .getDeclaredMethod("appendKeyValueArrayInline", StringBuilder.class, List.class);
        appendKeyValueArrayInline.setAccessible(true);
        StringBuilder sbNullList = new StringBuilder();
        appendKeyValueArrayInline.invoke(null, sbNullList, null);
        assertEquals("[]", sbNullList.toString());

        List<KeyValue> keyValues = new ArrayList<KeyValue>();
        keyValues.add(null);
        keyValues.add(new KeyValue() {
            @Override
            public String getKey() {
                return null;
            }

            @Override
            public AnyValue getValue() {
                return AnyValueFactory.ofString("v");
            }
        });
        keyValues.add(new KeyValue() {
            @Override
            public String getKey() {
                return "k";
            }

            @Override
            public AnyValue getValue() {
                return null;
            }
        });
        StringBuilder sbMixed = new StringBuilder();
        appendKeyValueArrayInline.invoke(null, sbMixed, keyValues);
        assertTrue(sbMixed.toString().contains("\"key\":\"unknown\""));
        assertTrue(sbMixed.toString().contains("\"key\":\"k\""));

        Method appendAnyValue = RawJsonRecordFormatter.class
                .getDeclaredMethod("appendAnyValue", StringBuilder.class, AnyValue.class);
        appendAnyValue.setAccessible(true);
        StringBuilder nullAny = new StringBuilder();
        appendAnyValue.invoke(null, nullAny, null);
        assertEquals("{}", nullAny.toString());

        AnyValue nullType = new AnyValue() {
            @Override
            public Type getType() { return null; }
            @Override
            public String asString() { return null; }
            @Override
            public boolean asBoolean() { return false; }
            @Override
            public long asLong() { return 0; }
            @Override
            public double asDouble() { return 0; }
            @Override
            public List<AnyValue> asArray() { return Collections.emptyList(); }
            @Override
            public List<KeyValue> asKvList() { return Collections.emptyList(); }
            @Override
            public byte[] asBytes() { return new byte[0]; }
        };
        StringBuilder nullTypeAny = new StringBuilder();
        appendAnyValue.invoke(null, nullTypeAny, nullType);
        assertEquals("{}", nullTypeAny.toString());

        LogRecord customResourceRecord = new StubLogRecord(
                ResourceFactory.create(
                        "https://schema",
                        Collections.<Entity>singletonList(null),
                        null),
                null);
        String withResource = formatter.format(customResourceRecord);
        assertTrue(withResource.contains("\"resource\":{"));

        RawJsonRecordFormatter noResourceFormatter = new RawJsonRecordFormatter(false);
        String noResource = noResourceFormatter.format(customResourceRecord);
        assertTrue(!noResource.contains("\"resource\":{"));
    }

    @Test
    @DisplayName("raw formatter should cover timestamp bounds and entity serialization branches")
    void rawFormatterShouldCoverTimestampBoundsAndEntitySerializationBranches() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        RawJsonRecordFormatter formatter = new RawJsonRecordFormatter(true);

        LogRecord negativeTimestampRecord = stubLogRecordWithTimestamp(resourceWithEntitiesAndAttributes(), Instant.ofEpochSecond(-1L));
        String negativeOut = formatter.format(negativeTimestampRecord);
        assertTrue(!negativeOut.contains("\"timeUnixNano\""));
        assertTrue(negativeOut.contains("\"resource\":{"));

        LogRecord overUint64TimestampRecord = stubLogRecordWithTimestamp(
                resourceWithEntitiesAndAttributes(),
                Instant.ofEpochSecond(18446744074L));
        String overflowOut = formatter.format(overUint64TimestampRecord);
        assertTrue(!overflowOut.contains("\"timeUnixNano\""));
        assertTrue(overflowOut.contains("\"entityRefs\":["));
        assertTrue(overflowOut.contains("\"type\":\"service\""));
        assertTrue(overflowOut.contains("\"descriptionKeys\":"));
        assertTrue(overflowOut.contains("\"schemaUrl\":\"https://entity.schema\""));
    }

    @Test
    @DisplayName("raw formatter should cover remaining resource and entity helper branches")
    void rawFormatterShouldCoverRemainingResourceAndEntityHelperBranches() throws Exception {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        RawJsonRecordFormatter formatter = new RawJsonRecordFormatter(true);

        String withEmptyAttrs = formatter.format(stubLogRecordWithTimestamp(resourceWithEmptyAttributesAndNonEmptyEntities(), Instant.now()));
        assertTrue(withEmptyAttrs.contains("\"resource\":{"));
        assertTrue(withEmptyAttrs.contains("\"entityRefs\":["));

        String withEmptyEntities = formatter.format(stubLogRecordWithTimestamp(resourceWithNullAttributesAndEmptyEntities(), Instant.now()));
        assertTrue(withEmptyEntities.contains("\"resource\":{"));

        String withNullEntities = formatter.format(stubLogRecordWithTimestamp(resourceWithNullEntities(), Instant.now()));
        assertTrue(withNullEntities.contains("\"resource\":{}"));

        Method appendEntityRef = RawJsonRecordFormatter.class
                .getDeclaredMethod("appendEntityRef", StringBuilder.class, Entity.class);
        appendEntityRef.setAccessible(true);

        StringBuilder idOnly = new StringBuilder();
        appendEntityRef.invoke(null, idOnly, entityWith(null, "https://id.only", singletonKeyValue("idOnly", "v"), null));
        assertTrue(idOnly.toString().contains("\"idKeys\":"));

        StringBuilder descOnly = new StringBuilder();
        appendEntityRef.invoke(null, descOnly, entityWith(null, null, null, singletonKeyValue("descOnly", "v")));
        assertTrue(descOnly.toString().contains("\"descriptionKeys\":"));

        StringBuilder schemaOnly = new StringBuilder();
        appendEntityRef.invoke(null, schemaOnly, entityWith(null, "https://schema.only", null, null));
        assertTrue(schemaOnly.toString().contains("\"schemaUrl\":\"https://schema.only\""));
    }

    @Test
    @DisplayName("export formatter should cover null and entity/resource edge branches in lenient mode")
    void exportFormatterShouldCoverNullAndEdgeBranchesInLenientMode() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        ExportLogsServiceRequestLogRecordFormatter formatter = new ExportLogsServiceRequestLogRecordFormatter();

        String fromNull = formatter.format(null);
        assertTrue(fromNull.contains("\"resourceLogs\""));

        Resource customResource = new Resource() {
            @Override
            public List<Entity> getEntities() {
                List<Entity> entities = new ArrayList<Entity>();
                entities.add(null);
                entities.add(new Entity() {
                    @Override
                    public String getType() { return null; }
                    @Override
                    public String getSchemaUrl() { return null; }
                    @Override
                    public List<KeyValue> getId() { return null; }
                    @Override
                    public List<KeyValue> getDescription() { return null; }
                    @Override
                    public Entity merge(final Entity other) { return this; }
                });
                return entities;
            }

            @Override
            public String getSchemaUrl() { return null; }

            @Override
            public List<KeyValue> getAttributes() { return null; }

            @Override
            public Resource merge(final Resource other) { return this; }
        };

        InstrumentationScope customScope = new InstrumentationScope() {
            @Override
            public String getName() { return null; }
            @Override
            public String getVersion() { return null; }
            @Override
            public String getSchemaUrl() { return "https://scope.schema"; }
            @Override
            public List<KeyValue> getAttributes() { return null; }
            @Override
            public int getDroppedAttributesCount() { return 0; }
        };

        String out = formatter.format(new StubLogRecord(customResource, customScope));
        assertTrue(out.contains("\"resource\":{"));
        assertTrue(out.contains("\"entityRefs\":[{}]"));
        assertTrue(out.contains("\"scope\":{}"));
        assertTrue(out.contains("\"schemaUrl\":\"https://scope.schema\""));
    }

    @Test
    @DisplayName("export formatter should cover resource and entity comma/field branches")
    void exportFormatterShouldCoverResourceAndEntityCommaFieldBranches() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        ExportLogsServiceRequestLogRecordFormatter formatter = new ExportLogsServiceRequestLogRecordFormatter();

        String out = formatter.format(new StubLogRecord(resourceWithEntitiesAndAttributes(), null));
        assertTrue(out.contains("\"resource\":{\"attributes\":"));
        assertTrue(out.contains("\"entityRefs\":["));
        assertTrue(out.contains("},{"));
        assertTrue(out.contains("\"idKeys\":"));
        assertTrue(out.contains("\"descriptionKeys\":"));
        assertTrue(out.contains("\"schemaUrl\":\"https://entity.schema\""));
    }

    @Test
    @DisplayName("export formatter should cover null entities and firstField false branches")
    void exportFormatterShouldCoverNullEntitiesAndFirstFieldFalseBranches() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        ExportLogsServiceRequestLogRecordFormatter formatter = new ExportLogsServiceRequestLogRecordFormatter();

        String nullEntitiesOut = formatter.format(new StubLogRecord(resourceWithNullEntities(), null));
        assertTrue(nullEntitiesOut.contains("\"resource\":{}"));

        String firstFieldFalseOut = formatter.format(new StubLogRecord(resourceForExportFirstFieldFalse(), null));
        assertTrue(firstFieldFalseOut.contains("\"idKeys\":"));
        assertTrue(firstFieldFalseOut.contains("\"descriptionKeys\":"));
        assertTrue(firstFieldFalseOut.contains("\"schemaUrl\":\"https://schema.export\""));
    }

    private static Resource resourceWithEntitiesAndAttributes() {
        return new Resource() {
            @Override
            public List<Entity> getEntities() {
                List<Entity> entities = new ArrayList<Entity>();
                entities.add(null);
                entities.add(new Entity() {
                    @Override
                    public String getType() {
                        return null;
                    }

                    @Override
                    public String getSchemaUrl() {
                        return null;
                    }

                    @Override
                    public List<KeyValue> getId() {
                        return Collections.<KeyValue>emptyList();
                    }

                    @Override
                    public List<KeyValue> getDescription() {
                        return Collections.<KeyValue>emptyList();
                    }

                    @Override
                    public Entity merge(final Entity other) {
                        return this;
                    }
                });
                entities.add(new Entity() {
                    @Override
                    public String getType() {
                        return "service";
                    }

                    @Override
                    public String getSchemaUrl() {
                        return "https://entity.schema";
                    }

                    @Override
                    public List<KeyValue> getId() {
                        return Collections.<KeyValue>singletonList(new KeyValue() {
                            @Override
                            public String getKey() {
                                return "id.k";
                            }

                            @Override
                            public AnyValue getValue() {
                                return AnyValueFactory.ofString("id.v");
                            }
                        });
                    }

                    @Override
                    public List<KeyValue> getDescription() {
                        return Collections.<KeyValue>singletonList(new KeyValue() {
                            @Override
                            public String getKey() {
                                return "desc.k";
                            }

                            @Override
                            public AnyValue getValue() {
                                return AnyValueFactory.ofString("desc.v");
                            }
                        });
                    }

                    @Override
                    public Entity merge(final Entity other) {
                        return this;
                    }
                });
                return entities;
            }

            @Override
            public String getSchemaUrl() {
                return "https://resource.schema";
            }

            @Override
            public List<KeyValue> getAttributes() {
                return Collections.<KeyValue>singletonList(new KeyValue() {
                    @Override
                    public String getKey() {
                        return "res.k";
                    }

                    @Override
                    public AnyValue getValue() {
                        return AnyValueFactory.ofString("res.v");
                    }
                });
            }

            @Override
            public Resource merge(final Resource other) {
                return this;
            }
        };
    }

    private static Resource resourceWithEmptyAttributesAndNonEmptyEntities() {
        return new Resource() {
            @Override
            public List<Entity> getEntities() {
                return Collections.<Entity>singletonList(entityWith("type", null, null, null));
            }

            @Override
            public String getSchemaUrl() {
                return null;
            }

            @Override
            public List<KeyValue> getAttributes() {
                return Collections.<KeyValue>emptyList();
            }

            @Override
            public Resource merge(final Resource other) {
                return this;
            }
        };
    }

    private static Resource resourceWithNullAttributesAndEmptyEntities() {
        return new Resource() {
            @Override
            public List<Entity> getEntities() {
                return Collections.<Entity>emptyList();
            }

            @Override
            public String getSchemaUrl() {
                return null;
            }

            @Override
            public List<KeyValue> getAttributes() {
                return null;
            }

            @Override
            public Resource merge(final Resource other) {
                return this;
            }
        };
    }

    private static Resource resourceWithNullEntities() {
        return new Resource() {
            @Override
            public List<Entity> getEntities() {
                return null;
            }

            @Override
            public String getSchemaUrl() {
                return null;
            }

            @Override
            public List<KeyValue> getAttributes() {
                return null;
            }

            @Override
            public Resource merge(final Resource other) {
                return this;
            }
        };
    }

    private static Resource resourceForExportFirstFieldFalse() {
        return new Resource() {
            @Override
            public List<Entity> getEntities() {
                List<Entity> entities = new ArrayList<Entity>();
                entities.add(entityWith(null, null, singletonKeyValue("id.k", "id.v"), null));
                entities.add(entityWith(null, null, null, singletonKeyValue("desc.k", "desc.v")));
                entities.add(entityWith(null, "https://schema.export", null, null));
                return entities;
            }

            @Override
            public String getSchemaUrl() {
                return null;
            }

            @Override
            public List<KeyValue> getAttributes() {
                return null;
            }

            @Override
            public Resource merge(final Resource other) {
                return this;
            }
        };
    }

    private static List<KeyValue> singletonKeyValue(final String key, final String value) {
        return Collections.<KeyValue>singletonList(new KeyValue() {
            @Override
            public String getKey() {
                return key;
            }

            @Override
            public AnyValue getValue() {
                return AnyValueFactory.ofString(value);
            }
        });
    }

    private static Entity entityWith(final String type,
                                     final String schemaUrl,
                                     final List<KeyValue> id,
                                     final List<KeyValue> description) {
        return new Entity() {
            @Override
            public String getType() {
                return type;
            }

            @Override
            public String getSchemaUrl() {
                return schemaUrl;
            }

            @Override
            public List<KeyValue> getId() {
                return id;
            }

            @Override
            public List<KeyValue> getDescription() {
                return description;
            }

            @Override
            public Entity merge(final Entity other) {
                return this;
            }
        };
    }

    private static LogRecord stubLogRecordWithTimestamp(final Resource resource, final Instant timestamp) {
        return new LogRecord() {
            @Override
            public Instant getTimestamp() {
                return timestamp;
            }

            @Override
            public Instant getObservedTimestamp() {
                return null;
            }

            @Override
            public String getTraceId() {
                return null;
            }

            @Override
            public String getSpanId() {
                return null;
            }

            @Override
            public int getTraceFlags() {
                return 0;
            }

            @Override
            public String getSeverityText() {
                return "INFO";
            }

            @Override
            public SeverityNumber getSeverityNumber() {
                return SeverityNumber.INFO;
            }

            @Override
            public AnyValue getBody() {
                return AnyValueFactory.ofString("x");
            }

            @Override
            public Resource getResource() {
                return resource;
            }

            @Override
            public InstrumentationScope getInstrumentationScope() {
                return null;
            }

            @Override
            public List<KeyValue> getAttributes() {
                return Collections.emptyList();
            }

            @Override
            public String getEventName() {
                return null;
            }
        };
    }

    private static final class ThrowingLogRecord implements LogRecord {
        @Override
        public Instant getTimestamp() {
            throw new RuntimeException("boom");
        }
        @Override
        public Instant getObservedTimestamp() { return null; }
        @Override
        public String getTraceId() { return null; }
        @Override
        public String getSpanId() { return null; }
        @Override
        public int getTraceFlags() { return 0; }
        @Override
        public String getSeverityText() { return null; }
        @Override
        public SeverityNumber getSeverityNumber() { return null; }
        @Override
        public AnyValue getBody() { return null; }
        @Override
        public Resource getResource() { return null; }
        @Override
        public InstrumentationScope getInstrumentationScope() { return null; }
        @Override
        public List<KeyValue> getAttributes() { return Collections.emptyList(); }
        @Override
        public String getEventName() { return null; }
    }

    private static final class StubLogRecord implements LogRecord {
        private final Resource resource;
        private final InstrumentationScope scope;

        private StubLogRecord(final Resource resource, final InstrumentationScope scope) {
            this.resource = resource;
            this.scope = scope;
        }

        @Override
        public Instant getTimestamp() { return Instant.parse("2026-05-01T10:10:10Z"); }
        @Override
        public Instant getObservedTimestamp() { return null; }
        @Override
        public String getTraceId() { return null; }
        @Override
        public String getSpanId() { return null; }
        @Override
        public int getTraceFlags() { return 0; }
        @Override
        public String getSeverityText() { return "INFO"; }
        @Override
        public SeverityNumber getSeverityNumber() { return SeverityNumber.INFO; }
        @Override
        public AnyValue getBody() { return AnyValueFactory.ofString("x"); }
        @Override
        public Resource getResource() { return resource; }
        @Override
        public InstrumentationScope getInstrumentationScope() { return scope; }
        @Override
        public List<KeyValue> getAttributes() { return Collections.emptyList(); }
        @Override
        public String getEventName() { return null; }
    }
}
