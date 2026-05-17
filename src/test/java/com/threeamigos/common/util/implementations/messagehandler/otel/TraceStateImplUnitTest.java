package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.TraceState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TraceStateImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class TraceStateImplUnitTest extends AbstractOtelValidatorLogTrapUnitTest {

    private static final boolean ORIGINAL_LENIENT = OpenTelemetryAttributeValidator.isLenientMode();

    @BeforeEach
    void setStrictModeByDefault() {
        setLenient(false);
    }

    @AfterEach
    void restoreOriginalMode() {
        setLenient(ORIGINAL_LENIENT);
    }

    @Test
    @DisplayName("default state should be empty")
    void defaultStateShouldBeEmpty() {
        TraceStateImpl state = new TraceStateImpl();
        assertTrue(state.getValues().isEmpty());
        assertNull(state.get("vendor"));
    }

    @Test
    @DisplayName("set() should return a new immutable state and keep original unchanged")
    void setShouldBeImmutable() {
        TraceState original = new TraceStateImpl();
        TraceState updated = original.set("vendor", AnyValueFactory.ofString("value"));

        assertNotSame(original, updated);
        assertNull(original.get("vendor"));
        assertNotNull(updated.get("vendor"));
        assertEquals("value", updated.get("vendor").asString());
        assertEquals(1, updated.getValues().size());
    }

    @Test
    @DisplayName("set() should move updated keys to the beginning")
    void setShouldMoveUpdatedKeyToFront() {
        TraceState state = new TraceStateImpl()
                .set("a", AnyValueFactory.ofString("1"))
                .set("b", AnyValueFactory.ofString("2"))
                .set("a", AnyValueFactory.ofString("3"));

        List<String> keys = keys(state.getValues());
        assertEquals(Arrays.asList("a", "b"), keys);
        assertEquals("3", state.get("a").asString());
    }

    @Test
    @DisplayName("set() should enforce maximum 32 entries and drop the oldest member")
    void setShouldEnforceMaxMembers() {
        TraceState state = new TraceStateImpl();
        for (int i = 1; i <= 33; i++) {
            state = state.set("k" + i, AnyValueFactory.ofString("v" + i));
        }

        assertEquals(32, state.getValues().size());
        assertNull(state.get("k1"));
        assertEquals("v33", state.get("k33").asString());
        assertEquals("k33", keys(state.getValues()).get(0));
    }

    @Test
    @DisplayName("getValues() should be unmodifiable")
    void getValuesShouldBeUnmodifiable() {
        TraceState state = new TraceStateImpl().set("vendor", AnyValueFactory.ofString("value"));
        Collection<KeyValue> values = state.getValues();
        assertThrows(UnsupportedOperationException.class,
                () -> values.add(new KeyValueImpl("other", AnyValueFactory.ofString("x"))));
    }

    @Test
    @DisplayName("delete() should remove existing key and return new state")
    void deleteShouldRemoveExistingKey() {
        TraceState state = new TraceStateImpl()
                .set("a", AnyValueFactory.ofString("1"))
                .set("b", AnyValueFactory.ofString("2"));

        TraceState deleted = state.delete("a");

        assertNotSame(state, deleted);
        assertNull(deleted.get("a"));
        assertEquals("2", deleted.get("b").asString());
        assertNotNull(state.get("a"));
    }

    @Test
    @DisplayName("delete() should return same instance when key is not present")
    void deleteShouldReturnSameWhenMissing() {
        TraceState state = new TraceStateImpl().set("a", AnyValueFactory.ofString("1"));
        assertSame(state, state.delete("missing"));
    }

    @Test
    @DisplayName("strict mode should throw for invalid tracestate keys")
    void strictModeShouldThrowForInvalidKey() {
        TraceState state = new TraceStateImpl();
        AnyValue value = AnyValueFactory.ofString("ok");

        assertThrows(IllegalArgumentException.class, () -> state.set("InvalidKey", value));
        assertThrows(IllegalArgumentException.class, () -> state.get("InvalidKey"));
        assertThrows(IllegalArgumentException.class, () -> state.delete("InvalidKey"));
        assertThrows(IllegalArgumentException.class, () -> state.set(null, value));
    }

    @Test
    @DisplayName("strict mode should throw for invalid tracestate values")
    void strictModeShouldThrowForInvalidValue() {
        TraceState state = new TraceStateImpl();

        assertThrows(IllegalArgumentException.class,
                () -> state.set("vendor", AnyValueFactory.ofLong(10L)));
        assertThrows(IllegalArgumentException.class,
                () -> state.set("vendor", AnyValueFactory.ofString("bad,value")));
        assertThrows(IllegalArgumentException.class,
                () -> state.set("vendor", AnyValueFactory.ofString("bad=value")));
        assertThrows(IllegalArgumentException.class,
                () -> state.set("vendor", AnyValueFactory.ofString("")));
        assertThrows(IllegalArgumentException.class,
                () -> state.set("vendor", null));
    }

    @Test
    @DisplayName("lenient mode should ignore invalid set/get/delete operations")
    void lenientModeShouldIgnoreInvalidOperations() {
        setLenient(true);
        TraceStateImpl state = new TraceStateImpl();
        TraceState afterSet = state.set("InvalidKey", AnyValueFactory.ofString("v"));
        TraceState afterDelete = state.delete("InvalidKey");

        assertSame(state, afterSet);
        assertSame(state, afterDelete);
        assertNull(state.get("InvalidKey"));
        assertTrue(state.getValues().isEmpty());
    }

    @Test
    @DisplayName("lenient mode should ignore invalid value and preserve state")
    void lenientModeShouldIgnoreInvalidValue() {
        setLenient(true);
        TraceState state = new TraceStateImpl().set("vendor", AnyValueFactory.ofString("ok"));

        TraceState unchanged = state.set("vendor", AnyValueFactory.ofLong(1L));
        assertSame(state, unchanged);
        assertEquals("ok", state.get("vendor").asString());
    }

    @Test
    @DisplayName("lenient mode should ignore string AnyValue with null payload")
    void lenientModeShouldIgnoreStringAnyValueWithNullPayload() {
        setLenient(true);
        TraceState state = new TraceStateImpl().set("vendor", AnyValueFactory.ofString("ok"));
        AnyValue invalidString = new AnyValue() {
            @Override
            public Type getType() {
                return Type.STRING;
            }

            @Override
            public String asString() {
                return null;
            }

            @Override
            public boolean asBoolean() {
                return false;
            }

            @Override
            public long asLong() {
                return 0;
            }

            @Override
            public double asDouble() {
                return 0;
            }

            @Override
            public List<AnyValue> asArray() {
                return Collections.emptyList();
            }

            @Override
            public List<KeyValue> asKvList() {
                return Collections.emptyList();
            }

            @Override
            public byte[] asBytes() {
                return new byte[0];
            }
        };

        assertSame(state, state.set("vendor", invalidString));
        assertEquals("ok", state.get("vendor").asString());
    }

    @Test
    @DisplayName("trace-state key with tenant format should be accepted")
    void tenantKeyFormatShouldBeAccepted() {
        TraceState state = new TraceStateImpl().set("tenant@vendor", AnyValueFactory.ofString("ok"));
        assertNotNull(state.get("tenant@vendor"));
        assertEquals("ok", state.get("tenant@vendor").asString());
    }

    private static List<String> keys(final Collection<KeyValue> values) {
        return new ArrayList<>(values).stream().map(KeyValue::getKey).collect(Collectors.toList());
    }

    private static void setLenient(final boolean value) {
        OpenTelemetryAttributeValidator.setLenientModeForTests(value);
    }
}
