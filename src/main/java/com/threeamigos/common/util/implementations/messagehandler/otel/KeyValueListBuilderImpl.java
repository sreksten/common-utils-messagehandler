package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Stefano Reksten
 */
class KeyValueListBuilderImpl extends KeyValueBuilderImpl implements KeyValueListBuilderInterface {

    @Override
    public KeyValueListBuilderInterface withEmpty(final String name) {
        super.withEmpty(name);
        return this;
    }

    @Override
    public KeyValueListBuilderInterface withEmpty(final Names name) {
        super.withEmpty(name);
        return this;
    }

    @Override
    public KeyValueListBuilderInterface withString(final String name, final String value) {
        super.withString(name, value);
        return this;
    }

    @Override
    public KeyValueListBuilderInterface withString(final Names name, final String value) {
        super.withString(name, value);
        return this;
    }

    @Override
    public KeyValueListBuilderInterface withBoolean(final String name, final boolean value) {
        super.withBoolean(name, value);
        return this;
    }

    @Override
    public KeyValueListBuilderInterface withBoolean(final Names name, final boolean value) {
        super.withBoolean(name, value);
        return this;
    }

    @Override
    public KeyValueListBuilderInterface withLong(final String name, final long value) {
        super.withLong(name, value);
        return this;
    }

    @Override
    public KeyValueListBuilderInterface withLong(final Names name, final long value) {
        super.withLong(name, value);
        return this;
    }

    @Override
    public KeyValueListBuilderInterface withDouble(final String name, final double value) {
        super.withDouble(name, value);
        return this;
    }

    @Override
    public KeyValueListBuilderInterface withDouble(final Names name, final double value) {
        super.withDouble(name, value);
        return this;
    }

    @Override
    public KeyValueListBuilderInterface withArray(final String name, final List<AnyValue> value) {
        super.withArray(name, value);
        return this;
    }

    @Override
    public KeyValueListBuilderInterface withArray(final Names name, final List<AnyValue> value) {
        super.withArray(name, value);
        return this;
    }

    @Override
    public KeyValueListBuilderInterface withKeyValueList(final String name, final List<KeyValue> value) {
        super.withKeyValueList(name, value);
        return this;
    }

    @Override
    public KeyValueListBuilderInterface withKeyValueList(final Names name, final List<KeyValue> value) {
        super.withKeyValueList(name, value);
        return this;
    }

    @Override
    public KeyValueListBuilderInterface withBytes(final String name, final byte[] value) {
        super.withBytes(name, value);
        return this;
    }

    @Override
    public KeyValueListBuilderInterface withBytes(final Names name, final byte[] value) {
        super.withBytes(name, value);
        return this;
    }

    @Override
    public List<KeyValue> build() {
        return Collections.unmodifiableList(new ArrayList<>(attributes));
    }
}
