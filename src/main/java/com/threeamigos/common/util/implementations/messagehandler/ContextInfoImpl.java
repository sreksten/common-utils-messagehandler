package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.ContextInfo;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the ContextInfo interface to manage contextual information
 * for logging and exception handling.
 *
 * @author Stefano Reksten
 */
public class ContextInfoImpl implements ContextInfo {

    private final Map<String, Object> context = new ConcurrentHashMap<>();

    @Override
    public void add(@Nonnull String key, Object value) {
        Objects.requireNonNull(key, MessageHandlerResourceBundle.get("nullContextInfoKeyProvided"));
        if (value == null) {
            context.remove(key);
        } else {
            context.put(key, value);
        }
    }

    @Override
    public Object get(@Nonnull String key) {
        Objects.requireNonNull(key, MessageHandlerResourceBundle.get("nullContextInfoKeyProvided"));
        return context.get(key);
    }

    @Override
    public void clear() {
        context.clear();
    }

    @Override
    public Map<String, Object> getValues() {
        return Collections.unmodifiableMap(context);
    }
}
