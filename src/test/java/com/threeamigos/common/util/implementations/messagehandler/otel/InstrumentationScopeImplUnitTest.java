package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("InstrumentationScopeImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class InstrumentationScopeImplUnitTest extends AbstractOtelValidatorLogTrapUnitTest {

    private static final boolean ORIGINAL_LENIENT = OpenTelemetryAttributeValidator.isLenientMode();

    @BeforeEach
    void enforceLenientModeForSanitizationTests() {
        setLenient(true);
    }

    @AfterEach
    void restoreLenientMode() {
        setLenient(ORIGINAL_LENIENT);
    }

    @Test
    @DisplayName("defaults should keep optional fields null")
    void defaultsShouldBeExpectedValues() {
        InstrumentationScopeImpl scope = new InstrumentationScopeImpl(null, null, null, null);
        assertNull(scope.getName());
        assertNull(scope.getVersion());
        assertNull(scope.getSchemaUrl());
        assertTrue(scope.getAttributes().isEmpty());
        assertEquals(0, scope.getDroppedAttributesCount());
    }

    @Test
    @DisplayName("constructor should store scalar fields")
    void constructorShouldStoreScalarFields() {
        InstrumentationScopeImpl scope = new InstrumentationScopeImpl(
                "com.example.lib",
                "1.2.3",
                "https://opentelemetry.io/schemas/1.26.0",
                null);

        assertEquals("com.example.lib", scope.getName());
        assertEquals("1.2.3", scope.getVersion());
        assertEquals("https://opentelemetry.io/schemas/1.26.0", scope.getSchemaUrl());
    }

    @Test
    @DisplayName("constructor should copy and expose unmodifiable attributes")
    void constructorShouldCopyAndExposeUnmodifiableAttributes() {
        List<KeyValue> attrs = new ArrayList<>(Collections.singletonList(
                new KeyValueImpl("scope.attr", AnyValueFactory.ofString("x"))
        ));

        InstrumentationScopeImpl scope = new InstrumentationScopeImpl(
                null, null, null, attrs);
        assertEquals(1, scope.getAttributes().size());
        assertThrows(UnsupportedOperationException.class,
                () -> scope.getAttributes().add(new KeyValueImpl("k", AnyValueFactory.ofString("v"))));

        attrs.add(new KeyValueImpl("other", AnyValueFactory.ofLong(2)));
        assertEquals(1, scope.getAttributes().size());
    }

    @Test
    @DisplayName("constructor should accept null attributes as empty list")
    void constructorShouldAcceptNullAsEmptyList() {
        InstrumentationScopeImpl scope = new InstrumentationScopeImpl(
                null, null, null, null);
        assertTrue(scope.getAttributes().isEmpty());
    }

    @Test
    @DisplayName("constructor should normalize blank scalar fields to null")
    void constructorShouldSanitizeBlankScalarFields() {
        InstrumentationScopeImpl scope = new InstrumentationScopeImpl(" ", "   ", "   ", null);
        assertNull(scope.getName());
        assertNull(scope.getVersion());
        assertNull(scope.getSchemaUrl());
    }

    @Test
    @DisplayName("constructor should trim non-blank scalar fields")
    void constructorShouldTrimNonBlankScalarFields() {
        InstrumentationScopeImpl scope = new InstrumentationScopeImpl(
                " com.example.lib ",
                " 1.2.3 ",
                " https://opentelemetry.io/schemas/1.26.0 ",
                null);
        assertEquals("com.example.lib", scope.getName());
        assertEquals("1.2.3", scope.getVersion());
        assertEquals("https://opentelemetry.io/schemas/1.26.0", scope.getSchemaUrl());
    }

    @Test
    @DisplayName("constructor should skip duplicate keys")
    void constructorShouldSkipDuplicateKeys() {
        List<KeyValue> attrs = Arrays.asList(
                new KeyValueImpl("k", AnyValueFactory.ofString("v1")),
                new KeyValueImpl("k", AnyValueFactory.ofString("v2"))
        );
        InstrumentationScopeImpl scope = new InstrumentationScopeImpl(null, null, null, attrs);
        assertEquals(1, scope.getAttributes().size());
        assertEquals("k", scope.getAttributes().get(0).getKey());
        assertEquals("v1", scope.getAttributes().get(0).getValue().asString());
    }

    @Test
    @DisplayName("constructor should skip invalid entries and normalize null values")
    void constructorShouldSkipInvalidEntries() {
        KeyValue emptyKey = new KeyValue() {
            @Override
            public String getKey() {
                return "";
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

        InstrumentationScopeImpl scope = new InstrumentationScopeImpl(
                null, null, null, Arrays.asList(emptyKey, nullValue, null));
        assertEquals(1, scope.getAttributes().size());
        assertEquals("k", scope.getAttributes().get(0).getKey());
        assertEquals(AnyValue.Type.EMPTY, scope.getAttributes().get(0).getValue().getType());
    }

    @Test
    @DisplayName("constructor should drop attributes above the default attribute count limit")
    void constructorShouldDropAttributesAboveDefaultLimit() {
        List<KeyValue> attributes = new ArrayList<>();
        for (int i = 0; i < 129; i++) {
            attributes.add(new KeyValueImpl("k" + i, AnyValueFactory.ofString("v" + i)));
        }

        InstrumentationScopeImpl scope = new InstrumentationScopeImpl(
                null, null, null, attributes);

        assertEquals(128, scope.getAttributes().size());
        assertEquals(1, scope.getDroppedAttributesCount());
        assertEquals("k127", scope.getAttributes().get(127).getKey());
    }

    private static void setLenient(final boolean value) {
        OpenTelemetryAttributeValidator.setLenientModeForTests(value);
    }
}
