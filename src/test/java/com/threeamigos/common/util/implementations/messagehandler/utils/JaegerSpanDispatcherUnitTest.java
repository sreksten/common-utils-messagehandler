package com.threeamigos.common.util.implementations.messagehandler.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("JaegerSpanDispatcher unit tests")
@Tag("unit")
@Tag("messageHandler")
class JaegerSpanDispatcherUnitTest {

    @Test
    @DisplayName("constructors should delegate OTLP configuration to parent dispatcher")
    void constructorsShouldDelegateConfigurationToParent() throws Exception {
        JaegerSpanDispatcher simple = new JaegerSpanDispatcher("http://localhost:4318/v1/traces");
        assertEquals("/v1/traces", ((URL) readField(simple, "endpoint")).getPath());
        assertNull(readField(simple, "username"));
        assertNull(readField(simple, "password"));
        assertNull(readField(simple, "bearerToken"));
        assertEquals(10_000, readField(simple, "connectTimeoutMillis"));
        assertEquals(10_000, readField(simple, "readTimeoutMillis"));
        assertTrue(((Map<?, ?>) readField(simple, "additionalHeaders")).isEmpty());

        JaegerSpanDispatcher basic = new JaegerSpanDispatcher(
                "http://localhost:4318/v1/traces", "alice", "secret");
        assertEquals("alice", readField(basic, "username"));
        assertEquals("secret", readField(basic, "password"));
        assertNull(readField(basic, "bearerToken"));

        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("X-Scope-OrgID", "tenant-j");
        JaegerSpanDispatcher full = new JaegerSpanDispatcher(
                "http://localhost:4318/v1/traces", "bob", "pw", "token-1",
                2_500, 3_500, headers);
        assertEquals("bob", readField(full, "username"));
        assertEquals("pw", readField(full, "password"));
        assertEquals("token-1", readField(full, "bearerToken"));
        assertEquals(2_500, readField(full, "connectTimeoutMillis"));
        assertEquals(3_500, readField(full, "readTimeoutMillis"));
        assertEquals("tenant-j", ((Map<?, ?>) readField(full, "additionalHeaders")).get("X-Scope-OrgID"));

        @SuppressWarnings("unchecked")
        Map<String, String> immutableHeaders = (Map<String, String>) readField(full, "additionalHeaders");
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> immutableHeaders.put("x", "y"));
        assertEquals(Collections.singletonMap("X-Scope-OrgID", "tenant-j"), immutableHeaders);
    }

    private static Object readField(final Object target, final String fieldName) throws Exception {
        Field field = OtlpSpanDispatcher.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
