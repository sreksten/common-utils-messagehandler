package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.RawJsonRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Entity;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RawJsonRecordFormatter unit tests")
@Tag("unit")
@Tag("messageHandler")
class RawJsonRecordFormatterUnitTest {

    @AfterEach
    void restoreValidatorDefaults() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(false);
        OpenTelemetryAttributeValidator.setLogTrapForTests(null);
    }

    @Test
    @DisplayName("mergedResourceAttributes should return empty list for null resources")
    @SuppressWarnings("unchecked")
    void mergedResourceAttributesShouldReturnEmptyListForNullResources() throws Exception {
        Method mergedResourceAttributes = RawJsonRecordFormatter.class.getDeclaredMethod(
                "mergedResourceAttributes",
                Resource.class);
        mergedResourceAttributes.setAccessible(true);

        List<KeyValue> result = (List<KeyValue>) mergedResourceAttributes.invoke(null, new Object[]{null});

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("format should sanitize malformed resource and entity attributes in lenient mode")
    void formatShouldSanitizeMalformedResourceAndEntityAttributesInLenientMode() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        OpenTelemetryAttributeValidator.setLogTrapForTests((message, throwable) -> {
            // no-op trap for lenient branch coverage
        });

        Entity entity = new Entity() {
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
                return Arrays.asList(
                        null,
                        keyValue(null, AnyValueFactory.ofString("x")),
                        keyValue("   ", AnyValueFactory.ofString("x")),
                        keyValue("id.good", AnyValueFactory.ofString("1")));
            }

            @Override
            public List<KeyValue> getDescription() {
                return Arrays.asList(
                        null,
                        keyValue(null, AnyValueFactory.ofString("x")),
                        keyValue("   ", AnyValueFactory.ofString("x")),
                        keyValue("desc.good", AnyValueFactory.ofString("2")));
            }

            @Override
            public Entity merge(final Entity other) {
                return this;
            }
        };

        Resource resource = new Resource() {
            @Override
            public List<Entity> getEntities() {
                return Collections.singletonList(entity);
            }

            @Override
            public String getSchemaUrl() {
                return null;
            }

            @Override
            public List<KeyValue> getAttributes() {
                return Arrays.asList(
                        null,
                        keyValue(null, AnyValueFactory.ofString("x")),
                        keyValue("   ", AnyValueFactory.ofString("x")),
                        keyValue("dup", AnyValueFactory.ofString("one")),
                        keyValue("dup", AnyValueFactory.ofString("two")),
                        keyValue("null.value", null),
                        keyValue("  trim.key  ", AnyValueFactory.ofString("trimmed")));
            }

            @Override
            public Resource merge(final Resource other) {
                return this;
            }
        };

        LogRecordImpl record = new LogRecordImpl();
        record.setTimestamp(Instant.parse("2026-05-02T10:00:00Z"));
        record.setResource(resource);

        String json = new RawJsonRecordFormatter(true).format(record);

        assertTrue(json.contains("\"resource\":{"));
        assertTrue(json.contains("\"key\":\"dup\""));
        int firstDup = json.indexOf("\"key\":\"dup\"");
        assertFalse(json.indexOf("\"key\":\"dup\"", firstDup + 1) >= 0);
        assertTrue(json.contains("\"key\":\"null.value\",\"value\":{}"));
        assertTrue(json.contains("\"key\":\"trim.key\""));
        assertTrue(json.contains("\"entityRefs\":[{\"type\":\"service\""));
        assertTrue(json.contains("\"idKeys\":[\"id.good\"]"));
        assertTrue(json.contains("\"descriptionKeys\":[\"desc.good\"]"));
    }

    private static KeyValue keyValue(final String key, final AnyValue value) {
        return new KeyValue() {
            @Override
            public String getKey() {
                return key;
            }

            @Override
            public AnyValue getValue() {
                return value;
            }
        };
    }
}
