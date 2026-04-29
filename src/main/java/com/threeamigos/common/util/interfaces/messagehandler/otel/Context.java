package com.threeamigos.common.util.interfaces.messagehandler.otel;

/**
 * OpenTelemetry-like execution context container.
 * <p>
 * Specification references:
 * <ul>
 *    <li><a href="https://opentelemetry.io/docs/specs/otel/context/">OpenTelemetry Context</a></li>
 *    <li><a href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/context/README.md">OpenTelemetry
 *    Context Specification</a></li>
 * </ul>
 * Context values are immutable snapshots: {@link #set(Key, AnyValue)} returns a new
 * context instead of mutating the existing one.
 * <p>
 * The current context is bound to the current execution unit (thread in this implementation)
 * and can be temporarily replaced via {@link #attachContext(Context)} and restored with
 * {@link #detachContext(String)}.
 *
 * @author Stefano Reksten
 */
public interface Context {

    /**
     * Opaque context key type.
     */
    interface Key {
        /**
         * @return human-readable key name, useful only for debugging/logging.
         */
        String getName();
    }

    /**
     * Returns an opaque Key object with the given name. If the name is null or empty, a random name will be chosen.
     * @param name name of the key.
     * @return a Key object with the given name, or a random name if the provided name is null or empty.
     */
    Key createKey(String name);

    /**
     * Returns a new Context with the added KeyValue pair.
     * @param key key to add.
     * @param value value to add.
     * @return a new Context with the added KeyValue pair, or the current context if the key is null.
     */
    Context set(Key key, AnyValue value);

    /**
     * Retrieves the value associated with the given key in the current context.
     * @param key key to retrieve the value for.
     * @return the value associated with the key, or null if the key is not found.
     */
    AnyValue get(Key key);

    /**
     * Returns the current context.
     * @return the current context.
     */
    Context getContext();

    /**
     * Attaches the given context to the current context and returns a token for detaching.
     * @param context context to attach.
     * @return a token for detaching the context.
     */
    String attachContext(Context context);

    /**
     * Detaches the context associated with the given token. If the given token is null or invalid, it returns false.
     * @param token token for detaching the context.
     * @return true if the context was successfully detached, false otherwise.
     */
    boolean detachContext(String token);

}
