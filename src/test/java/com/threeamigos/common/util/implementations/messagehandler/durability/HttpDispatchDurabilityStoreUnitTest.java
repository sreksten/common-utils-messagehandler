package com.threeamigos.common.util.implementations.messagehandler.durability;

import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordFactoryImpl;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("HTTP dispatch durability store unit tests")
@Tag("unit")
@Tag("messageHandler")
class HttpDispatchDurabilityStoreUnitTest {

    private static final LogRecordFactoryImpl FACTORY = new LogRecordFactoryImpl();

    @Test
    @DisplayName("in-memory store should keep insertion order and remove acknowledged entries")
    void inMemoryStoreShouldKeepInsertionOrderAndRemoveAcknowledgedEntries() throws Exception {
        InMemoryHttpDispatchDurabilityStore store = new InMemoryHttpDispatchDurabilityStore();
        LogRecord first = FACTORY.create(SeverityNumber.INFO, "one");
        LogRecord second = FACTORY.create(SeverityNumber.WARN, "two");

        String firstId = store.store(first);
        String secondId = store.store(second);

        List<DurableLogRecordEntry> pending = store.retrievePending();
        assertEquals(2, pending.size());
        assertEquals(firstId, pending.get(0).getEntryId());
        assertEquals(secondId, pending.get(1).getEntryId());

        store.remove(Collections.singletonList(firstId));
        List<DurableLogRecordEntry> afterRemove = store.retrievePending();
        assertEquals(1, afterRemove.size());
        assertEquals(secondId, afterRemove.get(0).getEntryId());
    }

    @Test
    @DisplayName("file store should persist pending entries across instances")
    void fileStoreShouldPersistPendingEntriesAcrossInstances() throws Exception {
        Path tempFile = Files.createTempFile("message-handler-durability-", ".bin");
        try {
            LogRecord record = FACTORY.create(SeverityNumber.ERROR, "disk");
            String entryId;
            try (FileHttpDispatchDurabilityStore firstInstance = new FileHttpDispatchDurabilityStore(tempFile)) {
                entryId = firstInstance.store(record);
                assertEquals(1, firstInstance.retrievePending().size());
            }

            try (FileHttpDispatchDurabilityStore secondInstance = new FileHttpDispatchDurabilityStore(tempFile)) {
                List<DurableLogRecordEntry> pending = secondInstance.retrievePending();
                assertEquals(1, pending.size());
                assertEquals(entryId, pending.get(0).getEntryId());
                secondInstance.remove(Collections.singletonList(entryId));
                assertTrue(secondInstance.retrievePending().isEmpty());
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("getPolicyName should return the simple class name of the implementation")
    void getPolicyNameShouldReturnSimpleClassName() {
        InMemoryHttpDispatchDurabilityStore store = new InMemoryHttpDispatchDurabilityStore();
        assertEquals("InMemoryHttpDispatchDurabilityStore", store.getPolicyName());
    }
}
