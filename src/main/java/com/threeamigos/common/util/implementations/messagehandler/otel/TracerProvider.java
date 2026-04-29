package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;

/**
 * The <code>TracerProvider</code> is expected to be the stateful object that holds any configuration.
 * @author Stefano Reksten
 */
public class TracerProvider {

    private static final TracerProvider INSTANCE = new TracerProvider();

    public static TracerProvider getGlobal() {
        return INSTANCE;
    }

    private TracerProvider() {}

    public TracerProvider createProvider() {
        return new TracerProvider();
    }

    /**
     * Returns a tracer instance with the given instrumentation name, version, schema URL, and attributes.
     * @param instrumentationName The name of the instrumentation. Should not be null or empty. As per spec, if it is
     *                            null, it will be treated as an empty string.
     * @param version The version of the instrumentation.
     * @param schemaUrl The schema URL for the instrumentation.
     * @param attributes The attributes associated with the instrumentation.
     * @return A tracer instance or null if not available.
     */
    public Tracer getTracer(@Nonnull String instrumentationName,
                            final @Nullable String version,
                            final @Nullable String schemaUrl,
                            final @Nullable Collection<KeyValue> attributes) {
        return null;
    }

}
