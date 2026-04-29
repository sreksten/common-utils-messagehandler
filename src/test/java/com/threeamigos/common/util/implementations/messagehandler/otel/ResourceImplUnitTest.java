package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Entity;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ResourceFactory unit tests")
@Tag("unit")
@Tag("messageHandler")
class ResourceFactoryUnitTest {

    private static final boolean ORIGINAL_LENIENT = OpenTelemetryAttributeValidator.isLenientMode();

    @BeforeEach
    void enforceLenientModeForResourceNormalizationTests() {
        setLenient(true);
    }

    @AfterEach
    void restoreLenientMode() {
        setLenient(ORIGINAL_LENIENT);
    }

    @Test
    @DisplayName("create() should support null schemaUrl and null attributes")
    void createShouldSupportNullSchemaUrlAndNullAttributes() {
        Resource resource = ResourceFactory.create(null, null, null);
        assertNull(resource.getSchemaUrl());
        assertTrue(resource.getEntities().isEmpty());
        assertTrue(resource.getAttributes().isEmpty());
    }

    @Test
    @DisplayName("create() should store schemaUrl value")
    void createShouldStoreSchemaUrlValue() {
        Resource resource = ResourceFactory.create("https://opentelemetry.io/schemas/1.26.0", null, null);
        assertEquals("https://opentelemetry.io/schemas/1.26.0", resource.getSchemaUrl());
    }

    @Test
    @DisplayName("create(schemaUrl, entities, attributes) should expose unmodifiable entities and derive schemaUrl from entities")
    void createWithEntitiesShouldExposeUnmodifiableEntitiesAndDeriveSchemaUrl() {
        List<Entity> entities = new ArrayList<>(Collections.singletonList(
                EntityFactory.create(
                        "service",
                        "https://opentelemetry.io/schemas/1.27.0",
                        Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("i-1"))),
                        Collections.singletonList(new KeyValueImpl("service.name", AnyValueFactory.ofString("billing")))
                )));
        List<KeyValue> attributes = new ArrayList<>(Collections.singletonList(
                new KeyValueImpl("deployment.environment", AnyValueFactory.ofString("dev"))));

        Resource resource = ResourceFactory.create("https://ignored.example/schema", entities, attributes);

        assertEquals("https://opentelemetry.io/schemas/1.27.0", resource.getSchemaUrl());
        assertEquals(1, resource.getEntities().size());
        assertThrows(UnsupportedOperationException.class, () ->
                resource.getEntities().add(EntityFactory.create(
                        "host",
                        null,
                        Collections.singletonList(new KeyValueImpl("host.id", AnyValueFactory.ofString("h-1"))),
                        null)));

        entities.add(EntityFactory.create(
                "host",
                "https://opentelemetry.io/schemas/1.27.0",
                Collections.singletonList(new KeyValueImpl("host.id", AnyValueFactory.ofString("h-1"))),
                null));
        attributes.add(new KeyValueImpl("host.name", AnyValueFactory.ofString("host-a")));
        assertEquals(1, resource.getEntities().size());
        assertEquals(1, resource.getAttributes().size());
    }

    @Test
    @DisplayName("create() should copy and expose unmodifiable attributes")
    void createShouldCopyAndExposeUnmodifiableAttributes() {
        List<KeyValue> attrs = new ArrayList<>(Collections.singletonList(
                new KeyValueImpl("service.name", AnyValueFactory.ofString("svc"))
        ));

        Resource resource = ResourceFactory.create(null, null, attrs);
        assertEquals(1, resource.getAttributes().size());
        assertThrows(UnsupportedOperationException.class,
                () -> resource.getAttributes().add(new KeyValueImpl("x", AnyValueFactory.ofString("y"))));

        attrs.add(new KeyValueImpl("service.version", AnyValueFactory.ofString("1.0")));
        assertEquals(1, resource.getAttributes().size());
    }

    @Test
    @DisplayName("create() should accept null attributes as empty list")
    void createShouldAcceptNullAttributesAsEmptyList() {
        Resource resource = ResourceFactory.create(null, null, null);
        assertTrue(resource.getAttributes().isEmpty());
    }

    @Test
    @DisplayName("create() should skip null entity elements in lenient mode")
    void createShouldSkipNullEntityElementsInLenientMode() {
        List<Entity> entities = new ArrayList<>();
        entities.add(EntityFactory.create(
                "service",
                null,
                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-1"))),
                null));
        entities.add(null);

        Resource resource = ResourceFactory.create(null, entities, null);
        assertEquals(1, resource.getEntities().size());
        assertEquals("service", resource.getEntities().get(0).getType());
    }

    @Test
    @DisplayName("create() should collapse duplicate entity types using Entity.merge semantics")
    void createShouldCollapseDuplicateEntityTypes() {
        List<Entity> entities = Arrays.asList(
                EntityFactory.create(
                        "service",
                        "https://opentelemetry.io/schemas/1.27.0",
                        Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-1"))),
                        Collections.singletonList(new KeyValueImpl("deployment.environment", AnyValueFactory.ofString("dev")))),
                EntityFactory.create(
                        "service",
                        "https://opentelemetry.io/schemas/1.27.0",
                        Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-1"))),
                        Collections.singletonList(new KeyValueImpl("host.name", AnyValueFactory.ofString("host-a")))),
                EntityFactory.create(
                        "service",
                        "https://opentelemetry.io/schemas/1.27.0",
                        Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-2"))),
                        Collections.singletonList(new KeyValueImpl("ignored.key", AnyValueFactory.ofString("ignored"))))
        );

        Resource resource = ResourceFactory.create(null, entities, null);

        assertEquals(1, resource.getEntities().size());
        Map<String, String> description = keyValuesToStringMap(resource.getEntities().get(0).getDescription());
        assertEquals(2, description.size());
        assertEquals("dev", description.get("deployment.environment"));
        assertEquals("host-a", description.get("host.name"));
        assertFalse(description.containsKey("ignored.key"));
    }

    @Test
    @DisplayName("create() should drop lower-priority conflicting entities and loose attributes covered by entity keys")
    void createShouldDropConflictingEntitiesAndCoveredLooseAttributes() {
        List<Entity> entities = Arrays.asList(
                EntityFactory.create(
                        "host",
                        "https://opentelemetry.io/schemas/1.27.0",
                        Collections.singletonList(new KeyValueImpl("host.id", AnyValueFactory.ofString("h-1"))),
                        Collections.singletonList(new KeyValueImpl("deployment.environment", AnyValueFactory.ofString("prod")))),
                EntityFactory.create(
                        "service",
                        "https://opentelemetry.io/schemas/1.27.0",
                        Collections.singletonList(new KeyValueImpl("host.id", AnyValueFactory.ofString("h-1"))),
                        Collections.singletonList(new KeyValueImpl("service.name", AnyValueFactory.ofString("billing")))),
                EntityFactory.create(
                        "process",
                        "https://opentelemetry.io/schemas/1.27.0",
                        Collections.singletonList(new KeyValueImpl("process.pid", AnyValueFactory.ofLong(123L))),
                        Collections.singletonList(new KeyValueImpl("deployment.environment", AnyValueFactory.ofString("dev"))))
        );

        List<KeyValue> looseAttributes = Arrays.asList(
                new KeyValueImpl("deployment.environment", AnyValueFactory.ofString("staging")),
                new KeyValueImpl("host.id", AnyValueFactory.ofString("from-loose-attr")),
                new KeyValueImpl("service.namespace", AnyValueFactory.ofString("payments"))
        );

        Resource resource = ResourceFactory.create("https://ignored.example/schema", entities, looseAttributes);

        assertEquals(1, resource.getEntities().size());
        assertEquals("host", resource.getEntities().get(0).getType());
        assertEquals(1, resource.getAttributes().size());
        assertEquals("service.namespace", resource.getAttributes().get(0).getKey());
    }

    @Test
    @DisplayName("create() should set schemaUrl to null when entity schema URLs are empty or inconsistent")
    void createShouldSetSchemaUrlNullWhenEntitySchemaUrlsAreEmptyOrInconsistent() {
        Resource emptySchema = ResourceFactory.create(
                "https://fallback.example/schema",
                Collections.singletonList(EntityFactory.create(
                        "service",
                        "",
                        Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("i-1"))),
                        null)),
                null);
        assertNull(emptySchema.getSchemaUrl());

        Resource mixedSchema = ResourceFactory.create(
                "https://fallback.example/schema",
                Arrays.asList(
                        EntityFactory.create(
                                "service",
                                "https://opentelemetry.io/schemas/1.27.0",
                                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("i-1"))),
                                null),
                        EntityFactory.create(
                                "host",
                                "https://opentelemetry.io/schemas/1.28.0",
                                Collections.singletonList(new KeyValueImpl("host.id", AnyValueFactory.ofString("h-1"))),
                                null)),
                null);
        assertNull(mixedSchema.getSchemaUrl());
    }

    @Test
    @DisplayName("create() should set schemaUrl to null when an entity schemaUrl is null")
    void createShouldSetSchemaUrlNullWhenEntitySchemaUrlIsNull() {
        Resource resource = ResourceFactory.create(
                "https://fallback.example/schema",
                Collections.singletonList(EntityFactory.create(
                        "service",
                        null,
                        Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("i-1"))),
                        null)),
                null);

        assertNull(resource.getSchemaUrl());
    }

    @Test
    @DisplayName("create() should keep schemaUrl when all entities share the same non-empty schema")
    void createShouldKeepSchemaUrlWhenAllEntitiesShareSameNonEmptySchema() {
        Resource resource = ResourceFactory.create(
                null,
                Arrays.asList(
                        EntityFactory.create(
                                "service",
                                "https://opentelemetry.io/schemas/1.27.0",
                                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("i-1"))),
                                null),
                        EntityFactory.create(
                                "host",
                                "https://opentelemetry.io/schemas/1.27.0",
                                Collections.singletonList(new KeyValueImpl("host.id", AnyValueFactory.ofString("h-1"))),
                                null)),
                null);

        assertEquals("https://opentelemetry.io/schemas/1.27.0", resource.getSchemaUrl());
    }

    @Test
    @DisplayName("create() should skip duplicate keys")
    void createShouldSkipDuplicateKeys() {
        List<KeyValue> attrs = Arrays.asList(
                new KeyValueImpl("k", AnyValueFactory.ofString("v1")),
                new KeyValueImpl("k", AnyValueFactory.ofString("v2"))
        );
        Resource resource = ResourceFactory.create(null, null, attrs);
        assertEquals(1, resource.getAttributes().size());
        assertEquals("k", resource.getAttributes().get(0).getKey());
        assertEquals("v1", resource.getAttributes().get(0).getValue().asString());
    }

    @Test
    @DisplayName("create() should skip invalid key/value entries and normalize null values")
    void createShouldSkipInvalidEntries() {
        KeyValue nullKey = new KeyValue() {
            @Override
            public String getKey() {
                return null;
            }

            @Override
            public AnyValue getValue() {
                return AnyValueFactory.ofString("v");
            }
        };
        KeyValue nullValue = new KeyValue() {
            @Override
            public String getKey() {
                return "k";
            }

            @Override
            public AnyValue getValue() {
                return null;
            }
        };

        Resource resource = ResourceFactory.create(null, null, Arrays.asList(null, nullKey, nullValue));
        assertEquals(1, resource.getAttributes().size());
        assertEquals("k", resource.getAttributes().get(0).getKey());
        assertEquals(AnyValue.Type.EMPTY, resource.getAttributes().get(0).getValue().getType());
    }

    @Test
    @DisplayName("create() should keep all attributes without applying the generic limit")
    void createShouldKeepAllAttributesWithoutGenericLimit() {
        List<KeyValue> attributes = new ArrayList<>();
        for (int i = 0; i < 129; i++) {
            attributes.add(new KeyValueImpl("k" + i, AnyValueFactory.ofString("v" + i)));
        }

        Resource resource = ResourceFactory.create(null, null, attributes);

        assertEquals(129, resource.getAttributes().size());
        assertEquals("k128", resource.getAttributes().get(128).getKey());
    }

    @Test
    @DisplayName("merge() should keep current resource when incoming resource is null in lenient mode")
    void mergeShouldKeepCurrentResourceWhenIncomingResourceIsNullInLenientMode() {
        Resource resource = ResourceFactory.create(null, null, null);
        Resource merged = resource.merge(null);
        assertSame(resource, merged);
    }

    @Test
    @DisplayName("merge() should override loose attributes and use incoming schemaUrl when current is absent")
    void mergeShouldOverrideLooseAttributesAndPreferIncomingSchemaUrlWithoutEntities() {
        Resource base = ResourceFactory.create(
                null,
                null,
                Arrays.asList(
                        new KeyValueImpl("service.name", AnyValueFactory.ofString("billing")),
                        new KeyValueImpl("deployment.environment", AnyValueFactory.ofString("dev"))));

        Resource incoming = ResourceFactory.create(
                "https://incoming.example/schema",
                null,
                Arrays.asList(
                        new KeyValueImpl("deployment.environment", AnyValueFactory.ofString("prod")),
                        new KeyValueImpl("host.name", AnyValueFactory.ofString("host-a"))));

        Resource merged = base.merge(incoming);

        assertNotNull(merged);
        assertEquals("https://incoming.example/schema", merged.getSchemaUrl());
        Map<String, String> mergedAttributes = keyValuesToStringMap(merged.getAttributes());
        assertEquals(3, mergedAttributes.size());
        assertEquals("billing", mergedAttributes.get("service.name"));
        assertEquals("prod", mergedAttributes.get("deployment.environment"));
        assertEquals("host-a", mergedAttributes.get("host.name"));
    }

    @Test
    @DisplayName("merge() should reject resources with conflicting non-empty schema URLs")
    void mergeShouldRejectResourcesWithConflictingNonEmptySchemaUrls() {
        setLenient(false);
        Resource base = ResourceFactory.create("https://base.example/schema", null, null);
        Resource incoming = ResourceFactory.create("https://incoming.example/schema", null, null);

        assertThrows(IllegalArgumentException.class, () -> base.merge(incoming));
    }

    @Test
    @DisplayName("merge() should preserve current schemaUrl when incoming schemaUrl is null and entities are absent")
    void mergeShouldPreserveCurrentSchemaUrlWhenIncomingSchemaUrlIsNullAndEntitiesAreAbsent() {
        Resource base = ResourceFactory.create("https://base.example/schema", null, null);
        Resource incoming = ResourceFactory.create(null, null, Collections.<KeyValue>emptyList());

        Resource merged = base.merge(incoming);

        assertEquals("https://base.example/schema", merged.getSchemaUrl());
    }

    @Test
    @DisplayName("merge() should merge entities by type and drop loose attributes covered by merged entity keys")
    void mergeShouldMergeEntitiesAndDropCoveredLooseAttributes() {
        Entity baseService = EntityFactory.create(
                "service",
                "https://opentelemetry.io/schemas/1.27.0",
                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-1"))),
                Collections.singletonList(new KeyValueImpl("service.name", AnyValueFactory.ofString("billing"))));

        Entity incomingService = EntityFactory.create(
                "service",
                "https://opentelemetry.io/schemas/1.27.0",
                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-1"))),
                Collections.singletonList(new KeyValueImpl("deployment.environment", AnyValueFactory.ofString("prod"))));

        Resource base = ResourceFactory.create(
                null,
                Collections.singletonList(baseService),
                Arrays.asList(
                        new KeyValueImpl("service.name", AnyValueFactory.ofString("from-resource")),
                        new KeyValueImpl("resource.only", AnyValueFactory.ofString("base"))));

        Resource incoming = ResourceFactory.create(
                null,
                Collections.singletonList(incomingService),
                Arrays.asList(
                        new KeyValueImpl("deployment.environment", AnyValueFactory.ofString("from-resource")),
                        new KeyValueImpl("resource.only", AnyValueFactory.ofString("incoming"))));

        Resource merged = base.merge(incoming);

        assertEquals("https://opentelemetry.io/schemas/1.27.0", merged.getSchemaUrl());
        assertEquals(1, merged.getEntities().size());
        Map<String, String> mergedDescription = keyValuesToStringMap(merged.getEntities().get(0).getDescription());
        assertEquals(2, mergedDescription.size());
        assertEquals("billing", mergedDescription.get("service.name"));
        assertEquals("prod", mergedDescription.get("deployment.environment"));

        Map<String, String> looseAttributes = keyValuesToStringMap(merged.getAttributes());
        assertEquals(1, looseAttributes.size());
        assertEquals("incoming", looseAttributes.get("resource.only"));
    }

    @Test
    @DisplayName("merge() should set schemaUrl to null when merged entities have different schema URLs")
    void mergeShouldSetSchemaUrlToNullWhenMergedEntitiesHaveDifferentSchemaUrls() {
        Resource base = ResourceFactory.create(
                "https://opentelemetry.io/schemas/1.27.0",
                Collections.singletonList(EntityFactory.create(
                        "service",
                        "https://opentelemetry.io/schemas/1.27.0",
                        Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("i-1"))),
                        null)),
                null);

        Resource incoming = ResourceFactory.create(
                "https://opentelemetry.io/schemas/1.27.0",
                Collections.singletonList(EntityFactory.create(
                        "host",
                        "https://opentelemetry.io/schemas/1.28.0",
                        Collections.singletonList(new KeyValueImpl("host.id", AnyValueFactory.ofString("h-1"))),
                        null)),
                null);

        Resource merged = base.merge(incoming);

        assertNull(merged.getSchemaUrl());
        assertEquals(2, merged.getEntities().size());
    }

    @Test
    @DisplayName("ResourceImpl(schemaUrl, attributes) constructor should delegate to entity-aware model")
    void legacyResourceImplConstructorShouldDelegate() {
        ResourceImpl resource = new ResourceImpl(
                "https://opentelemetry.io/schemas/1.26.0",
                null,
                Collections.singletonList(new KeyValueImpl("service.name", AnyValueFactory.ofString("billing"))));

        assertEquals("https://opentelemetry.io/schemas/1.26.0", resource.getSchemaUrl());
        assertTrue(resource.getEntities().isEmpty());
        assertEquals(1, resource.getAttributes().size());
    }

    private static Map<String, String> keyValuesToStringMap(final List<KeyValue> keyValues) {
        return keyValues.stream()
                .collect(Collectors.toMap(KeyValue::getKey, kv -> kv.getValue().asString()));
    }

    private static void setLenient(final boolean value) {
        OpenTelemetryAttributeValidator.setLenientModeForTests(value);
    }
}
