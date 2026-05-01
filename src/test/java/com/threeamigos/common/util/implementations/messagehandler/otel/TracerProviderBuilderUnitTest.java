package com.threeamigos.common.util.implementations.messagehandler.otel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("TracerProviderBuilder unit tests")
@Tag("unit")
@Tag("messageHandler")
class TracerProviderBuilderUnitTest extends AbstractOtelValidatorLogTrapUnitTest {

    @org.junit.jupiter.api.AfterEach
    void restoreLenient() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(false);
    }

    @Test
    @DisplayName("invalid resource/common attribute keys should be ignored in lenient mode")
    void invalidResourceCommonAttributeKeysShouldBeIgnoredInLenientMode() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        TracerProvider provider = TracerProvider.builder()
                .resourceAttribute(" ", "x")
                .commonAttribute(" ", "x")
                .serviceName("svc")
                .build();

        assertNotNull(provider);
        assertEquals(0, provider.getDefaultCommonAttributes().size());
    }

    @Test
    @DisplayName("resourceAttribute with null tag and null value should be non-blocking in lenient mode")
    void resourceAttributeWithNullTagAndNullValueShouldBeNonBlockingInLenientMode() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        TracerProvider provider = TracerProvider.builder()
                .resourceAttribute((OTelTags) null, null)
                .resourceAttribute("k", null)
                .serviceName("svc")
                .build();

        assertNotNull(provider.getDefaultResource());
    }
}
