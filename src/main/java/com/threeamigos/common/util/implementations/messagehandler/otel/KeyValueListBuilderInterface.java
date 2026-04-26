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

    KeyValueListBuilderInterface withEmpty(OTelTags name);

    KeyValueListBuilderInterface withString(String name, String value);

    KeyValueListBuilderInterface withString(OTelTags name, String value);

    KeyValueListBuilderInterface withBoolean(String name, boolean value);

    KeyValueListBuilderInterface withBoolean(OTelTags name, boolean value);

    KeyValueListBuilderInterface withLong(String name, long value);

    KeyValueListBuilderInterface withLong(OTelTags name, long value);

    KeyValueListBuilderInterface withDouble(String name, double value);

    KeyValueListBuilderInterface withDouble(OTelTags name, double value);

    KeyValueListBuilderInterface withArray(String name, final List<AnyValue> value);

    KeyValueListBuilderInterface withArray(OTelTags name, final List<AnyValue> value);

    KeyValueListBuilderInterface withKeyValueList(String name, final List<KeyValue> value);

    KeyValueListBuilderInterface withKeyValueList(OTelTags name, final List<KeyValue> value);

    KeyValueListBuilderInterface withBytes(String name, final byte[] value);

    KeyValueListBuilderInterface withBytes(OTelTags name, final byte[] value);

    List<KeyValue> build();

}
