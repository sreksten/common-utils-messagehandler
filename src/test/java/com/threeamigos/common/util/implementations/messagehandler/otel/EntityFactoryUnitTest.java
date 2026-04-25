package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Entity;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EntityFactory unit tests")
@Tag("unit")
@Tag("messageHandler")
class EntityFactoryUnitTest {

    @Test
    @DisplayName("create(type, schemaUrl, id, description) should store values and expose unmodifiable lists")
    void createWithSchemaUrlShouldStoreValuesAndExposeUnmodifiableLists() {
        List<KeyValue> id = new ArrayList<>(Collections.singletonList(
                new KeyValueImpl("service.instance.id", AnyValueImpl.ofString("instance-1"))));
        List<KeyValue> description = new ArrayList<>(Collections.singletonList(
                new KeyValueImpl("service.name", AnyValueImpl.ofString("billing"))));

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
                () -> entity.getId().add(new KeyValueImpl("x", AnyValueImpl.ofString("y"))));
        assertThrows(UnsupportedOperationException.class,
                () -> entity.getDescription().add(new KeyValueImpl("x", AnyValueImpl.ofString("y"))));

        id.add(new KeyValueImpl("service.version", AnyValueImpl.ofString("1.0.0")));
        description.add(new KeyValueImpl("host.name", AnyValueImpl.ofString("host-a")));
        assertEquals(1, entity.getId().size());
        assertEquals(1, entity.getDescription().size());
    }

    @Test
    @DisplayName("create(type, id, description) should set schemaUrl to null and accept null description")
    void createWithoutSchemaUrlShouldSetSchemaUrlNullAndAcceptNullDescription() {
        Entity entity = EntityFactory.create(
                "service",
                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueImpl.ofString("instance-2"))),
                null);

        assertEquals("service", entity.getType());
        assertNull(entity.getSchemaUrl());
        assertEquals(1, entity.getId().size());
        assertTrue(entity.getDescription().isEmpty());
    }

    @Test
    @DisplayName("create() should reject null and blank type")
    void createShouldRejectNullAndBlankType() {
        List<KeyValue> id = Collections.singletonList(
                new KeyValueImpl("service.instance.id", AnyValueImpl.ofString("instance-1")));

        assertThrows(NullPointerException.class, () -> EntityFactory.create(null, id, null));
        assertThrows(IllegalArgumentException.class, () -> EntityFactory.create("", id, null));
        assertThrows(IllegalArgumentException.class, () -> EntityFactory.create("   ", id, null));
    }

    @Test
    @DisplayName("create() should reject null or empty entity id")
    void createShouldRejectNullOrEmptyEntityId() {
        assertThrows(IllegalArgumentException.class, () -> EntityFactory.create("service", null, null));
        assertThrows(IllegalArgumentException.class, () -> EntityFactory.create("service", Collections.<KeyValue>emptyList(), null));
    }

    @Test
    @DisplayName("create() should reject invalid id entries")
    void createShouldRejectInvalidIdEntries() {
        List<KeyValue> duplicateId = Arrays.asList(
                new KeyValueImpl("service.instance.id", AnyValueImpl.ofString("i1")),
                new KeyValueImpl("service.instance.id", AnyValueImpl.ofString("i2")));
        assertThrows(IllegalArgumentException.class, () -> EntityFactory.create("service", duplicateId, null));

        KeyValue nullKey = new KeyValue() {
            @Override
            public String getKey() {
                return null;
            }

            @Override
            public AnyValue getValue() {
                return AnyValueImpl.ofString("x");
            }
        };
        KeyValue nullValue = new KeyValue() {
            @Override
            public String getKey() {
                return "service.instance.id";
            }

            @Override
            public AnyValue getValue() {
                return null;
            }
        };

        assertThrows(NullPointerException.class, () -> EntityFactory.create("service", Collections.singletonList(null), null));
        assertThrows(NullPointerException.class, () -> EntityFactory.create("service", Collections.singletonList(nullKey), null));
        assertThrows(NullPointerException.class, () -> EntityFactory.create("service", Collections.singletonList(nullValue), null));
    }

    @Test
    @DisplayName("create() should reject invalid description entries")
    void createShouldRejectInvalidDescriptionEntries() {
        List<KeyValue> id = Collections.singletonList(
                new KeyValueImpl("service.instance.id", AnyValueImpl.ofString("instance-3")));

        List<KeyValue> duplicateDescription = Arrays.asList(
                new KeyValueImpl("service.name", AnyValueImpl.ofString("a")),
                new KeyValueImpl("service.name", AnyValueImpl.ofString("b")));
        assertThrows(IllegalArgumentException.class,
                () -> EntityFactory.create("service", id, duplicateDescription));
    }

    @Test
    @DisplayName("merge() should reject null entity")
    void mergeShouldRejectNullEntity() {
        Entity entity = EntityFactory.create(
                "service",
                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueImpl.ofString("instance-1"))),
                null);

        assertThrows(NullPointerException.class, () -> entity.merge(null));
    }

    @Test
    @DisplayName("merge() should return the same instance when entities are incompatible")
    void mergeShouldReturnSameInstanceWhenEntitiesAreIncompatible() {
        Entity base = EntityFactory.create(
                "service",
                "https://opentelemetry.io/schemas/1.27.0",
                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueImpl.ofString("instance-1"))),
                Collections.singletonList(new KeyValueImpl("service.name", AnyValueImpl.ofString("billing"))));

        Entity differentType = EntityFactory.create(
                "host",
                "https://opentelemetry.io/schemas/1.27.0",
                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueImpl.ofString("instance-1"))),
                Collections.singletonList(new KeyValueImpl("host.name", AnyValueImpl.ofString("host-a"))));
        assertSame(base, base.merge(differentType));

        Entity differentSchema = EntityFactory.create(
                "service",
                "https://opentelemetry.io/schemas/1.28.0",
                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueImpl.ofString("instance-1"))),
                Collections.singletonList(new KeyValueImpl("service.name", AnyValueImpl.ofString("x"))));
        assertSame(base, base.merge(differentSchema));

        Entity differentId = EntityFactory.create(
                "service",
                "https://opentelemetry.io/schemas/1.27.0",
                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueImpl.ofString("instance-2"))),
                Collections.singletonList(new KeyValueImpl("service.name", AnyValueImpl.ofString("x"))));
        assertSame(base, base.merge(differentId));
    }

    @Test
    @DisplayName("merge() should return the same instance when other description is empty")
    void mergeShouldReturnSameInstanceWhenOtherDescriptionIsEmpty() {
        Entity base = EntityFactory.create(
                "service",
                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueImpl.ofString("instance-1"))),
                Collections.singletonList(new KeyValueImpl("service.name", AnyValueImpl.ofString("billing"))));

        Entity other = EntityFactory.create(
                "service",
                Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueImpl.ofString("instance-1"))),
                Collections.emptyList());

        assertSame(base, base.merge(other));
    }

    @Test
    @DisplayName("merge() should return the same instance when compatible descriptions are equivalent")
    void mergeShouldReturnSameInstanceWhenCompatibleDescriptionsAreEquivalent() {
        Entity base = EntityFactory.create(
                "service",
                Arrays.asList(
                        new KeyValueImpl("service.instance.id", AnyValueImpl.ofString("instance-1")),
                        new KeyValueImpl("service.namespace", AnyValueImpl.ofString("payments"))),
                Collections.singletonList(new KeyValueImpl("service.name", AnyValueImpl.ofString("billing"))));

        Entity other = EntityFactory.create(
                "service",
                Arrays.asList(
                        new KeyValueImpl("service.namespace", AnyValueImpl.ofString("payments")),
                        new KeyValueImpl("service.instance.id", AnyValueImpl.ofString("instance-1"))),
                Collections.singletonList(new KeyValueImpl("service.name", AnyValueImpl.ofString("billing"))));

        assertSame(base, base.merge(other));
    }

    @Test
    @DisplayName("merge() should merge compatible entities and prefer incoming description values")
    void mergeShouldMergeCompatibleEntitiesAndPreferIncomingDescriptionValues() {
        Entity base = EntityFactory.create(
                "service",
                "https://opentelemetry.io/schemas/1.27.0",
                Arrays.asList(
                        new KeyValueImpl("service.instance.id", AnyValueImpl.ofString("instance-1")),
                        new KeyValueImpl("service.namespace", AnyValueImpl.ofString("payments"))),
                Arrays.asList(
                        new KeyValueImpl("service.name", AnyValueImpl.ofString("billing")),
                        new KeyValueImpl("deployment.environment", AnyValueImpl.ofString("dev"))));

        Entity incoming = EntityFactory.create(
                "service",
                "https://opentelemetry.io/schemas/1.27.0",
                Arrays.asList(
                        new KeyValueImpl("service.namespace", AnyValueImpl.ofString("payments")),
                        new KeyValueImpl("service.instance.id", AnyValueImpl.ofString("instance-1"))),
                Arrays.asList(
                        new KeyValueImpl("deployment.environment", AnyValueImpl.ofString("prod")),
                        new KeyValueImpl("host.name", AnyValueImpl.ofString("host-a"))));

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
