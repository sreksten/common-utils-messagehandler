package com.threeamigos.common.util.implementations.messagehandler.otel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    @DisplayName("null keys should be ignored and null values normalized for common attributes")
    void nullKeysShouldBeIgnoredAndNullValuesNormalizedForCommonAttributes() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        TracerProvider provider = TracerProvider.builder()
                .resourceAttribute((String) null, "x")
                .commonAttribute(null, "x")
                .commonAttribute("common.k", null)
                .build();

        assertEquals(1, provider.getDefaultCommonAttributes().size());
        assertEquals("common.k", provider.getDefaultCommonAttributes().get(0).getKey());
        assertEquals("", provider.getDefaultCommonAttributes().get(0).getValue().asString());
    }

    @Test
    @DisplayName("blank semantic convention values should not be added as resource attributes")
    void blankSemanticConventionValuesShouldNotBeAddedAsResourceAttributes() {
        TracerProvider provider = TracerProvider.builder()
                .serviceName(" ")
                .serviceVersion(" ")
                .serviceInstanceId(" ")
                .deploymentEnvironment(" ")
                .build();

        assertNotNull(provider.getDefaultResource());
        assertTrue(provider.getDefaultResource().getAttributes().isEmpty());
    }
}
