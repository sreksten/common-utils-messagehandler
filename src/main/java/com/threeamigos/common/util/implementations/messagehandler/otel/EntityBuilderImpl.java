package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Entity;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Stefano Reksten
 */
class EntityBuilderImpl implements EntityBuilderInterface,
        EntityBuilderInterface.EntityBuilderServiceStep,
        EntityBuilderInterface.EntityBuilderProcessStep,
        EntityBuilderInterface.EntityBuilderProcessStartTimeSubstep,
        EntityBuilderInterface.EntityBuilderHostStep,
        EntityBuilderInterface.EntityBuilderContainerStep,
        EntityBuilderInterface.EntityBuilderK8sClusterStep,
        EntityBuilderInterface.EntityBuilderK8sNodeStep,
        EntityBuilderInterface.EntityBuilderK8sNamespaceStep,
        EntityBuilderInterface.EntityBuilderK8sPodStep,
        EntityBuilderInterface.EntityBuilderK8sDeploymentStep,
        EntityBuilderInterface.EntityBuilderCloudPlatformStep,
        EntityBuilderInterface.EntityBuilderCloudPlatformSubstep,
        EntityBuilderInterface.EntityBuilderCloudRegionStep,
        EntityBuilderInterface.EntityBuilderCloudRegionCloudProviderSubstep,
        EntityBuilderInterface.EntityBuilderCloudAccountStep,
        EntityBuilderInterface.EntityBuilderBrowserStep,
        EntityBuilderInterface.EntityBuilderBrowserPlatformSubstep,
        EntityBuilderInterface.EntityBuilderDeviceStep,
        EntityBuilderInterface.EntityBuilderSchemaUrlStep,
        EntityBuilderInterface.EntityBuilderStepId,
        EntityBuilderInterface.EntityBuilderStepIdOrAttributes,
        EntityBuilderInterface.EntityBuilderStepAttributes,
        EntityBuilderInterface.EntityBuilderBuildStep {

    private String type;
    private String schemaUrl;
    private final List<KeyValue> id = new ArrayList<>();
    private final List<KeyValue> description = new ArrayList<>();
    
    @Override
    public EntityBuilderSchemaUrlStep withType(String type) {
        type = OpenTelemetryAttributeValidator.requireNonBlank(type, "type");
        this.type = type;
        return this;
    }

    @Override
    public EntityBuilderServiceStep withServiceType() {
        this.type = "service";
        return this;
    }

    @Override
    public EntityBuilderSchemaUrlStep withServiceName(String serviceName) {
        serviceName = OpenTelemetryAttributeValidator.requireNonBlank(serviceName, "serviceName");
        id.add(KeyValueFactory.of(OTelTags.SERVICE_NAME, AnyValueFactory.ofString(serviceName)));
        return this;
    }

    @Override
    public EntityBuilderProcessStep withProcessType() {
        this.type = "process";
        return this;
    }

    @Override
    public EntityBuilderProcessStartTimeSubstep withProcessPid(long pid) {
        id.add(KeyValueFactory.of(OTelTags.PROCESS_PID, AnyValueFactory.ofLong(pid)));
        return this;
    }

    @Override
    public EntityBuilderSchemaUrlStep withProcessStartTime(long timestamp) {
        id.add(KeyValueFactory.of(OTelTags.PROCESS_CREATION_TIME, AnyValueFactory.ofLong(timestamp)));
        return this;
    }

    @Override
    public EntityBuilderBuildStep withTelemetryType() {
        this.type = "telemetry.sdk";
        id.addAll(TelemetrySDKData.getDefaultAttributes());
        return this;
    }

    @Override
    public EntityBuilderHostStep withHostType() {
        this.type = "host";
        return this;
    }

    @Override
    public EntityBuilderSchemaUrlStep withHostId(String hostId) {
        hostId = OpenTelemetryAttributeValidator.requireNonBlank(hostId, "hostId");
        id.add(KeyValueFactory.of(OTelTags.HOST_ID, AnyValueFactory.ofString(hostId)));
        return this;
    }

    @Override
    public EntityBuilderSchemaUrlStep withHostName(String hostName) {
        hostName = OpenTelemetryAttributeValidator.requireNonBlank(hostName, "hostName");
        id.add(KeyValueFactory.of(OTelTags.HOST_NAME, AnyValueFactory.ofString(hostName)));
        return this;
    }

    @Override
    public EntityBuilderContainerStep withContainerType() {
        this.type = "container";
        return this;
    }

    @Override
    public EntityBuilderSchemaUrlStep withContainerId(String containerId) {
        containerId = OpenTelemetryAttributeValidator.requireNonBlank(containerId, "containerId");
        id.add(KeyValueFactory.of(OTelTags.CONTAINER_ID, AnyValueFactory.ofString(containerId)));
        return this;
    }

    @Override
    public EntityBuilderK8sClusterStep withK8sClusterType() {
        this.type = "k8s.cluster";
        return this;
    }

    @Override
    public EntityBuilderSchemaUrlStep withClusterName(String clusterName) {
        clusterName = OpenTelemetryAttributeValidator.requireNonBlank(clusterName, "clusterName");
        id.add(KeyValueFactory.of(OTelTags.K8S_CLUSTER_NAME, AnyValueFactory.ofString(clusterName)));
        return this;
    }

    @Override
    public EntityBuilderK8sNodeStep withK8SNodeType() {
        this.type = "k8s.node";
        return this;
    }

    @Override
    public EntityBuilderSchemaUrlStep withNodeUid(String nodeUid) {
        nodeUid = OpenTelemetryAttributeValidator.requireNonBlank(nodeUid, "nodeUid");
        id.add(KeyValueFactory.of(OTelTags.K8S_NODE_UID, AnyValueFactory.ofString(nodeUid)));
        return this;
    }

    @Override
    public EntityBuilderK8sNamespaceStep withK8sNamespaceType() {
        this.type = "k8s.namespace";
        return this;
    }

    @Override
    public EntityBuilderK8sClusterStep withNamespaceName(String namespaceName) {
        namespaceName = OpenTelemetryAttributeValidator.requireNonBlank(namespaceName, "namespaceName");
        id.add(KeyValueFactory.of(OTelTags.K8S_NAMESPACE_NAME, AnyValueFactory.ofString(namespaceName)));
        return this;
    }

    @Override
    public EntityBuilderK8sPodStep withK8sPodType() {
        this.type = "k8s.pod";
        return this;
    }

    @Override
    public EntityBuilderSchemaUrlStep withPodUid(String podUid) {
        podUid = OpenTelemetryAttributeValidator.requireNonBlank(podUid, "podUid");
        id.add(KeyValueFactory.of(OTelTags.K8S_POD_UID, AnyValueFactory.ofString(podUid)));
        return this;
    }

    @Override
    public EntityBuilderK8sDeploymentStep withK8sDeploymentType() {
        this.type = "k8s.deployment";
        return this;
    }

    @Override
    public EntityBuilderK8sNamespaceStep withDeploymentName(String deploymentName) {
        deploymentName = OpenTelemetryAttributeValidator.requireNonBlank(deploymentName, "deploymentName");
        id.add(KeyValueFactory.of(OTelTags.K8S_DEPLOYMENT_NAME, AnyValueFactory.ofString(deploymentName)));
        return this;
    }

    @Override
    public EntityBuilderCloudPlatformStep withCloudPlatformType() {
        this.type = "cloud.platform";
        return this;
    }

    @Override
    public EntityBuilderCloudAccountStep withCloudProvider(String cloudProvider) {
        cloudProvider = OpenTelemetryAttributeValidator.requireNonBlank(cloudProvider, "cloudProvider");
        id.add(KeyValueFactory.of(OTelTags.CLOUD_PROVIDER, AnyValueFactory.ofString(cloudProvider)));
        return this;
    }

    @Override
    public EntityBuilderSchemaUrlStep withCloudPlatform(String cloudPlatform) {
        cloudPlatform = OpenTelemetryAttributeValidator.requireNonBlank(cloudPlatform, "cloudPlatform");
        id.add(KeyValueFactory.of(OTelTags.CLOUD_PLATFORM, AnyValueFactory.ofString(cloudPlatform)));
        return this;
    }

    @Override
    public EntityBuilderCloudRegionStep withCloudRegionType() {
        this.type = "cloud.region";
        return this;
    }

    @Override
    public EntityBuilderCloudRegionCloudProviderSubstep withCloudRegion(String cloudRegion) {
        cloudRegion = OpenTelemetryAttributeValidator.requireNonBlank(cloudRegion, "cloudRegion");
        id.add(KeyValueFactory.of(OTelTags.CLOUD_REGION, AnyValueFactory.ofString(cloudRegion)));
        return this;
    }

    @Override
    public EntityBuilderCloudAccountStep withCloudAccountType() {
        this.type = "cloud.account";
        return this;
    }

    @Override
    public EntityBuilderSchemaUrlStep withCloudAccountId(String cloudAccountId) {
        cloudAccountId = OpenTelemetryAttributeValidator.requireNonBlank(cloudAccountId, "cloudAccountId");
        id.add(KeyValueFactory.of(OTelTags.CLOUD_ACCOUNT_ID, AnyValueFactory.ofString(cloudAccountId)));
        return this;
    }

    @Override
    public EntityBuilderBrowserStep withBrowserType() {
        this.type = "browser";
        return this;
    }

    @Override
    public EntityBuilderBrowserPlatformSubstep withBrowserBrands(String browserBrands) {
        browserBrands = OpenTelemetryAttributeValidator.requireNonBlank(browserBrands, "browserBrands");
        id.add(KeyValueFactory.of(OTelTags.BROWSER_BRANDS, AnyValueFactory.ofString(browserBrands)));
        return this;
    }

    @Override
    public EntityBuilderStepId withBrowserPlatform(String browserPlatform) {
        browserPlatform = OpenTelemetryAttributeValidator.requireNonBlank(browserPlatform, "browserPlatform");
        id.add(KeyValueFactory.of(OTelTags.BROWSER_PLATFORM, AnyValueFactory.ofString(browserPlatform)));
        return this;
    }

    @Override
    public EntityBuilderDeviceStep withDeviceType() {
        this.type = "device";
        return this;
    }

    @Override
    public EntityBuilderSchemaUrlStep withDeviceId(String deviceId) {
        deviceId = OpenTelemetryAttributeValidator.requireNonBlank(deviceId, "deviceId");
        id.add(KeyValueFactory.of(OTelTags.DEVICE_ID, AnyValueFactory.ofString(deviceId)));
        return this;
    }

    @Override
    public EntityBuilderStepId withSchemaUrl(String schemaUrl) {
        this.schemaUrl = OpenTelemetryAttributeValidator.requireNonBlank(schemaUrl, "schemaUrl");
        return this;
    }

    @Override
    public EntityBuilderStepId withNoSchemaUrl() {
        return this;
    }

    private String validateId(OTelTags name) {
        return name != null ? name.getValue() : validateId((String) null);
    }

    private String validateId(String id) {
        if (id == null) {
            OpenTelemetryAttributeValidator.handleBundled("entityIdMustNotBeNull");
            return "unknown_id";
        }
        if (id.trim().isEmpty()) {
            OpenTelemetryAttributeValidator.handleBundled("entityIdMustNotBeEmpty");
            return "unknown_id";
        }
        return id;
    }

    private String validateIdValue(String value) {
        if (value == null) {
            OpenTelemetryAttributeValidator.handle(com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle.format(
                            "builderFieldMustNotBeNull",
                            "entityIdValue"));
            return "unknown";
        }
        if (value.trim().isEmpty()) {
            OpenTelemetryAttributeValidator.handle(com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle.format(
                            "builderFieldMustNotBeBlank",
                            "entityIdValue"));
            return "unknown";
        }
        return value;
    }

    @Override
    public EntityBuilderStepAttributes withId(List<KeyValue> id) {
        if (id == null || id.isEmpty()) {
            this.id.add(KeyValueFactory.of(validateId((String) null), AnyValueFactory.ofString(validateIdValue(null))));
            return this;
        }
        this.id.addAll(id);
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdString(String name, String value) {
        id.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofString(validateIdValue(value))));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdString(OTelTags name, String value) {
        id.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofString(validateIdValue(value))));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdBoolean(String name, boolean value) {
        id.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofBoolean(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdBoolean(OTelTags name, boolean value) {
        id.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofBoolean(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdLong(String name, long value) {
        id.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofLong(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdLong(OTelTags name, long value) {
        id.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofLong(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdDouble(String name, double value) {
        id.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofDouble(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdDouble(OTelTags name, double value) {
        id.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofDouble(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdArray(String name, List<AnyValue> value) {
        id.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofArray(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdArray(OTelTags name, List<AnyValue> value) {
        id.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofArray(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdKeyValueList(String name, List<KeyValue> value) {
        id.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofKvList(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdKeyValueList(OTelTags name, List<KeyValue> value) {
        id.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofKvList(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdBytes(String name, byte[] value) {
        id.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofBytes(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdBytes(OTelTags name, byte[] value) {
        id.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofBytes(value)));
        return this;
    }

    @Override
    public EntityBuilderBuildStep withDescription(List<KeyValue> description) {
        if (description != null) {
            this.description.addAll(description);
        }
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionString(String name, String value) {
        description.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofNullableString(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionString(OTelTags name, String value) {
        description.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofNullableString(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionBoolean(String name, boolean value) {
        description.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofBoolean(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionBoolean(OTelTags name, boolean value) {
        description.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofBoolean(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionLong(String name, long value) {
        description.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofLong(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionLong(OTelTags name, long value) {
        description.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofLong(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionDouble(String name, double value) {
        description.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofDouble(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionDouble(OTelTags name, double value) {
        description.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofDouble(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionArray(String name, List<AnyValue> value) {
        description.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofArray(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionArray(OTelTags name, List<AnyValue> value) {
        description.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofArray(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionKeyValueList(String name, List<KeyValue> value) {
        description.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofKvList(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionKeyValueList(OTelTags name, List<KeyValue> value) {
        description.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofKvList(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionBytes(String name, byte[] value) {
        description.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofBytes(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionBytes(OTelTags name, byte[] value) {
        description.add(KeyValueFactory.of(validateId(name), AnyValueFactory.ofBytes(value)));
        return this;
    }

    @Override
    public Entity build() {
        return new EntityImpl(type, schemaUrl, id, description);
    }

}
