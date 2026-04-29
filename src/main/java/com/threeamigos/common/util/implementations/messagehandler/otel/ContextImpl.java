package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Context;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An immutable implementation of a {@link Context}.
 *
 * @author Stefano Reksten
 */
public class ContextImpl implements Context {

    private static final Context ROOT = new ContextImpl(Collections.emptyMap());

    private static final ThreadLocal<Context> CURRENT_CONTEXT =
            ThreadLocal.withInitial(() -> ROOT);

    private static final ThreadLocal<Deque<Holder>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private final Map<Key, AnyValue> values;

    /**
     * Creates a new empty immutable context.
     * <p>
     * This constructor does not attach the created context as current.
     */
    public ContextImpl() {
        this(Collections.emptyMap());
    }

    private ContextImpl(final Map<Key, AnyValue> values) {
        this.values = Collections.unmodifiableMap(new HashMap<>(values));
    }

    @Override
    public Key createKey(final String name) {
        if (name == null || name.trim().isEmpty()) {
            logWarning("nullContextKeyNameProvided");
            return new KeyImpl(UUID.randomUUID().toString());
        }
        return new KeyImpl(name);
    }

    @Override
    public Context set(final Key key, final AnyValue value) {
        if (key == null) {
            logWarning("nullContextKeyProvided");
            return this;
        }
        Map<Key, AnyValue> newValues = new HashMap<>(values);
        newValues.put(key, value);
        if (value == null) {
            logWarning("nullContextValueProvided");
        }
        return new ContextImpl(newValues);
    }

    @Override
    public AnyValue get(final Key key) {
        if (key == null) {
            logWarning("nullContextKeyProvided");
            return null;
        }
        return values.get(key);
    }

    @Override
    public Context getContext() {
        return CURRENT_CONTEXT.get();
    }

    @Override
    public String attachContext(final Context context) {
        if (context == null) {
            logWarning("nullContextProvided");
            return null;
        }
        String token = UUID.randomUUID().toString();
        Context previousContext = getContext();
        STACK.get().push(new Holder(token, previousContext));
        CURRENT_CONTEXT.set(context);
        return token;
    }

    @Override
    public boolean detachContext(final String token) {
        if (token == null) {
            logWarning("nullContextTokenProvided");
            return false;
        }

        Deque<Holder> currentStack = STACK.get();
        Holder last = currentStack.peek();
        if (last == null || !last.token.equals(token)) {
            logWarning("invalidContextDetachTokenOrNonLifoDetachAttempt");
            if (currentStack.isEmpty()) {
                STACK.remove();
            }
            return false;
        }

        currentStack.pop();
        CURRENT_CONTEXT.set(last.context);

        if (currentStack.isEmpty()) {
            STACK.remove();
            CURRENT_CONTEXT.remove();
        }
        return true;
    }

    private static void logWarning(final String bundleKey) {
        OpenTelemetryAttributeValidator.reportBundled(bundleKey);
    }

    private static final class Holder {
        private final String token;
        private final Context context;

        private Holder(final String token, final Context context) {
            this.token = token;
            this.context = context;
        }
    }

    static final class KeyImpl implements Key {
        private final String name;

        KeyImpl(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
