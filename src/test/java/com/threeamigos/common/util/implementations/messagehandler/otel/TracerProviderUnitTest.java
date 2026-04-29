package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;

@DisplayName("TracerProvider unit tests")
@Tag("unit")
@Tag("messageHandler")
class TracerProviderUnitTest {

    @Test
    @DisplayName("getGlobal should return singleton provider")
    void getGlobalShouldReturnSingletonProvider() {
        TracerProvider first = TracerProvider.getGlobal();
        TracerProvider second = TracerProvider.getGlobal();

        assertNotNull(first);
        assertSame(first, second);
    }

    @Test
    @DisplayName("createProvider should return a distinct provider instance")
    void createProviderShouldReturnDistinctProviderInstance() {
        TracerProvider global = TracerProvider.getGlobal();
        TracerProvider created = global.createProvider();

        assertNotNull(created);
        assertNotSame(global, created);
    }

    @Test
    @DisplayName("getTracer should handle null and non-null instrumentation names")
    void getTracerShouldHandleNullAndNonNullInstrumentationNames() {
        TracerProvider provider = TracerProvider.getGlobal();

        Tracer withNullName = provider.getTracer(null, "1.0.0", "schema", Collections.emptyList());
        Tracer withName = provider.getTracer("orders", "1.0.0", "schema", Collections.emptyList());

        assertNull(withNullName);
        assertNull(withName);
    }
}
