package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable implementation of {@link InstrumentationScope}.
 * All fields default to {@code null} / empty list / {@code 0} and may be set via the corresponding setters.
 *
 * @author Stefano Reksten
 */
public class InstrumentationScopeImpl implements InstrumentationScope {

    private String name;
    private String version;
    private List<KeyValue> attributes = new ArrayList<>();
    private int droppedAttributesCount;

    @Override
    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public String getVersion() {
        return version;
    }

    public void setVersion(final String version) {
        this.version = version;
    }

    @Override
    public List<KeyValue> getAttributes() {
        return Collections.unmodifiableList(attributes);
    }

    public void setAttributes(final List<KeyValue> attributes) {
        this.attributes = attributes != null ? attributes : new ArrayList<>();
    }

    @Override
    public int getDroppedAttributesCount() {
        return droppedAttributesCount;
    }

    public void setDroppedAttributesCount(final int droppedAttributesCount) {
        this.droppedAttributesCount = droppedAttributesCount;
    }
}
