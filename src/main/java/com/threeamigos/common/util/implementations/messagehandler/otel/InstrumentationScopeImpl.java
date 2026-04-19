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
    private String schemaUrl;
    private List<KeyValue> attributes = new ArrayList<>();
    private int droppedAttributesCount;

    @Override
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the instrumentation scope.
     * <p>
     * The OTel specification states that the name SHOULD be set. Passing {@code null} is accepted
     * but constitutes a spec deviation: the {@code "name"} field will be omitted from the
     * serialized {@code ScopeLogs} scope object, making the emitting library unidentifiable.
     *
     * @param name the instrumentation scope name, e.g. the library package name; {@code null} is
     *             permitted but discouraged.
     */
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
