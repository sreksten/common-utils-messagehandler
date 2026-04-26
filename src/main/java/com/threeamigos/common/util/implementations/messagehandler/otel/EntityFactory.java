package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.Entity;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.util.List;

/**
 * Utility factory for creating {@link Entity} values.
 *
 * @author Stefano Reksten
 */
public final class EntityFactory {

    private EntityFactory() {}

    public static Entity create(final String type,
                                final String schemaUrl,
                                final List<KeyValue> id,
                                final List<KeyValue> description) {
        return new EntityImpl(type, schemaUrl, id, description);
    }
}
