package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Entity;

import java.util.List;

/**
 * Utility factory for creating {@link Resource} values.
 *
 * @author Stefano Reksten
 */
public final class ResourceFactory {

    private ResourceFactory() {}

    public static Resource create(final String schemaUrl,
                                  final List<KeyValue> attributes) {
        return create(schemaUrl, null, attributes);
    }

    public static Resource create(final String schemaUrl,
                                  final List<Entity> entities,
                                  final List<KeyValue> attributes) {
        return new ResourceImpl(schemaUrl, entities, attributes);
    }
}
