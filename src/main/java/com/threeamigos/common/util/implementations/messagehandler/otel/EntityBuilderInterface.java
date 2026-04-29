package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Entity;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.util.List;

/**
 *
 * @author Stefano Reksten
 */
public interface EntityBuilderInterface {

        /**
         * Generic type
         * @param type the type of Entity
         *             must be non-null and non-blank.
         */
        EntityBuilderSchemaUrlStep withType(String type);

        // ---------------------------------------------------------------------
        // Software and services
        // ---------------------------------------------------------------------
        EntityBuilderServiceStep withServiceType();
        EntityBuilderProcessStep withProcessType();
        /**
         * Adds the TelemetrySDKData attributes, no further operation allowed
         */
        EntityBuilderBuildStep withTelemetryType();

        // ---------------------------------------------------------------------
        // Host infrastructure
        // ---------------------------------------------------------------------
        EntityBuilderHostStep withHostType();
        EntityBuilderContainerStep withContainerType();

        // ---------------------------------------------------------------------
        // Orchestration - Kubernetes
        // ---------------------------------------------------------------------
        EntityBuilderK8sClusterStep withK8sClusterType();
        EntityBuilderK8sNodeStep withK8SNodeType();
        EntityBuilderK8sNamespaceStep withK8sNamespaceType();
        EntityBuilderK8sPodStep withK8sPodType();
        EntityBuilderK8sDeploymentStep withK8sDeploymentType();

        // ---------------------------------------------------------------------
        // Cloud and platform
        // ---------------------------------------------------------------------
        EntityBuilderCloudPlatformStep withCloudPlatformType();
        EntityBuilderCloudRegionStep withCloudRegionType();
        EntityBuilderCloudAccountStep withCloudAccountType();

        // ---------------------------------------------------------------------
        // Web and browser
        // ---------------------------------------------------------------------
        EntityBuilderBrowserStep withBrowserType();
        EntityBuilderDeviceStep withDeviceType();

    /**
     * Logical name of the service
     */
    interface EntityBuilderServiceStep {
        EntityBuilderSchemaUrlStep withServiceName(String serviceName);
    }

    /**
     * Unique ID or name of the host
     */
    interface EntityBuilderHostStep {
        EntityBuilderSchemaUrlStep withHostId(String hostId);
        EntityBuilderSchemaUrlStep withHostName(String hostName);
    }

    /**
     * Unique ID or name of the container (e.g., Docker ID)
     */
    interface EntityBuilderContainerStep {
        EntityBuilderSchemaUrlStep withContainerId(String containerId);
    }

    /**
     * PID of the process
     */
    interface EntityBuilderProcessStep {
        EntityBuilderProcessStartTimeSubstep withProcessPid(long pid);
    }

    /**
     * Start time of the process
     */
    interface EntityBuilderProcessStartTimeSubstep {
        EntityBuilderSchemaUrlStep withProcessStartTime(long timestamp);
    }

    /**
     * K8s POD uid
     */
    interface EntityBuilderK8sPodStep {
        EntityBuilderSchemaUrlStep withPodUid(String podUid);
    }

    interface EntityBuilderK8sClusterStep {
        EntityBuilderSchemaUrlStep withClusterName(String clusterName);
    }

    /**
     * K8s node uid
     */
    interface EntityBuilderK8sNodeStep {
        EntityBuilderSchemaUrlStep withNodeUid(String nodeUid);
    }

    interface EntityBuilderK8sNamespaceStep {
        EntityBuilderK8sClusterStep withNamespaceName(String namespace);
    }

    interface EntityBuilderK8sDeploymentStep {
        EntityBuilderK8sNamespaceStep withDeploymentName(String deploymentName);
    }

    interface EntityBuilderK8sDeploymentNamespaceSubstep {
        EntityBuilderSchemaUrlStep withNamespaceName(String namespaceName);
    }

    interface EntityBuilderCloudPlatformStep {
        EntityBuilderCloudPlatformSubstep withCloudProvider(String cloudProvider);
    }

    interface EntityBuilderCloudPlatformSubstep {
        EntityBuilderSchemaUrlStep withCloudPlatform(String cloudPlatform);
    }

    interface EntityBuilderCloudRegionStep {
        EntityBuilderCloudRegionCloudProviderSubstep withCloudRegion(String cloudRegion);
    }

    interface EntityBuilderCloudRegionCloudProviderSubstep {
        EntityBuilderCloudAccountStep withCloudProvider(String cloudProvider);
    }

    /**
     * Cloud account id
     */
    interface EntityBuilderCloudAccountStep extends EntityBuilderCloudPlatformSubstep {
        EntityBuilderSchemaUrlStep withCloudAccountId(String cloudAccountId);
    }

    interface EntityBuilderSchemaUrlStep {
        /**
         * Sets schema URL for the entity.
         * Value must be non-null and non-blank.
         */
        EntityBuilderStepId withSchemaUrl(String schemaUrl);
        /**
         * Explicitly omits schema URL.
         */
        EntityBuilderStepId withNoSchemaUrl();
    }

    interface EntityBuilderBrowserStep {
        EntityBuilderBrowserPlatformSubstep withBrowserBrands(String browserBrands);
    }

    interface EntityBuilderBrowserPlatformSubstep {
        EntityBuilderStepId withBrowserPlatform(String browserPlatform);
    }

    interface EntityBuilderDeviceStep {
        EntityBuilderSchemaUrlStep withDeviceId(String deviceId);
    }

    interface EntityBuilderStepId {
        EntityBuilderStepAttributes withId(List<KeyValue> id);

        EntityBuilderStepIdOrAttributes withIdString(String name, String value);
        EntityBuilderStepIdOrAttributes withIdString(OTelTags name, String value);

        EntityBuilderStepIdOrAttributes withIdBoolean(String name, boolean value);
        EntityBuilderStepIdOrAttributes withIdBoolean(OTelTags name, boolean value);

        EntityBuilderStepIdOrAttributes withIdLong(String name, long value);
        EntityBuilderStepIdOrAttributes withIdLong(OTelTags name, long value);

        EntityBuilderStepIdOrAttributes withIdDouble(String name, double value);
        EntityBuilderStepIdOrAttributes withIdDouble(OTelTags name, double value);

        EntityBuilderStepIdOrAttributes withIdArray(String name, final List<AnyValue> value);
        EntityBuilderStepIdOrAttributes withIdArray(OTelTags name, final List<AnyValue> value);

        EntityBuilderStepIdOrAttributes withIdKeyValueList(String name, final List<KeyValue> value);
        EntityBuilderStepIdOrAttributes withIdKeyValueList(OTelTags name, final List<KeyValue> value);

        EntityBuilderStepIdOrAttributes withIdBytes(String name, final byte[] value);
        EntityBuilderStepIdOrAttributes withIdBytes(OTelTags name, final byte[] value);
    }

    interface EntityBuilderStepIdOrAttributes {
        EntityBuilderStepIdOrAttributes withIdString(String name, String value);
        EntityBuilderStepIdOrAttributes withIdString(OTelTags name, String value);

        EntityBuilderStepIdOrAttributes withIdBoolean(String name, boolean value);
        EntityBuilderStepIdOrAttributes withIdBoolean(OTelTags name, boolean value);

        EntityBuilderStepIdOrAttributes withIdLong(String name, long value);
        EntityBuilderStepIdOrAttributes withIdLong(OTelTags name, long value);

        EntityBuilderStepIdOrAttributes withIdDouble(String name, double value);
        EntityBuilderStepIdOrAttributes withIdDouble(OTelTags name, double value);

        EntityBuilderStepIdOrAttributes withIdArray(String name, final List<AnyValue> value);
        EntityBuilderStepIdOrAttributes withIdArray(OTelTags name, final List<AnyValue> value);

        EntityBuilderStepIdOrAttributes withIdKeyValueList(String name, final List<KeyValue> value);
        EntityBuilderStepIdOrAttributes withIdKeyValueList(OTelTags name, final List<KeyValue> value);

        EntityBuilderStepIdOrAttributes withIdBytes(String name, final byte[] value);
        EntityBuilderStepIdOrAttributes withIdBytes(OTelTags name, final byte[] value);

        EntityBuilderBuildStep withDescription(List<KeyValue> description);

        EntityBuilderStepAttributes withDescriptionString(String name, String value);
        EntityBuilderStepAttributes withDescriptionString(OTelTags name, String value);

        EntityBuilderStepAttributes withDescriptionBoolean(String name, boolean value);
        EntityBuilderStepAttributes withDescriptionBoolean(OTelTags name, boolean value);

        EntityBuilderStepAttributes withDescriptionLong(String name, long value);
        EntityBuilderStepAttributes withDescriptionLong(OTelTags name, long value);

        EntityBuilderStepAttributes withDescriptionDouble(String name, double value);
        EntityBuilderStepAttributes withDescriptionDouble(OTelTags name, double value);

        EntityBuilderStepAttributes withDescriptionArray(String name, final List<AnyValue> value);
        EntityBuilderStepAttributes withDescriptionArray(OTelTags name, final List<AnyValue> value);

        EntityBuilderStepAttributes withDescriptionKeyValueList(String name, final List<KeyValue> value);
        EntityBuilderStepAttributes withDescriptionKeyValueList(OTelTags name, final List<KeyValue> value);

        EntityBuilderStepAttributes withDescriptionBytes(String name, final byte[] value);
        EntityBuilderStepAttributes withDescriptionBytes(OTelTags name, final byte[] value);

        Entity build();
    }

    interface EntityBuilderStepAttributes {
        EntityBuilderBuildStep withDescription(List<KeyValue> description);

        EntityBuilderStepAttributes withDescriptionString(String name, String value);
        EntityBuilderStepAttributes withDescriptionString(OTelTags name, String value);

        EntityBuilderStepAttributes withDescriptionBoolean(String name, boolean value);
        EntityBuilderStepAttributes withDescriptionBoolean(OTelTags name, boolean value);

        EntityBuilderStepAttributes withDescriptionLong(String name, long value);
        EntityBuilderStepAttributes withDescriptionLong(OTelTags name, long value);

        EntityBuilderStepAttributes withDescriptionDouble(String name, double value);
        EntityBuilderStepAttributes withDescriptionDouble(OTelTags name, double value);

        EntityBuilderStepAttributes withDescriptionArray(String name, final List<AnyValue> value);
        EntityBuilderStepAttributes withDescriptionArray(OTelTags name, final List<AnyValue> value);

        EntityBuilderStepAttributes withDescriptionKeyValueList(String name, final List<KeyValue> value);
        EntityBuilderStepAttributes withDescriptionKeyValueList(OTelTags name, final List<KeyValue> value);

        EntityBuilderStepAttributes withDescriptionBytes(String name, final byte[] value);
        EntityBuilderStepAttributes withDescriptionBytes(OTelTags name, final byte[] value);

        Entity build();
    }

    interface EntityBuilderBuildStep {
        Entity build();
    }
}
