package com.threeamigos.common.util.implementations.messagehandler.durability;

import com.threeamigos.common.util.implementations.messagehandler.utils.ParametersValidator;
import com.threeamigos.common.util.interfaces.messagehandler.durability.HttpDispatchDurabilityStore;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import jakarta.annotation.Nonnull;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Redis-backed durability store using Jedis.
 * <p>
 * Entries are kept in one Redis list key in insertion order.
 *
 * @author Stefano Reksten
 */
public class RedisHttpDispatchDurabilityStore implements HttpDispatchDurabilityStore {

    private final Object lock = new Object();
    private final JedisPool jedisPool;
    private final String redisListKey;

    public RedisHttpDispatchDurabilityStore(final @Nonnull String redisUri,
                                            final @Nonnull String redisListKey) {
        this(new JedisPool(URI.create(ParametersValidator.validateNotBlank(redisUri, "redisUri"))), redisListKey);
    }

    public RedisHttpDispatchDurabilityStore(final @Nonnull String redisHost,
                                            final int redisPort,
                                            final @Nonnull String redisListKey) {
        this(new JedisPool(
                ParametersValidator.validateNotBlank(redisHost, "redisHost"),
                        ParametersValidator.validateGreaterThanZero(redisPort, "redisPort")),
                redisListKey);
    }

    RedisHttpDispatchDurabilityStore(final @Nonnull JedisPool jedisPool,
                                     final @Nonnull String redisListKey) {
        this.jedisPool = ParametersValidator.validateNotNull(jedisPool, "jedisPool");
        this.redisListKey = ParametersValidator.validateNotBlank(redisListKey, "redisListKey");
    }

    @Override
    @Nonnull
    public String store(final @Nonnull LogRecord logRecord) throws IOException {
        ParametersValidator.validateNotNull(logRecord, "logRecord");
        String entryId = UUID.randomUUID().toString();
        DurableLogRecordEntry durableEntry = new DurableLogRecordEntry(entryId, System.currentTimeMillis(), logRecord);
        String encodedPayload = DurableLogRecordSerialization.toBase64(durableEntry);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.rpush(redisListKey, encodedPayload);
        } catch (RuntimeException redisException) {
            throw new IOException("Failed to store durable entry in Redis key '" + redisListKey + "'", redisException);
        }
        return entryId;
    }

    @Override
    @Nonnull
    public List<DurableLogRecordEntry> retrievePending() throws IOException {
        try (Jedis jedis = jedisPool.getResource()) {
            List<String> encodedEntries = jedis.lrange(redisListKey, 0, -1);
            List<DurableLogRecordEntry> pendingEntries = new ArrayList<>(encodedEntries.size());
            for (String encodedEntry : encodedEntries) {
                pendingEntries.add(DurableLogRecordSerialization.fromBase64(encodedEntry));
            }
            return pendingEntries;
        } catch (RuntimeException redisException) {
            throw new IOException("Failed to retrieve durable entries from Redis key '" + redisListKey + "'", redisException);
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
            try (Jedis jedis = jedisPool.getResource()) {
                List<String> encodedEntries = jedis.lrange(redisListKey, 0, -1);
                if (encodedEntries.isEmpty()) {
                    return;
                }
                List<String> survivors = new ArrayList<>(encodedEntries.size());
                for (String encodedEntry : encodedEntries) {
                    DurableLogRecordEntry durableEntry = DurableLogRecordSerialization.fromBase64(encodedEntry);
                    if (!idsToRemove.contains(durableEntry.getEntryId())) {
                        survivors.add(encodedEntry);
                    }
                }
                jedis.del(redisListKey);
                if (!survivors.isEmpty()) {
                    jedis.rpush(redisListKey, survivors.toArray(new String[0]));
                }
            } catch (RuntimeException redisException) {
                throw new IOException("Failed to remove durable entries from Redis key '" + redisListKey + "'", redisException);
            }
        }
    }

    @Override
    public void close() {
        jedisPool.close();
    }
}
