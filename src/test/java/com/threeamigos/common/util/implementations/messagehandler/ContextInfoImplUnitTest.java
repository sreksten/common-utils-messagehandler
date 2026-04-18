package com.threeamigos.common.util.implementations.messagehandler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ContextInfoImpl unit test")
@Tag("unit")
@Tag("messageHandler")
class ContextInfoImplUnitTest {

    @Test
    @DisplayName("add and get should store and retrieve a value by key")
    void addAndGetShouldStoreAndRetrieveValue() {
        ContextInfoImpl sut = new ContextInfoImpl();

        sut.add("key", "value");

        assertEquals("value", sut.get("key"));
    }

    @Test
    @DisplayName("get should return null for an absent key")
    void getShouldReturnNullForAbsentKey() {
        ContextInfoImpl sut = new ContextInfoImpl();

        assertNull(sut.get("missing"));
    }

    @Test
    @DisplayName("add should replace the previous value for an existing key")
    void addShouldReplaceExistingValue() {
        ContextInfoImpl sut = new ContextInfoImpl();

        sut.add("key", "first");
        sut.add("key", "second");

        assertEquals("second", sut.get("key"));
    }

    @Test
    @DisplayName("add with null value on absent key should be a no-op")
    void addWithNullValueOnAbsentKeyShouldBeNoOp() {
        ContextInfoImpl sut = new ContextInfoImpl();

        sut.add("key", null);

        assertNull(sut.get("key"));
        assertTrue(sut.getValues().isEmpty());
    }

    @Test
    @DisplayName("add with null value should remove an existing entry")
    void addWithNullValueShouldRemoveExistingEntry() {
        ContextInfoImpl sut = new ContextInfoImpl();
        sut.add("key", "original");

        sut.add("key", null);

        assertNull(sut.get("key"));
        assertTrue(sut.getValues().isEmpty());
    }

    @Test
    @DisplayName("getValues should return all stored key-value pairs")
    void getValuesShouldReturnAllEntries() {
        ContextInfoImpl sut = new ContextInfoImpl();
        sut.add("a", 1);
        sut.add("b", 2);

        Map<String, Object> values = sut.getValues();

        assertEquals(2, values.size());
        assertEquals(1, values.get("a"));
        assertEquals(2, values.get("b"));
    }

    @Test
    @DisplayName("getValues should return an unmodifiable view")
    void getValuesShouldReturnUnmodifiableView() {
        ContextInfoImpl sut = new ContextInfoImpl();
        sut.add("key", "value");

        Map<String, Object> values = sut.getValues();

        assertThrows(UnsupportedOperationException.class, () -> values.put("new", "entry"));
    }

    @Test
    @DisplayName("getValues should return an empty map when no entries have been added")
    void getValuesShouldReturnEmptyMapWhenEmpty() {
        ContextInfoImpl sut = new ContextInfoImpl();

        assertTrue(sut.getValues().isEmpty());
    }

    @Test
    @DisplayName("clear should remove all entries")
    void clearShouldRemoveAllEntries() {
        ContextInfoImpl sut = new ContextInfoImpl();
        sut.add("a", 1);
        sut.add("b", 2);

        sut.clear();

        assertTrue(sut.getValues().isEmpty());
        assertNull(sut.get("a"));
        assertNull(sut.get("b"));
    }

    @Test
    @DisplayName("clear on an empty context should be a no-op")
    void clearOnEmptyContextShouldBeNoOp() {
        ContextInfoImpl sut = new ContextInfoImpl();

        assertDoesNotThrow(sut::clear);
        assertTrue(sut.getValues().isEmpty());
    }

    @Test
    @DisplayName("add should throw NullPointerException when key is null and value is non-null")
    void addShouldThrowNpeWhenKeyIsNullAndValueIsNonNull() {
        ContextInfoImpl sut = new ContextInfoImpl();

        assertThrows(NullPointerException.class, () -> sut.add(null, "value"));
    }

    @Test
    @DisplayName("add should throw NullPointerException when key is null and value is null")
    void addShouldThrowNpeWhenKeyIsNullAndValueIsNull() {
        ContextInfoImpl sut = new ContextInfoImpl();

        assertThrows(NullPointerException.class, () -> sut.add(null, null));
    }
}
