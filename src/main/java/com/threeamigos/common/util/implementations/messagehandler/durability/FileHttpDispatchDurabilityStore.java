package com.threeamigos.common.util.implementations.messagehandler.durability;

import com.threeamigos.common.util.implementations.messagehandler.utils.ParametersValidator;
import com.threeamigos.common.util.interfaces.messagehandler.durability.HttpDispatchDurabilityStore;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import jakarta.annotation.Nonnull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * File-backed durability store.
 * <p>
 * This policy persists pending entries in one local file so they survive JVM restarts.
 *
 * @author Stefano Reksten
 */
public class FileHttpDispatchDurabilityStore implements HttpDispatchDurabilityStore {

    private final Object lock = new Object();
    private final Path storagePath;
    private final List<DurableLogRecordEntry> pendingEntries;

    public FileHttpDispatchDurabilityStore(final @Nonnull String storageFilePath) throws IOException {
        this(Paths.get(ParametersValidator.validateNotNull(storageFilePath, "storageFilePath")));
    }

    public FileHttpDispatchDurabilityStore(final @Nonnull Path storagePath) throws IOException {
        this.storagePath = ParametersValidator.validateNotNull(storagePath, "storagePath");
        this.pendingEntries = loadFromDisk(storagePath);
    }

    @Override
    @Nonnull
    public String store(final @Nonnull LogRecord logRecord) throws IOException {
        ParametersValidator.validateNotNull(logRecord, "logRecord");
        String entryId = UUID.randomUUID().toString();
        DurableLogRecordEntry durableEntry = new DurableLogRecordEntry(entryId, System.currentTimeMillis(), logRecord);
        synchronized (lock) {
            pendingEntries.add(durableEntry);
            persistLocked();
        }
        return entryId;
    }

    @Override
    @Nonnull
    public List<DurableLogRecordEntry> retrievePending() {
        synchronized (lock) {
            return new ArrayList<>(pendingEntries);
        }
    }

    @Override
    public void remove(final @Nonnull List<String> entryIds) throws IOException {
        ParametersValidator.validateNotNull(entryIds, "entryIds");
        if (entryIds.isEmpty()) {
            return;
        }
        Set<String> idsToRemove = new HashSet<>(entryIds);
        synchronized (lock) {
            boolean removedAny = false;
            Iterator<DurableLogRecordEntry> iterator = pendingEntries.iterator();
            while (iterator.hasNext()) {
                DurableLogRecordEntry durableEntry = iterator.next();
                if (idsToRemove.contains(durableEntry.getEntryId())) {
                    iterator.remove();
                    removedAny = true;
                }
            }
            if (removedAny) {
                persistLocked();
            }
        }
    }

    private void persistLocked() throws IOException {
        Path parent = storagePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempPath = createTempPath(parent);
        byte[] serialized = serializeEntries(pendingEntries);
        Files.write(tempPath, serialized, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        moveTempToTarget(tempPath, storagePath);
    }

    private static byte[] serializeEntries(final List<DurableLogRecordEntry> entries) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(entries);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private static List<DurableLogRecordEntry> loadFromDisk(final Path storagePath) throws IOException {
        if (!Files.exists(storagePath)) {
            return new ArrayList<>();
        }
        byte[] serialized = Files.readAllBytes(storagePath);
        if (serialized.length == 0) {
            return new ArrayList<>();
        }
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object deserialized = objectInputStream.readObject();
            if (!(deserialized instanceof List)) {
                throw new IOException("Unexpected durable file payload type: " + deserialized.getClass().getName());
            }
            List<?> rawEntries = (List<?>) deserialized;
            List<DurableLogRecordEntry> loaded = new ArrayList<>(rawEntries.size());
            for (Object rawEntry : rawEntries) {
                if (!(rawEntry instanceof DurableLogRecordEntry)) {
                    throw new IOException("Unexpected durable file entry type: " + rawEntry.getClass().getName());
                }
                loaded.add((DurableLogRecordEntry) rawEntry);
            }
            return loaded;
        } catch (ClassNotFoundException classNotFoundException) {
            throw new IOException("Failed to deserialize durable file payload", classNotFoundException);
        }
    }

    private static Path createTempPath(final Path parent) throws IOException {
        if (parent != null) {
            return Files.createTempFile(parent, "message-handler-durable-", ".tmp");
        }
        return Files.createTempFile("message-handler-durable-", ".tmp");
    }

    private static void moveTempToTarget(final Path tempPath, final Path targetPath) throws IOException {
        try {
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveException) {
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }
}
