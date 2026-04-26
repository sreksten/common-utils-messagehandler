package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Stefano Reksten
 */
class KeyValueBuilderImpl implements KeyValueBuilderInterface {

    protected final List<KeyValue> attributes = new ArrayList<>();

    @Override
    public KeyValueBuilderInterface withEmpty(String name) {
        attributes.add(KeyValueFactory.of(name, AnyValueFactory.empty()));
        return this;
    }

    @Override
    public KeyValueBuilderInterface withEmpty(Names name) {
        attributes.add(KeyValueFactory.of(name, AnyValueFactory.empty()));
        return this;
    }

    @Override
    public KeyValueBuilderInterface withString(String name, String value) {
        attributes.add(KeyValueFactory.of(name, AnyValueFactory.ofNullableString(value)));
        return this;
    }

    @Override
    public KeyValueBuilderInterface withString(Names name, String value) {
        attributes.add(KeyValueFactory.of(name, AnyValueFactory.ofNullableString(value)));
        return this;
    }

    @Override
    public KeyValueBuilderInterface withBoolean(String name, boolean value) {
        attributes.add(KeyValueFactory.of(name, AnyValueFactory.ofBoolean(value)));
        return this;
    }

    @Override
    public KeyValueBuilderInterface withBoolean(Names name, boolean value) {
        attributes.add(KeyValueFactory.of(name, AnyValueFactory.ofBoolean(value)));
        return this;
    }

    @Override
    public KeyValueBuilderInterface withLong(String name, long value) {
        attributes.add(KeyValueFactory.of(name, AnyValueFactory.ofLong(value)));
        return this;
    }

    @Override
    public KeyValueBuilderInterface withLong(Names name, long value) {
        attributes.add(KeyValueFactory.of(name, AnyValueFactory.ofLong(value)));
        return this;
    }

    @Override
    public KeyValueBuilderInterface withDouble(String name, double value) {
        attributes.add(KeyValueFactory.of(name, AnyValueFactory.ofDouble(value)));
        return this;
    }

    @Override
    public KeyValueBuilderInterface withDouble(Names name, double value) {
        attributes.add(KeyValueFactory.of(name, AnyValueFactory.ofDouble(value)));
        return this;
    }

    @Override
    public KeyValueBuilderInterface withArray(String name, List<AnyValue> value) {
        attributes.add(KeyValueFactory.of(name, AnyValueFactory.ofArray(value)));
        return this;
    }

    @Override
    public KeyValueBuilderInterface withArray(Names name, List<AnyValue> value) {
        attributes.add(KeyValueFactory.of(name, AnyValueFactory.ofArray(value)));
        return this;
    }

    @Override
    public KeyValueBuilderInterface withKeyValueList(String name, List<KeyValue> value) {
        attributes.add(KeyValueFactory.of(name, AnyValueFactory.ofKvList(value)));
        return this;
    }

    @Override
    public KeyValueBuilderInterface withKeyValueList(Names name, List<KeyValue> value) {
        attributes.add(KeyValueFactory.of(name, AnyValueFactory.ofKvList(value)));
        return this;
    }

    @Override
    public KeyValueBuilderInterface withBytes(String name, byte[] value) {
        attributes.add(KeyValueFactory.of(name, AnyValueFactory.ofBytes(value)));
        return this;
    }

    @Override
    public KeyValueBuilderInterface withBytes(Names name, byte[] value) {
        attributes.add(KeyValueFactory.of(name, AnyValueFactory.ofBytes(value)));
        return this;
    }
}
