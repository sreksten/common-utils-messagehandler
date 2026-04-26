package com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues;

import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("OTel known values enums unit tests")
@Tag("unit")
@Tag("messageHandler")
class OTelKnownValuesEnumsUnitTest {

    @Test
    @DisplayName("known values enums should expose expected values and tags")
    void knownValuesEnumsShouldExposeExpectedValuesAndTags() {
        assertKnownValues(
                HostArchKnownValuesEnum.values(),
                OTelTags.HOST_ARCH,
                "amd64",
                "arm32",
                "arm64",
                "ia64",
                "ppc32",
                "ppc64",
                "s390x",
                "x86");

        assertKnownValues(
                CloudProviderKnownValuesEnum.values(),
                OTelTags.CLOUD_PROVIDER,
                "akamai_cloud",
                "alibaba_cloud",
                "aws",
                "azure",
                "gcp",
                "heroku",
                "hetzner",
                "ibm_cloud",
                "oracle_cloud",
                "tencent_cloud",
                "vultr");

        assertKnownValues(
                CloudPlatformKnownValuesEnum.values(),
                OTelTags.CLOUD_PLATFORM,
                "akamai_cloud.compute",
                "alibaba_cloud_ecs",
                "alibaba_cloud_fc",
                "alibaba_cloud_openshift",
                "aws_app_runner",
                "aws_ec2",
                "aws_ecs",
                "aws_eks",
                "aws_elastic_beanstalk",
                "aws_lambda",
                "aws_openshift",
                "azure.aks",
                "azure.app_service",
                "azure.container_apps",
                "azure.container_instances",
                "azure.functions",
                "azure.openshift",
                "azure.vm",
                "gcp.agent_engine",
                "gcp_app_engine",
                "gcp_bare_metal_solution",
                "gcp_cloud_functions",
                "gcp_cloud_run",
                "gcp_compute_engine",
                "gcp_kubernetes_engine",
                "gcp_openshift",
                "hetzner.cloud_server",
                "ibm_cloud_openshift",
                "oracle_cloud_compute",
                "oracle_cloud_oke",
                "tencent_cloud_cvm",
                "tencent_cloud_eks",
                "tencent_cloud_scf",
                "vultr.cloud_compute");

        assertKnownValues(
                TelemetrySdkLanguageKnownValuesEnum.values(),
                OTelTags.TELEMETRY_SDK_LANGUAGE,
                "cpp",
                "dotnet",
                "erlang",
                "go",
                "java",
                "nodejs",
                "php",
                "python",
                "ruby",
                "rust",
                "swift",
                "webjs");

        assertKnownValues(
                ProcessContextSwitchTypeKnownValuesEnum.values(),
                OTelTags.PROCESS_CONTEXT_SWITCH_TYPE,
                "voluntary",
                "involuntary");

        assertKnownValues(
                ProcessStateKnownValuesEnum.values(),
                OTelTags.PROCESS_STATE,
                "running",
                "sleeping",
                "stopped",
                "defunct");

        assertKnownValues(
                K8sContainerStatusStateKnownValuesEnum.values(),
                OTelTags.K8S_CONTAINER_STATUS_STATE,
                "running",
                "waiting",
                "terminated");

        assertKnownValues(
                K8sNamespacePhaseKnownValuesEnum.values(),
                OTelTags.K8S_NAMESPACE_PHASE,
                "active",
                "terminating");

        assertKnownValues(
                K8sNodeConditionStatusKnownValuesEnum.values(),
                OTelTags.K8S_NODE_CONDITION_STATUS,
                "true",
                "false",
                "unknown");

        assertKnownValues(
                K8sNodeConditionTypeKnownValuesEnum.values(),
                OTelTags.K8S_NODE_CONDITION_TYPE,
                "DiskPressure",
                "MemoryPressure",
                "NetworkUnavailable",
                "PIDPressure",
                "Ready");

        assertKnownValues(
                K8sPodStatusPhaseKnownValuesEnum.values(),
                OTelTags.K8S_POD_STATUS_PHASE,
                "Pending",
                "Running",
                "Succeeded",
                "Failed",
                "Unknown");

        assertKnownValues(
                K8sServiceEndpointAddressTypeKnownValuesEnum.values(),
                OTelTags.K8S_SERVICE_ENDPOINT_ADDRESS_TYPE,
                "IPv4",
                "IPv6",
                "FQDN");

        assertKnownValues(
                K8sServiceEndpointConditionKnownValuesEnum.values(),
                OTelTags.K8S_SERVICE_ENDPOINT_CONDITION,
                "ready",
                "serving",
                "terminating");

        assertKnownValues(
                K8sServiceTrafficDistributionKnownValuesEnum.values(),
                OTelTags.K8S_SERVICE_TRAFFIC_DISTRIBUTION,
                "PreferSameZone",
                "PreferSameNode");

        assertKnownValues(
                K8sServiceTypeKnownValuesEnum.values(),
                OTelTags.K8S_SERVICE_TYPE,
                "ClusterIP",
                "NodePort",
                "LoadBalancer",
                "ExternalName");

        assertKnownValues(
                K8sVolumeTypeKnownValuesEnum.values(),
                OTelTags.K8S_VOLUME_TYPE,
                "configMap",
                "downwardAPI",
                "emptyDir",
                "local",
                "persistentVolumeClaim",
                "secret");

        assertKnownValues(
                ServiceCriticalityKnownValuesEnum.values(),
                OTelTags.SERVICE_CRITICALITY,
                "critical",
                "high",
                "medium",
                "low");
    }

    private <E extends Enum<E> & OTelTagKnownValue> void assertKnownValues(
            final E[] constants,
            final OTelTags expectedTag,
            final String... expectedValues) {

        assertEquals(expectedValues.length, constants.length);

        final List<String> actualValues = Arrays.stream(constants)
                .map(OTelTagKnownValue::getValue)
                .collect(Collectors.toList());

        final List<String> expectedValuesList = Arrays.asList(expectedValues);
        assertEquals(expectedValuesList, actualValues);

        final Set<String> uniqueValues = new LinkedHashSet<String>(actualValues);
        assertEquals(expectedValuesList.size(), uniqueValues.size(), "Enum contains duplicate values");

        Arrays.stream(constants).forEach(constant -> {
            assertEquals(expectedTag, constant.getTag());
            assertNotNull(constant.getValue());
            assertFalse(constant.getValue().isEmpty());
        });
    }
}
