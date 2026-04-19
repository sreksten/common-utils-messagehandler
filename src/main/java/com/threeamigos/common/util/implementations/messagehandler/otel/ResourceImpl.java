package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable implementation of {@link Resource}.
 * All fields default to empty list / {@code 0} and may be set via the corresponding setters.
 *
 * @author Stefano Reksten
 */
public class ResourceImpl implements Resource {

    private String schemaUrl;
    private List<KeyValue> attributes = new ArrayList<>();
    private int droppedAttributesCount;

    @Override
    public String getSchemaUrl() {
        return schemaUrl;
    }

    public void setSchemaUrl(final String schemaUrl) {
        this.schemaUrl = schemaUrl;
    }

    @Override
    public List<KeyValue> getAttributes() {
        return Collections.unmodifiableList(attributes);
    }

    public void setAttributes(final List<KeyValue> attributes) {
        this.attributes = attributes != null ? new ArrayList<>(attributes) : new ArrayList<>();
    }

    @Override
    public int getDroppedAttributesCount() {
        return droppedAttributesCount;
    }

    public void setDroppedAttributesCount(final int droppedAttributesCount) {
        if (droppedAttributesCount < 0) {
            throw new IllegalArgumentException("droppedAttributesCount must not be negative, got: " + droppedAttributesCount);
        }
        this.droppedAttributesCount = droppedAttributesCount;
    }
}
