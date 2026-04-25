package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.util.List;

/**
 * Utility factory for creating {@link InstrumentationScope} values.
 *
 * @author Stefano Reksten
 */
public final class InstrumentationScopeFactory {

    private InstrumentationScopeFactory() {
    }

    public static InstrumentationScope create(final String name,
                                              final String version,
                                              final String schemaUrl,
                                              final List<KeyValue> attributes) {
        return new InstrumentationScopeImpl(name, version, schemaUrl, attributes);
    }
}
