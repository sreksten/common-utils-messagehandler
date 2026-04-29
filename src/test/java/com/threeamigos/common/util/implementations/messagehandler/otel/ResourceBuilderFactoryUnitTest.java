package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.Entity;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ResourceBuilderFactory unit tests")
@Tag("unit")
@Tag("messageHandler")
class ResourceBuilderFactoryUnitTest {

    @Test
    @DisplayName("getBuilder() should return ResourceBuilderImpl")
    void getBuilderShouldReturnResourceBuilderImpl() {
        assertTrue(ResourceBuilderFactory.getBuilder() instanceof ResourceBuilderImpl);
    }

    @Test
    @DisplayName("builder should apply overriding attributes and append telemetry defaults")
    void builderShouldApplyOverridingAttributesAndAppendTelemetryDefaults() {
        ResourceBuilderImpl builder = new ResourceBuilderImpl();
        builder.withServiceName("checkout");
        builder.withServiceNamespace("payments");
        builder.withServiceVersion("1.2.3");
        builder.withServiceInstanceId("instance-1");
        builder.withDeploymentEnvironmentName("prod");
        builder.withSchemaUrl("https://opentelemetry.io/schemas/1.27.0");
        builder.withNoEntity();

        builder.withString(OTelTags.SERVICE_NAME.getValue(), "old-name");
        builder.withString(OTelTags.SERVICE_NAMESPACE.getValue(), "old-namespace");
        builder.withString(OTelTags.SERVICE_VERSION.getValue(), "old-version");
        builder.withString(OTelTags.SERVICE_INSTANCE_ID.getValue(), "old-instance");
        builder.withString(OTelTags.DEPLOYMENT_ENVIRONMENT_NAME.getValue(), "old-env");
        builder.withString("custom.attribute", "custom-value");

        Resource resource = builder.build();
        Map<String, String> attributes = stringValuesByKey(resource.getAttributes());

        assertEquals("https://opentelemetry.io/schemas/1.27.0", resource.getSchemaUrl());
        assertTrue(resource.getEntities().isEmpty());
        assertEquals("checkout", attributes.get(OTelTags.SERVICE_NAME.getValue()));
        assertEquals("payments", attributes.get(OTelTags.SERVICE_NAMESPACE.getValue()));
        assertEquals("1.2.3", attributes.get(OTelTags.SERVICE_VERSION.getValue()));
        assertEquals("instance-1", attributes.get(OTelTags.SERVICE_INSTANCE_ID.getValue()));
        assertEquals("prod", attributes.get(OTelTags.DEPLOYMENT_ENVIRONMENT_NAME.getValue()));
        assertEquals("custom-value", attributes.get("custom.attribute"));

        assertEquals("java", attributes.get(OTelTags.TELEMETRY_SDK_LANGUAGE.getValue()));
        assertEquals("message-handler", attributes.get(OTelTags.TELEMETRY_SDK_NAME.getValue()));
        assertEquals("1.0.0", attributes.get(OTelTags.TELEMETRY_SDK_VERSION.getValue()));
    }

    @Test
    @DisplayName("builder should normalize null or blank values in with... methods")
    void builderShouldNormalizeNullOrBlankValuesInWithMethods() {
        ResourceBuilderImpl builder = new ResourceBuilderImpl();
        assertDoesNotThrow(() -> builder
                .withServiceName(null)
                .withServiceNamespace("")
                .withServiceVersion(" ")
                .withServiceInstanceId(null)
                .withDeploymentEnvironmentName("")
                .withSchemaUrl("   ")
                .withNoEntity()
                .build());

        Resource resource = builder.build();
        Map<String, String> attributes = stringValuesByKey(resource.getAttributes());

        assertEquals("unknown", attributes.get(OTelTags.SERVICE_NAME.getValue()));
        assertEquals("unknown", attributes.get(OTelTags.SERVICE_NAMESPACE.getValue()));
        assertEquals("unknown", attributes.get(OTelTags.SERVICE_VERSION.getValue()));
        assertEquals("unknown", attributes.get(OTelTags.SERVICE_INSTANCE_ID.getValue()));
        assertEquals("unknown", attributes.get(OTelTags.DEPLOYMENT_ENVIRONMENT_NAME.getValue()));
        assertEquals("unknown", resource.getSchemaUrl());

        assertThrows(NullPointerException.class, () -> new ResourceBuilderImpl()
                .withServiceName("checkout")
                .withNoServiceNamespace()
                .withNoServiceVersion()
                .withNoServiceInstanceId()
                .withNoDeploymentEnvironmentName()
                .withNoSchemaUrl()
                .withEntity(null));
    }

    @Test
    @DisplayName("builder should support no-op steps and keep entities")
    void builderShouldSupportNoOpStepsAndKeepEntities() {
        Entity entity = EntityFactory.create(
                "service",
                "https://opentelemetry.io/schemas/1.27.0",
                java.util.Collections.singletonList(new KeyValueImpl("service.instance.id", AnyValueFactory.ofString("instance-9"))),
                java.util.Collections.singletonList(new KeyValueImpl("service.name", AnyValueFactory.ofString("checkout")))
        );

        ResourceBuilderImpl builder = new ResourceBuilderImpl();
        builder.withServiceName("checkout");
        builder.withNoServiceNamespace();
        builder.withNoServiceVersion();
        builder.withNoServiceInstanceId();
        builder.withNoDeploymentEnvironmentName();
        builder.withNoSchemaUrl();
        builder.withEntity(entity);
        builder.withNoEntity();

        Resource resource = builder.build();
        Map<String, String> attributes = stringValuesByKey(resource.getAttributes());

        assertEquals("https://opentelemetry.io/schemas/1.27.0", resource.getSchemaUrl());
        assertEquals(1, resource.getEntities().size());
        assertEquals("service", resource.getEntities().get(0).getType());
        assertFalse(attributes.containsKey(OTelTags.SERVICE_NAME.getValue()));
    }

    private static Map<String, String> stringValuesByKey(final java.util.List<KeyValue> keyValues) {
        Map<String, String> out = new HashMap<>(keyValues.size());
        for (KeyValue keyValue : keyValues) {
            out.put(keyValue.getKey(), keyValue.getValue().asString());
        }
        return out;
    }
}
