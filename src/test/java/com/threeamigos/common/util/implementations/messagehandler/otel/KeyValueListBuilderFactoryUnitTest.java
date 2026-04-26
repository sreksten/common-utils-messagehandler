package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("KeyValueListBuilder unit tests")
@Tag("unit")
@Tag("messageHandler")
class KeyValueListBuilderFactoryUnitTest {

    @Test
    @DisplayName("create() should return KeyValueListBuilderImpl")
    void getBuilderShouldReturnImplementation() {
        KeyValueListBuilderInterface builder = KeyValueListBuilderFactory.getBuilder();
        assertNotNull(builder);
        assertTrue(builder instanceof KeyValueListBuilderImpl);
    }

    @Test
    @DisplayName("all fluent methods should return same builder and build should return immutable snapshot")
    void allFluentMethodsShouldReturnSameBuilderAndBuildShouldReturnImmutableSnapshot() {
        KeyValueListBuilderImpl builder = new KeyValueListBuilderImpl();

        List<AnyValue> array = Collections.singletonList(AnyValueFactory.ofString("inner-array"));
        List<KeyValue> kvList = Collections.singletonList(new KeyValueImpl("inner-key", AnyValueFactory.ofLong(1)));

        KeyValueListBuilderInterface result = builder
                .withEmpty("empty.string")
                .withEmpty(Names.ATTR_SERVICE_NAME)
                .withString("string.string", "v")
                .withString(Names.ATTR_SERVICE_VERSION, "1.0.0")
                .withBoolean("bool.string", true)
                .withBoolean(Names.ATTR_TELEMETRY_SDK_LANGUAGE, false)
                .withLong("long.string", 123L)
                .withLong(Names.ATTR_HTTP_RESPONSE_STATUS_CODE, 200L)
                .withDouble("double.string", 1.25)
                .withDouble(Names.ATTR_TELEMETRY_SDK_VERSION, 2.5)
                .withArray("array.string", array)
                .withArray(Names.ATTR_TELEMETRY_SDK_NAME, array)
                .withKeyValueList("kvlist.string", kvList)
                .withKeyValueList(Names.ATTR_EXCEPTION_TYPE, kvList)
                .withBytes("bytes.string", new byte[] {1, 2, 3})
                .withBytes(Names.ATTR_SERVICE_NAMESPACE, new byte[] {4, 5, 6});

        assertSame(builder, result);

        List<KeyValue> firstBuild = builder.build();
        assertEquals(16, firstBuild.size());
        assertThrows(UnsupportedOperationException.class,
                () -> firstBuild.add(new KeyValueImpl("x", AnyValueFactory.ofString("y"))));

        builder.withString("after-build", "value");
        assertEquals(16, firstBuild.size());
        assertEquals(17, builder.build().size());

        assertEquals(Arrays.asList(
                "empty.string",
                "service.name",
                "string.string",
                "service.version",
                "bool.string",
                "telemetry.sdk.language",
                "long.string",
                "http.response.status_code",
                "double.string",
                "telemetry.sdk.version",
                "array.string",
                "telemetry.sdk.name",
                "kvlist.string",
                "exception.type",
                "bytes.string",
                "service.namespace"
        ), keys(firstBuild));
    }

    private static List<String> keys(final List<KeyValue> values) {
        java.util.List<String> out = new java.util.ArrayList<>(values.size());
        for (KeyValue kv : values) {
            out.add(kv.getKey());
        }
        return out;
    }
}
