package com.threeamigos.common.util.implementations.messagehandler.durability;

import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordFactoryImpl;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DurableLogRecordSerialization unit tests")
@Tag("unit")
@Tag("messageHandler")
class DurableLogRecordSerializationUnitTest {

    private static DurableLogRecordEntry createEntry() {
        LogRecord record = new LogRecordFactoryImpl().create(SeverityNumber.INFO, "test payload");
        return new DurableLogRecordEntry("entry-1", System.currentTimeMillis(), record);
    }

    @Test
    @DisplayName("toBase64 and fromBase64 should round-trip a DurableLogRecordEntry")
    void toBase64AndFromBase64ShouldRoundTrip() throws IOException {
        DurableLogRecordEntry original = createEntry();
        String encoded = DurableLogRecordSerialization.toBase64(original);
        assertNotNull(encoded);
        DurableLogRecordEntry decoded = DurableLogRecordSerialization.fromBase64(encoded);
        assertEquals(original.getEntryId(), decoded.getEntryId());
    }

    @Test
    @DisplayName("toBytes and fromBytes should round-trip a DurableLogRecordEntry")
    void toBytesAndFromBytesShouldRoundTrip() throws IOException {
        DurableLogRecordEntry original = createEntry();
        byte[] bytes = DurableLogRecordSerialization.toBytes(original);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        DurableLogRecordEntry decoded = DurableLogRecordSerialization.fromBytes(bytes);
        assertEquals(original.getEntryId(), decoded.getEntryId());
    }

    @Test
    @DisplayName("fromBase64 should throw IOException for null input")
    void fromBase64ShouldThrowForNullInput() {
        assertThrows(IOException.class, () -> DurableLogRecordSerialization.fromBase64(null));
    }

    @Test
    @DisplayName("fromBase64 should throw IOException for blank input")
    void fromBase64ShouldThrowForBlankInput() {
        assertThrows(IOException.class, () -> DurableLogRecordSerialization.fromBase64("  "));
    }

    @Test
    @DisplayName("fromBase64 should throw IOException for invalid base64")
    void fromBase64ShouldThrowForInvalidBase64() {
        assertThrows(IOException.class, () -> DurableLogRecordSerialization.fromBase64("not-valid-base64!!!"));
    }

    @Test
    @DisplayName("fromBytes should throw IOException for null input")
    void fromBytesShouldThrowForNullInput() {
        assertThrows(IOException.class, () -> DurableLogRecordSerialization.fromBytes(null));
    }

    @Test
    @DisplayName("fromBytes should throw IOException for empty byte array")
    void fromBytesShouldThrowForEmptyArray() {
        assertThrows(IOException.class, () -> DurableLogRecordSerialization.fromBytes(new byte[0]));
    }

    @Test
    @DisplayName("fromBytes should throw IOException when deserialized object is not DurableLogRecordEntry")
    void fromBytesShouldThrowForWrongType() throws IOException {
        // Serialize a plain String object, then try to deserialize as DurableLogRecordEntry
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos)) {
            oos.writeObject("not-a-durable-entry");
        }
        byte[] wrongTypeBytes = baos.toByteArray();
        assertThrows(IOException.class, () -> DurableLogRecordSerialization.fromBytes(wrongTypeBytes));
    }

    @Test
    @DisplayName("fromBytes should throw IOException for non-deserializable bytes")
    void fromBytesShouldThrowForCorruptBytes() {
        byte[] corruptBytes = "this-is-not-java-serialization".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> DurableLogRecordSerialization.fromBytes(corruptBytes));
    }
}
