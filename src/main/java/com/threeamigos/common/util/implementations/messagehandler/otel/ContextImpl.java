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
 * Immutable {@link Context} implementation.
 * <p>
 * Current-context state is tracked per-thread via {@link ThreadLocal}.
 * Attach/detach follows strict LIFO semantics.
 * <p>
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
            OpenTelemetryAttributeValidator.handleBundled("nullContextKeyNameProvided");
            return new Key(UUID.randomUUID().toString());
        }
        return new Key(name);
    }

    @Override
    public Context set(final Key key, final AnyValue value) {
        if (key == null) {
            OpenTelemetryAttributeValidator.handleBundled("nullContextKeyProvided");
            return this;
        }
        if (value == null) {
            OpenTelemetryAttributeValidator.handleBundled("nullContextValueProvided");
            return this;
        }
        Map<Key, AnyValue> newValues = new HashMap<>(values);
        newValues.put(key, value);
        return new ContextImpl(newValues);
    }

    @Override
    public AnyValue get(final Key key) {
        if (key == null) {
            OpenTelemetryAttributeValidator.handleBundled("nullContextKeyProvided");
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
            OpenTelemetryAttributeValidator.handleBundled("nullContextProvided");
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
            OpenTelemetryAttributeValidator.handleBundled("nullContextTokenProvided");
            return false;
        }

        Deque<Holder> currentStack = STACK.get();
        Holder last = currentStack.peek();
        if (last == null || !last.token.equals(token)) {
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

    private static final class Holder {
        private final String token;
        private final Context context;

        private Holder(final String token, final Context context) {
            this.token = token;
            this.context = context;
        }
    }
}
