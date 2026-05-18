package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.Filter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("Telemetry API default methods")
@Tag("unit")
@Tag("messageHandler")
class TelemetryApiDefaultMethodsUnitTest {

    @Test
    @DisplayName("tracer convenience methods should not be default placeholders")
    void tracerConvenienceMethodsShouldNotBeDefaultPlaceholders() throws Exception {
        assertNotDefault("getInstrumentationScope");
        assertNotDefault("getLogRecordFactory");
        assertNotDefault("getConsoleMessageHandler");
        assertNotDefault("getConsoleMessageHandler", Filter.class);
        assertNotDefault("getFileMessageHandler", String.class);
        assertNotDefault("getFileMessageHandler", String.class, Filter.class);
        assertNotDefault("getFileMessageHandler", File.class);
        assertNotDefault("getFileMessageHandler", File.class, Filter.class);
        assertNotDefault("getInMemoryMessageHandler");
        assertNotDefault("getInMemoryMessageHandler", Filter.class);
        assertNotDefault("getJULMessageHandler", java.util.logging.Logger.class);
        assertNotDefault("getJULMessageHandler", java.util.logging.Logger.class, Filter.class);
        assertNotDefault("getLog4JMessageHandler", org.apache.logging.log4j.Logger.class);
        assertNotDefault("getLog4JMessageHandler", org.apache.logging.log4j.Logger.class, Filter.class);
        assertNotDefault("getSLF4JMessageHandler", org.slf4j.Logger.class);
        assertNotDefault("getSLF4JMessageHandler", org.slf4j.Logger.class, Filter.class);
        assertNotDefault("getSwingMessageHandler");
        assertNotDefault("getSwingMessageHandler", Filter.class);
        assertNotDefault("getJaegerMessageHandler", String.class);
        assertNotDefault("getJaegerMessageHandler", String.class, Filter.class);
        assertNotDefault("getJaegerMessageHandler", String.class, String.class, String.class);
        assertNotDefault("getJaegerMessageHandler",
                String.class, String.class, String.class, String.class, int.class, int.class, java.util.Map.class,
                boolean.class, int.class, boolean.class);
        assertNotDefault("getJaegerMessageHandler",
                String.class, String.class, String.class, String.class, int.class, int.class, java.util.Map.class,
                boolean.class, int.class, boolean.class, Filter.class);
        assertNotDefault("getGrafanaMessageHandler", String.class);
        assertNotDefault("getGrafanaMessageHandler", String.class, Filter.class);
        assertNotDefault("getGrafanaMessageHandler", String.class, String.class, String.class);
        assertNotDefault("getGrafanaMessageHandler",
                String.class, String.class, String.class, String.class, int.class, int.class, java.util.Map.class,
                boolean.class, int.class, boolean.class);
        assertNotDefault("getGrafanaMessageHandler",
                String.class, String.class, String.class, String.class, int.class, int.class, java.util.Map.class,
                boolean.class, int.class, boolean.class, Filter.class);
        assertNotDefault("getVoidMessageHandler");
    }

    private static void assertNotDefault(final String methodName,
                                         final Class<?>... parameterTypes) throws Exception {
        Method method = Tracer.class.getMethod(methodName, parameterTypes);
        assertFalse(method.isDefault(),
                "Tracer#" + methodName + " should be abstract and implemented by concrete tracer implementations");
    }
}
