package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Entity;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EntityBuilderFactory unit tests")
@Tag("unit")
@Tag("messageHandler")
class EntityBuilderFactoryUnitTest {

    @Test
    @DisplayName("getBuilder() should return EntityBuilderImpl")
    void getBuilderShouldReturnEntityBuilderImpl() {
        assertTrue(EntityBuilderFactory.getBuilder() instanceof EntityBuilderImpl);
    }

    @Test
    @DisplayName("type-specific builder methods should use expected OTel keys")
    void typeSpecificBuilderMethodsShouldUseExpectedOtelKeys() {
        EntityBuilderImpl serviceBuilder = new EntityBuilderImpl();
        serviceBuilder.withServiceType();
        serviceBuilder.withServiceName("checkout");
        Entity service = serviceBuilder.build();
        assertEntity("service", null, service, OTelTags.SERVICE_NAME.getValue());

        EntityBuilderImpl processBuilder = new EntityBuilderImpl();
        processBuilder.withProcessType();
        processBuilder.withProcessPid(123L);
        processBuilder.withProcessStartTime(456L);
        Entity process = processBuilder.build();
        assertEntity("process", null, process, OTelTags.PROCESS_PID.getValue(), OTelTags.PROCESS_CREATION_TIME.getValue());

        EntityBuilderImpl hostBuilder = new EntityBuilderImpl();
        hostBuilder.withHostType();
        hostBuilder.withHostId("host-1");
        hostBuilder.withHostName("host-a");
        Entity host = hostBuilder.build();
        assertEntity("host", null, host, OTelTags.HOST_ID.getValue(), OTelTags.HOST_NAME.getValue());

        EntityBuilderImpl containerBuilder = new EntityBuilderImpl();
        containerBuilder.withContainerType();
        containerBuilder.withContainerId("container-1");
        Entity container = containerBuilder.build();
        assertEntity("container", null, container, OTelTags.CONTAINER_ID.getValue());

        EntityBuilderImpl k8sClusterBuilder = new EntityBuilderImpl();
        k8sClusterBuilder.withK8sClusterType();
        k8sClusterBuilder.withClusterName("cluster-a");
        Entity k8sCluster = k8sClusterBuilder.build();
        assertEntity("k8s.cluster", null, k8sCluster, OTelTags.K8S_CLUSTER_NAME.getValue());

        EntityBuilderImpl k8sNodeBuilder = new EntityBuilderImpl();
        k8sNodeBuilder.withK8SNodeType();
        k8sNodeBuilder.withNodeUid("node-1");
        Entity k8sNode = k8sNodeBuilder.build();
        assertEntity("k8s.node", null, k8sNode, OTelTags.K8S_NODE_UID.getValue());

        EntityBuilderImpl k8sNamespaceBuilder = new EntityBuilderImpl();
        k8sNamespaceBuilder.withK8sNamespaceType();
        k8sNamespaceBuilder.withNamespaceName("payments");
        k8sNamespaceBuilder.withClusterName("cluster-a");
        Entity k8sNamespace = k8sNamespaceBuilder.build();
        assertEntity("k8s.namespace", null, k8sNamespace, OTelTags.K8S_NAMESPACE_NAME.getValue(), OTelTags.K8S_CLUSTER_NAME.getValue());

        EntityBuilderImpl k8sPodBuilder = new EntityBuilderImpl();
        k8sPodBuilder.withK8sPodType();
        k8sPodBuilder.withPodUid("pod-1");
        Entity k8sPod = k8sPodBuilder.build();
        assertEntity("k8s.pod", null, k8sPod, OTelTags.K8S_POD_UID.getValue());

        EntityBuilderImpl k8sDeploymentBuilder = new EntityBuilderImpl();
        k8sDeploymentBuilder.withK8sDeploymentType();
        k8sDeploymentBuilder.withDeploymentName("checkout-deployment");
        k8sDeploymentBuilder.withNamespaceName("payments");
        k8sDeploymentBuilder.withClusterName("cluster-a");
        Entity k8sDeployment = k8sDeploymentBuilder.build();
        assertEntity(
                "k8s.deployment",
                null,
                k8sDeployment,
                OTelTags.K8S_DEPLOYMENT_NAME.getValue(),
                OTelTags.K8S_NAMESPACE_NAME.getValue(),
                OTelTags.K8S_CLUSTER_NAME.getValue());

        EntityBuilderImpl cloudPlatformBuilder = new EntityBuilderImpl();
        cloudPlatformBuilder.withCloudPlatformType();
        cloudPlatformBuilder.withCloudProvider("aws");
        cloudPlatformBuilder.withCloudPlatform("aws_eks");
        Entity cloudPlatform = cloudPlatformBuilder.build();
        assertEntity("cloud.platform", null, cloudPlatform, OTelTags.CLOUD_PROVIDER.getValue(), OTelTags.CLOUD_PLATFORM.getValue());

        EntityBuilderImpl cloudRegionBuilder = new EntityBuilderImpl();
        cloudRegionBuilder.withCloudRegionType();
        cloudRegionBuilder.withCloudRegion("eu-west-1");
        cloudRegionBuilder.withCloudProvider("aws");
        cloudRegionBuilder.withCloudAccountId("12345");
        Entity cloudRegion = cloudRegionBuilder.build();
        assertEntity(
                "cloud.region",
                null,
                cloudRegion,
                OTelTags.CLOUD_REGION.getValue(),
                OTelTags.CLOUD_PROVIDER.getValue(),
                OTelTags.CLOUD_ACCOUNT_ID.getValue());

        EntityBuilderImpl cloudAccountBuilder = new EntityBuilderImpl();
        cloudAccountBuilder.withCloudAccountType();
        cloudAccountBuilder.withCloudAccountId("67890");
        cloudAccountBuilder.withCloudPlatform("gcp_compute_engine");
        Entity cloudAccount = cloudAccountBuilder.build();
        assertEntity("cloud.account", null, cloudAccount, OTelTags.CLOUD_ACCOUNT_ID.getValue(), OTelTags.CLOUD_PLATFORM.getValue());

        EntityBuilderImpl browserBuilder = new EntityBuilderImpl();
        browserBuilder.withBrowserType();
        browserBuilder.withBrowserBrands("Chromium");
        browserBuilder.withBrowserPlatform("Linux");
        Entity browser = browserBuilder.build();
        assertEntity("browser", null, browser, OTelTags.BROWSER_BRANDS.getValue(), OTelTags.BROWSER_PLATFORM.getValue());

        EntityBuilderImpl deviceBuilder = new EntityBuilderImpl();
        deviceBuilder.withDeviceType();
        deviceBuilder.withDeviceId("device-1");
        Entity device = deviceBuilder.build();
        assertEntity("device", null, device, OTelTags.DEVICE_ID.getValue());
    }

    @Test
    @DisplayName("withTelemetryType() should build telemetry.sdk entity with default sdk attributes")
    void withTelemetryTypeShouldBuildTelemetryEntityWithDefaultSdkAttributes() {
        EntityBuilderImpl builder = new EntityBuilderImpl();
        builder.withTelemetryType();

        Entity entity = builder.build();
        Map<String, String> id = stringValuesByKey(entity.getId());

        assertEquals("telemetry.sdk", entity.getType());
        assertNull(entity.getSchemaUrl());
        assertEquals(3, id.size());
        assertEquals("java", id.get(OTelTags.TELEMETRY_SDK_LANGUAGE.getValue()));
        assertEquals("message-handler", id.get(OTelTags.TELEMETRY_SDK_NAME.getValue()));
        assertEquals("1.0.0", id.get(OTelTags.TELEMETRY_SDK_VERSION.getValue()));
    }

    @Test
    @DisplayName("generic builder API should support all id and description overloads")
    void genericBuilderApiShouldSupportAllIdAndDescriptionOverloads() {
        List<AnyValue> anyValueArray = Arrays.asList(
                AnyValueFactory.ofString("x"),
                AnyValueFactory.ofLong(2L),
                AnyValueFactory.ofBoolean(true)
        );
        List<KeyValue> keyValueList = Collections.singletonList(
                new KeyValueImpl("inner.key", AnyValueFactory.ofString("inner.value"))
        );

        EntityBuilderImpl builder = new EntityBuilderImpl();
        builder.withType("custom.entity");
        builder.withSchemaUrl("https://opentelemetry.io/schemas/1.27.0");
        builder.withNoSchemaUrl();

        builder.withId(Collections.singletonList(new KeyValueImpl("id.list.base", AnyValueFactory.ofString("base"))));
        builder.withIdString("id.string", "value");
        builder.withIdString(OTelTags.URL_SCHEME, "https");
        builder.withIdBoolean("id.bool", true);
        builder.withIdBoolean(OTelTags.PROCESS_INTERACTIVE, false);
        builder.withIdLong("id.long", 123L);
        builder.withIdLong(OTelTags.SERVER_PORT, 443L);
        builder.withIdDouble("id.double", 12.5);
        builder.withIdDouble(OTelTags.CODE_COLUMN_NUMBER, 7.5);
        builder.withIdArray("id.array", anyValueArray);
        builder.withIdArray(OTelTags.HOST_IP, anyValueArray);
        builder.withIdKeyValueList("id.kvlist", keyValueList);
        builder.withIdKeyValueList(OTelTags.HTTP_REQUEST_METHOD, keyValueList);
        builder.withIdBytes("id.bytes", new byte[] {1, 2, 3});
        builder.withIdBytes(OTelTags.CLIENT_ADDRESS, new byte[] {4, 5, 6});

        builder.withDescription(Collections.singletonList(new KeyValueImpl("desc.list.base", AnyValueFactory.ofString("base"))));
        builder.withDescriptionString("desc.string", "value");
        builder.withDescriptionString(OTelTags.EXCEPTION_MESSAGE, "boom");
        builder.withDescriptionBoolean("desc.bool", true);
        builder.withDescriptionBoolean(OTelTags.ERROR_TYPE, false);
        builder.withDescriptionLong("desc.long", 456L);
        builder.withDescriptionLong(OTelTags.PROCESS_PID, 789L);
        builder.withDescriptionDouble("desc.double", 1.5);
        builder.withDescriptionDouble(OTelTags.CODE_LINE_NUMBER, 2.5);
        builder.withDescriptionArray("desc.array", anyValueArray);
        builder.withDescriptionArray(OTelTags.HOST_MAC, anyValueArray);
        builder.withDescriptionKeyValueList("desc.kvlist", keyValueList);
        builder.withDescriptionKeyValueList(OTelTags.GCP_HTTP_REQUEST, keyValueList);
        builder.withDescriptionBytes("desc.bytes", new byte[] {9, 8, 7});
        builder.withDescriptionBytes(OTelTags.SERVICE_NAMESPACE, new byte[] {6, 5, 4});

        Entity entity = builder.build();

        assertEquals("custom.entity", entity.getType());
        assertEquals("https://opentelemetry.io/schemas/1.27.0", entity.getSchemaUrl());
        assertEquals(15, entity.getId().size());
        assertEquals(15, entity.getDescription().size());
        assertEquals(15, new HashSet<>(keys(entity.getId())).size());
        assertEquals(15, new HashSet<>(keys(entity.getDescription())).size());
    }

    @Test
    @DisplayName("builder should normalize null or blank type and schemaUrl")
    void builderShouldNormalizeNullOrBlankTypeAndSchemaUrl() {
        EntityBuilderImpl nullTypeBuilder = new EntityBuilderImpl();
        assertDoesNotThrow(() -> nullTypeBuilder.withType(null));
        Entity nullTypeEntity = nullTypeBuilder.withId(Collections.emptyList()).build();
        assertEquals("unknown", nullTypeEntity.getType());

        EntityBuilderImpl blankTypeBuilder = new EntityBuilderImpl();
        assertDoesNotThrow(() -> blankTypeBuilder.withType("   "));
        Entity blankTypeEntity = blankTypeBuilder.withId(Collections.emptyList()).build();
        assertEquals("unknown", blankTypeEntity.getType());

        EntityBuilderImpl nullSchemaBuilder = new EntityBuilderImpl();
        assertDoesNotThrow(() -> nullSchemaBuilder.withType("custom.entity").withSchemaUrl(null));
        Entity nullSchemaEntity = nullSchemaBuilder.withIdString("id", "value").build();
        assertEquals("unknown", nullSchemaEntity.getSchemaUrl());

        EntityBuilderImpl blankSchemaBuilder = new EntityBuilderImpl();
        assertDoesNotThrow(() -> blankSchemaBuilder.withType("custom.entity").withSchemaUrl(""));
        Entity blankSchemaEntity = blankSchemaBuilder.withIdString("id", "value").build();
        assertEquals("unknown", blankSchemaEntity.getSchemaUrl());
    }

    private static void assertEntity(final String expectedType,
                                     final String expectedSchemaUrl,
                                     final Entity entity,
                                     final String... expectedIdKeys) {
        assertEquals(expectedType, entity.getType());
        assertEquals(expectedSchemaUrl, entity.getSchemaUrl());

        Set<String> expected = new HashSet<>(Arrays.asList(expectedIdKeys));
        Set<String> actual = new HashSet<>(keys(entity.getId()));
        assertEquals(expected, actual);
    }

    private static List<String> keys(final List<KeyValue> keyValues) {
        List<String> out = new ArrayList<>(keyValues.size());
        for (KeyValue keyValue : keyValues) {
            out.add(keyValue.getKey());
        }
        return out;
    }

    private static Map<String, String> stringValuesByKey(final List<KeyValue> keyValues) {
        Map<String, String> out = new HashMap<>(keyValues.size());
        for (KeyValue keyValue : keyValues) {
            out.put(keyValue.getKey(), keyValue.getValue().asString());
        }
        return out;
    }
}
