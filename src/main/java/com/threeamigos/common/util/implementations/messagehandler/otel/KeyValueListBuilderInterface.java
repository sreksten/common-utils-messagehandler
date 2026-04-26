package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.util.List;

/**
 *
 * @author Stefano Reksten
 */
public interface KeyValueListBuilderInterface extends KeyValueBuilderInterface {

    KeyValueListBuilderInterface withEmpty(String name);

    KeyValueListBuilderInterface withEmpty(Names name);

    KeyValueListBuilderInterface withString(String name, String value);

    KeyValueListBuilderInterface withString(Names name, String value);

    KeyValueListBuilderInterface withBoolean(String name, boolean value);

    KeyValueListBuilderInterface withBoolean(Names name, boolean value);

    KeyValueListBuilderInterface withLong(String name, long value);

    KeyValueListBuilderInterface withLong(Names name, long value);

    KeyValueListBuilderInterface withDouble(String name, double value);

    KeyValueListBuilderInterface withDouble(Names name, double value);

    KeyValueListBuilderInterface withArray(String name, final List<AnyValue> value);

    KeyValueListBuilderInterface withArray(Names name, final List<AnyValue> value);

    KeyValueListBuilderInterface withKeyValueList(String name, final List<KeyValue> value);

    KeyValueListBuilderInterface withKeyValueList(Names name, final List<KeyValue> value);

    KeyValueListBuilderInterface withBytes(String name, final byte[] value);

    KeyValueListBuilderInterface withBytes(Names name, final byte[] value);

    List<KeyValue> build();

}
