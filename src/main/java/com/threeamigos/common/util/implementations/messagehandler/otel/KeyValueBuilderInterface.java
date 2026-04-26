package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.util.*;

/**
 *
 * @author Stefano Reksten
 */
interface KeyValueBuilderInterface {

    KeyValueBuilderInterface withEmpty(String name);
    KeyValueBuilderInterface withEmpty(Names name);

    KeyValueBuilderInterface withString(String name, String value);
    KeyValueBuilderInterface withString(Names name, String value);

    KeyValueBuilderInterface withBoolean(String name, boolean value);
    KeyValueBuilderInterface withBoolean(Names name, boolean value);

    KeyValueBuilderInterface withLong(String name, long value);
    KeyValueBuilderInterface withLong(Names name, long value);

    KeyValueBuilderInterface withDouble(String name, double value);
    KeyValueBuilderInterface withDouble(Names name, double value);

    KeyValueBuilderInterface withArray(String name, final List<AnyValue> value);
    KeyValueBuilderInterface withArray(Names name, final List<AnyValue> value);

    KeyValueBuilderInterface withKeyValueList(String name, final List<KeyValue> value);
    KeyValueBuilderInterface withKeyValueList(Names name, final List<KeyValue> value);

    KeyValueBuilderInterface withBytes(String name, final byte[] value);
    KeyValueBuilderInterface withBytes(Names name, final byte[] value);
}
