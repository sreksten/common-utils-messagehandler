package com.threeamigos.common.util.implementations.messagehandler.durability;

import com.threeamigos.common.util.implementations.messagehandler.utils.ParametersValidator;
import jakarta.annotation.Nonnull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;

/**
 * Serialization helper for {@link DurableLogRecordEntry}.
 */
final class DurableLogRecordSerialization {

    private DurableLogRecordSerialization() {
    }

    static String toBase64(final @Nonnull DurableLogRecordEntry durableEntry) throws IOException {
        ParametersValidator.validateNotNull(durableEntry, "durableEntry");
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(durableEntry);
        }
        return Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray());
    }

    static DurableLogRecordEntry fromBase64(final String encodedEntry) throws IOException {
        ParametersValidator.validateNotBlank(encodedEntry, "encodedEntry");
        byte[] serializedBytes;
        try {
            serializedBytes = Base64.getDecoder().decode(encodedEntry);
        } catch (IllegalArgumentException decodeException) {
            throw new IOException("Failed to decode durable entry payload", decodeException);
        }
        return fromBytes(serializedBytes);
    }

    static byte[] toBytes(final DurableLogRecordEntry durableEntry) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(durableEntry);
        }
        return byteArrayOutputStream.toByteArray();
    }

    static DurableLogRecordEntry fromBytes(final byte[] serializedBytes) throws IOException {
        ParametersValidator.validateNonEmpty(serializedBytes, "serializedBytes");
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serializedBytes))) {
            Object deserialized = objectInputStream.readObject();
            if (!(deserialized instanceof DurableLogRecordEntry)) {
                throw new IOException("Unexpected durable entry payload type: " + deserialized.getClass().getName());
            }
            return (DurableLogRecordEntry) deserialized;
        } catch (ClassNotFoundException classNotFoundException) {
            throw new IOException("Durable entry class not found while deserializing payload", classNotFoundException);
        }
    }
}
