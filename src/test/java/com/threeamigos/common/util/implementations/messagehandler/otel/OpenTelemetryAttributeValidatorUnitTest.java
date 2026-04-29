package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OpenTelemetryAttributeValidator unit tests")
@Tag("unit")
@Tag("messageHandler")
class OpenTelemetryAttributeValidatorUnitTest extends AbstractOtelValidatorLogTrapUnitTest {

    private static final boolean ORIGINAL_LENIENT = OpenTelemetryAttributeValidator.isLenientMode();

    @BeforeEach
    void enforceLenientModeForValidationTests() {
        setLenient(true);
    }

    @AfterEach
    void restoreLenientMode() {
        setLenient(ORIGINAL_LENIENT);
    }

    @Test
    @DisplayName("requireNonBlank should return value when valid")
    void requireNonBlankShouldReturnValueWhenValid() {
        assertEquals("checkout", OpenTelemetryAttributeValidator.requireNonBlank("checkout", "service.name"));
    }

    @Test
    @DisplayName("requireNonBlank should normalize null and blank to unknown")
    void requireNonBlankShouldNormalizeNullAndBlank() {
        assertEquals("unknown", OpenTelemetryAttributeValidator.requireNonBlank(null, "service.name"));
        assertEquals("unknown", OpenTelemetryAttributeValidator.requireNonBlank("   ", "service.name"));
    }

    @Test
    @DisplayName("constructor should be private and instantiable via reflection for coverage")
    void constructorShouldBePrivateUtilityGuard() throws Exception {
        Constructor<OpenTelemetryAttributeValidator> constructor = OpenTelemetryAttributeValidator.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        OpenTelemetryAttributeValidator instance = constructor.newInstance();
        assertNotNull(instance);
    }

    @Test
    @DisplayName("copyAndValidateKeyValues() should return empty list for null input")
    void copyAndValidateKeyValuesShouldReturnEmptyListForNullInput() {
        List<KeyValue> copy = OpenTelemetryAttributeValidator.copyAndValidateKeyValues(null, "attrs");
        assertTrue(copy.isEmpty());
    }

    @Test
    @DisplayName("copyAndValidateKeyValues() should return an independent copy")
    void copyAndValidateKeyValuesShouldReturnIndependentCopy() {
        List<KeyValue> source = new ArrayList<>(Collections.singletonList(
                new KeyValueImpl("k1", AnyValueFactory.ofString("v1"))
        ));

        List<KeyValue> copy = OpenTelemetryAttributeValidator.copyAndValidateKeyValues(source, "attrs");
        assertNotSame(source, copy);
        assertEquals(1, copy.size());

        source.add(new KeyValueImpl("k2", AnyValueFactory.ofString("v2")));
        assertEquals(1, copy.size());
    }

    @Test
    @DisplayName("copyAndValidateKeyValues() should skip null elements")
    void copyAndValidateKeyValuesShouldSkipNullElements() {
        List<KeyValue> source = Arrays.asList(new KeyValueImpl("k1", AnyValueFactory.ofString("v1")), null);
        List<KeyValue> copy = OpenTelemetryAttributeValidator.copyAndValidateKeyValues(source, "attrs");
        assertEquals(1, copy.size());
        assertEquals("k1", copy.get(0).getKey());
    }

    @Test
    @DisplayName("copyAndValidateKeyValues() should skip null keys")
    void copyAndValidateKeyValuesShouldSkipNullKeys() {
        KeyValue invalid = new KeyValue() {
            @Override
            public String getKey() {
                return null;
            }

            @Override
            public AnyValue getValue() {
                return AnyValueFactory.ofString("v");
            }
        };

        List<KeyValue> copy = OpenTelemetryAttributeValidator.copyAndValidateKeyValues(Collections.singletonList(invalid), "attrs");
        assertTrue(copy.isEmpty());
    }

    @Test
    @DisplayName("copyAndValidateKeyValues() should skip empty keys")
    void copyAndValidateKeyValuesShouldSkipEmptyKeys() {
        KeyValue invalid = new KeyValue() {
            @Override
            public String getKey() {
                return "";
            }

            @Override
            public AnyValue getValue() {
                return AnyValueFactory.ofString("v");
            }
        };

        List<KeyValue> copy = OpenTelemetryAttributeValidator.copyAndValidateKeyValues(Collections.singletonList(invalid), "attrs");
        assertTrue(copy.isEmpty());
    }

    @Test
    @DisplayName("copyAndValidateKeyValues() should normalize null values to AnyValue.EMPTY")
    void copyAndValidateKeyValuesShouldNormalizeNullValues() {
        KeyValue invalid = new KeyValue() {
            @Override
            public String getKey() {
                return "k";
            }

            @Override
            public AnyValue getValue() {
                return null;
            }
        };

        List<KeyValue> copy = OpenTelemetryAttributeValidator.copyAndValidateKeyValues(Collections.singletonList(invalid), "attrs");
        assertEquals(1, copy.size());
        assertEquals("k", copy.get(0).getKey());
        assertEquals(AnyValue.Type.EMPTY, copy.get(0).getValue().getType());
    }

    @Test
    @DisplayName("copyAndValidateKeyValues() should skip duplicate keys")
    void copyAndValidateKeyValuesShouldSkipDuplicateKeys() {
        List<KeyValue> source = Arrays.asList(
                new KeyValueImpl("dup", AnyValueFactory.ofString("v1")),
                new KeyValueImpl("dup", AnyValueFactory.ofString("v2"))
        );
        List<KeyValue> copy = OpenTelemetryAttributeValidator.copyAndValidateKeyValues(source, "attrs");
        assertEquals(1, copy.size());
        assertEquals("dup", copy.get(0).getKey());
        assertEquals("v1", copy.get(0).getValue().asString());
    }

    @Test
    @DisplayName("copyAndValidateKeyValues() should accept valid unique key-values")
    void copyAndValidateKeyValuesShouldAcceptValidUniqueValues() {
        assertDoesNotThrow(() -> OpenTelemetryAttributeValidator.copyAndValidateKeyValues(Arrays.asList(
                new KeyValueImpl("a", AnyValueFactory.ofString("1")),
                new KeyValueImpl("b", AnyValueFactory.ofLong(2))
        ), "attrs"));
    }

    private static void setLenient(final boolean value) {
        OpenTelemetryAttributeValidator.setLenientModeForTests(value);
    }
}
