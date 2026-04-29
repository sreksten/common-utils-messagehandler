package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Entity;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EntityFactory unit tests")
@Tag("unit")
@Tag("messageHandler")
class EntityFactoryUnitTest extends AbstractOtelValidatorLogTrapUnitTest {

    @Test
    @DisplayName("create(type, schemaUrl, id, description) should store values and expose unmodifiable lists")
    void createWithSchemaUrlShouldStoreValuesAndExposeUnmodifiableLists() {
        List<KeyValue> id = new ArrayList<>(Collections.singletonList(
                new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-1"))));
        List<KeyValue> description = new ArrayList<>(Collections.singletonList(
                new KeyValueImpl("service.name", AnyValueFactory.ofString("billing"))));

        Entity entity = EntityFactory.create(
                "service",
                "https://opentelemetry.io/schemas/1.27.0",
                id,
                description);

        assertEquals("service", entity.getType());
        assertEquals("https://opentelemetry.io/schemas/1.27.0", entity.getSchemaUrl());
        assertEquals(1, entity.getId().size());
        assertEquals("service.instance.id", entity.getId().get(0).getKey());
        assertEquals(1, entity.getDescription().size());
        assertEquals("service.name", entity.getDescription().get(0).getKey());
        assertThrows(UnsupportedOperationException.class,
                () -> entity.getId().add(new KeyValueImpl("x", AnyValueFactory.ofString("y"))));
        assertThrows(UnsupportedOperationException.class,
                () -> entity.getDescription().add(new KeyValueImpl("x", AnyValueFactory.ofString("y"))));

        id.add(new KeyValueImpl("service.version", AnyValueFactory.ofString("1.0.0")));
        description.add(new KeyValueImpl("host.name", AnyValueFactory.ofString("host-a")));
        assertEquals(1, entity.getId().size());
        assertEquals(1, entity.getDescription().size());
    }

    @Test
    @DisplayName("create(type, null, id, description) should set schemaUrl to null and accept null description")
    void createWithoutSchemaUrlShouldSetSchemaUrlNullAndAcceptNullDescription() {
        Entity entity = EntityFactory.create(
                "service",
                null,
                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-2"))),
                null);

        assertEquals("service", entity.getType());
        assertNull(entity.getSchemaUrl());
        assertEquals(1, entity.getId().size());
        assertTrue(entity.getDescription().isEmpty());
    }

    @Test
    @DisplayName("create() should normalize null and blank type values")
    void createShouldNormalizeNullAndBlankType() {
        List<KeyValue> id = Collections.singletonList(
                new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-1")));

        Entity nullType = EntityFactory.create(null, null, id, null);
        Entity emptyType = EntityFactory.create("", null, id, null);
        Entity blankType = EntityFactory.create("   ", null, id, null);
        Entity invalidType = EntityFactory.create("@invalid", null, id, null);
        Entity trimmedType = EntityFactory.create("  service.instance  ", null, id, null);

        assertEquals("unknown", nullType.getType());
        assertEquals("unknown", emptyType.getType());
        assertEquals("unknown", blankType.getType());
        assertEquals("unknown", invalidType.getType());
        assertEquals("service.instance", trimmedType.getType());
    }

    @Test
    @DisplayName("create() should normalize blank or invalid schemaUrl to null")
    void createShouldNormalizeInvalidSchemaUrlToNull() {
        List<KeyValue> id = Collections.singletonList(
                new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-1")));

        Entity blankSchema = EntityFactory.create("service", "   ", id, null);
        Entity invalidSchema = EntityFactory.create("service", "ht^tp:// bad-uri", id, null);
        Entity trimmedSchema = EntityFactory.create("service", "  https://opentelemetry.io/schemas/1.27.0  ", id, null);

        assertNull(blankSchema.getSchemaUrl());
        assertNull(invalidSchema.getSchemaUrl());
        assertEquals("https://opentelemetry.io/schemas/1.27.0", trimmedSchema.getSchemaUrl());
    }

    @Test
    @DisplayName("create() should default entity id when id list is null or empty")
    void createShouldDefaultEntityIdWhenIdListIsNullOrEmpty() {
        Entity nullId = EntityFactory.create("service", null, null, null);
        Entity emptyId = EntityFactory.create("service", null, Collections.<KeyValue>emptyList(), null);

        assertEquals(1, nullId.getId().size());
        assertEquals("unknown_id", nullId.getId().get(0).getKey());
        assertEquals("unknown", nullId.getId().get(0).getValue().asString());

        assertEquals(1, emptyId.getId().size());
        assertEquals("unknown_id", emptyId.getId().get(0).getKey());
        assertEquals("unknown", emptyId.getId().get(0).getValue().asString());
    }

    @Test
    @DisplayName("create() should skip invalid id entries and normalize null values")
    void createShouldSkipInvalidIdEntriesAndNormalizeNullValues() {
        List<KeyValue> duplicateId = Arrays.asList(
                null,
                new KeyValue() {
                    @Override
                    public String getKey() {
                        return null;
                    }

                    @Override
                    public AnyValue getValue() {
                        return AnyValueFactory.ofString("x");
                    }
                },
                new KeyValue() {
                    @Override
                    public String getKey() {
                        return " ";
                    }

                    @Override
                    public AnyValue getValue() {
                        return AnyValueFactory.ofString("x");
                    }
                },
                new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("i1")),
                new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("i2")),
                new KeyValue() {
                    @Override
                    public String getKey() {
                        return "service.name";
                    }

                    @Override
                    public AnyValue getValue() {
                        return null;
                    }
                }
        );

        Entity entity = EntityFactory.create("service", null, duplicateId, null);
        assertEquals(2, entity.getId().size());
        assertEquals("service.instance.id", entity.getId().get(0).getKey());
        assertEquals("i1", entity.getId().get(0).getValue().asString());
        assertEquals("service.name", entity.getId().get(1).getKey());
        assertEquals(AnyValue.Type.EMPTY, entity.getId().get(1).getValue().getType());
    }

    @Test
    @DisplayName("create() should skip invalid description entries")
    void createShouldSkipInvalidDescriptionEntries() {
        List<KeyValue> id = Collections.singletonList(
                new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-3")));

        List<KeyValue> duplicateDescription = Arrays.asList(
                null,
                new KeyValueImpl("service.name", AnyValueFactory.ofString("a")),
                new KeyValueImpl("service.name", AnyValueFactory.ofString("b")),
                new KeyValue() {
                    @Override
                    public String getKey() {
                        return "host.name";
                    }

                    @Override
                    public AnyValue getValue() {
                        return null;
                    }
                });

        Entity entity = EntityFactory.create("service", null, id, duplicateDescription);
        assertEquals(2, entity.getDescription().size());
        assertEquals("service.name", entity.getDescription().get(0).getKey());
        assertEquals("a", entity.getDescription().get(0).getValue().asString());
        assertEquals("host.name", entity.getDescription().get(1).getKey());
        assertEquals(AnyValue.Type.EMPTY, entity.getDescription().get(1).getValue().getType());
    }

    @Test
    @DisplayName("merge() should return the same instance when other is null")
    void mergeShouldReturnSameInstanceWhenOtherIsNull() {
        Entity entity = EntityFactory.create(
                "service",
                null,
                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-1"))),
                null);

        assertSame(entity, entity.merge(null));
    }

    @Test
    @DisplayName("merge() should ignore malformed external entities")
    void mergeShouldIgnoreMalformedExternalEntities() {
        Entity base = EntityFactory.create(
                "service",
                null,
                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-1"))),
                Collections.singletonList(new KeyValueImpl("service.name", AnyValueFactory.ofString("billing"))));

        Entity malformed = new Entity() {
            @Override
            public String getType() {
                return "service";
            }

            @Override
            public String getSchemaUrl() {
                return null;
            }

            @Override
            public List<KeyValue> getId() {
                return Collections.singletonList(null);
            }

            @Override
            public List<KeyValue> getDescription() {
                return null;
            }

            @Override
            public Entity merge(Entity other) {
                return this;
            }
        };

        assertSame(base, base.merge(malformed));
    }

    @Test
    @DisplayName("merge() should return the same instance when entities are incompatible")
    void mergeShouldReturnSameInstanceWhenEntitiesAreIncompatible() {
        Entity base = EntityFactory.create(
                "service",
                "https://opentelemetry.io/schemas/1.27.0",
                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-1"))),
                Collections.singletonList(new KeyValueImpl("service.name", AnyValueFactory.ofString("billing"))));

        Entity differentType = EntityFactory.create(
                "host",
                "https://opentelemetry.io/schemas/1.27.0",
                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-1"))),
                Collections.singletonList(new KeyValueImpl("host.name", AnyValueFactory.ofString("host-a"))));
        assertSame(base, base.merge(differentType));

        Entity differentSchema = EntityFactory.create(
                "service",
                "https://opentelemetry.io/schemas/1.28.0",
                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-1"))),
                Collections.singletonList(new KeyValueImpl("service.name", AnyValueFactory.ofString("x"))));
        assertSame(base, base.merge(differentSchema));

        Entity differentId = EntityFactory.create(
                "service",
                "https://opentelemetry.io/schemas/1.27.0",
                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-2"))),
                Collections.singletonList(new KeyValueImpl("service.name", AnyValueFactory.ofString("x"))));
        assertSame(base, base.merge(differentId));
    }

    @Test
    @DisplayName("merge() should return the same instance when other description is empty")
    void mergeShouldReturnSameInstanceWhenOtherDescriptionIsEmpty() {
        Entity base = EntityFactory.create(
                "service",
                null,
                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-1"))),
                Collections.singletonList(new KeyValueImpl("service.name", AnyValueFactory.ofString("billing"))));

        Entity other = EntityFactory.create(
                "service",
                null,
                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-1"))),
                Collections.emptyList());

        assertSame(base, base.merge(other));
    }

    @Test
    @DisplayName("merge() should return the same instance when compatible descriptions are equivalent")
    void mergeShouldReturnSameInstanceWhenCompatibleDescriptionsAreEquivalent() {
        Entity base = EntityFactory.create(
                "service",
                null,
                Arrays.asList(
                        new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-1")),
                        new KeyValueImpl("service.namespace", AnyValueFactory.ofString("payments"))),
                Collections.singletonList(new KeyValueImpl("service.name", AnyValueFactory.ofString("billing"))));

        Entity other = EntityFactory.create(
                "service",
                null,
                Arrays.asList(
                        new KeyValueImpl("service.namespace", AnyValueFactory.ofString("payments")),
                        new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-1"))),
                Collections.singletonList(new KeyValueImpl("service.name", AnyValueFactory.ofString("billing"))));

        assertSame(base, base.merge(other));
    }

    @Test
    @DisplayName("merge() should merge compatible entities and prefer incoming description values")
    void mergeShouldMergeCompatibleEntitiesAndPreferIncomingDescriptionValues() {
        Entity base = EntityFactory.create(
                "service",
                "https://opentelemetry.io/schemas/1.27.0",
                Arrays.asList(
                        new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-1")),
                        new KeyValueImpl("service.namespace", AnyValueFactory.ofString("payments"))),
                Arrays.asList(
                        new KeyValueImpl("service.name", AnyValueFactory.ofString("billing")),
                        new KeyValueImpl("deployment.environment", AnyValueFactory.ofString("dev"))));

        Entity incoming = EntityFactory.create(
                "service",
                "https://opentelemetry.io/schemas/1.27.0",
                Arrays.asList(
                        new KeyValueImpl("service.namespace", AnyValueFactory.ofString("payments")),
                        new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-1"))),
                Arrays.asList(
                        new KeyValueImpl("deployment.environment", AnyValueFactory.ofString("prod")),
                        new KeyValueImpl("host.name", AnyValueFactory.ofString("host-a"))));

        Entity merged = base.merge(incoming);

        assertNotSame(base, merged);
        assertEquals("service", merged.getType());
        assertEquals("https://opentelemetry.io/schemas/1.27.0", merged.getSchemaUrl());
        assertEquals(2, merged.getId().size());
        assertEquals(3, merged.getDescription().size());

        java.util.Map<String, String> mergedDescription = new java.util.HashMap<>();
        for (KeyValue kv : merged.getDescription()) {
            mergedDescription.put(kv.getKey(), kv.getValue().asString());
        }
        assertEquals("billing", mergedDescription.get("service.name"));
        assertEquals("prod", mergedDescription.get("deployment.environment"));
        assertEquals("host-a", mergedDescription.get("host.name"));

        java.util.Map<String, String> baseDescription = new java.util.HashMap<>();
        for (KeyValue kv : base.getDescription()) {
            baseDescription.put(kv.getKey(), kv.getValue().asString());
        }
        assertEquals("dev", baseDescription.get("deployment.environment"));
    }
}
