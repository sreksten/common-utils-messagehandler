package com.threeamigos.common.util.interfaces.messagehandler;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Map;

/**
 * Represents contextual information associated with a message.
 *
 * @author Stefano Reksten
 */
public interface ContextInfo {

    /**
     * Key for the fully qualified class name in the context.
     */
    String CLASS_NAME = "className";

    /**
     * Associates the given value with the given key in this context.
     * If the key is already present, its previous value is replaced.
     * If {@code value} is {@code null}, any existing entry for {@code key} is removed.
     *
     * @param key   the non-null key that identifies the context entry
     * @param value the value to associate with {@code key}; passing {@code null} removes the entry
     */
    void add(@Nonnull String key, @Nullable Object value);

    /**
     * Returns the value associated with the given key, or {@code null} if the key is absent.
     *
     * @param key the non-null key to look up
     * @return the associated value
     */
    @Nullable Object get(@Nonnull String key);

    /**
     * Removes all entries from this context.
     */
    void clear();

    /**
     * Returns an unmodifiable view of all key-value pairs currently held in this context.
     *
     * @return a non-null, possibly empty map of context entries
     */
    Map<String, Object> getValues();

}
