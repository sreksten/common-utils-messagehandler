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

    interface EntityBuilderStepType {
        EntityBuilderStepSchemaUrl withType(String type);
        EntityBuilderServiceStep withServiceType();
        EntityBuilderHostStep withHostType();
        EntityBuilderContainerStep withContainerType();
        EntityBuilderProcessType withProcessType();
        EntityBuilderPodType withK8sPodType(String podUid);
        EntityBuilderNodeType withK8SNodeType(String nodeUid);
        EntityBuilderCloudAccountType withCloudAccountType(String cloudAccountId);
    }

    /**
     * Logical name of the service
     */
    interface EntityBuilderServiceStep {
        EntityBuilderStepSchemaUrl withServiceName(String serviceName);
    }

    /**
     * Unique ID or name of the host
     */
    interface EntityBuilderHostStep {
        EntityBuilderStepSchemaUrl withHostId(String hostId);
        EntityBuilderStepSchemaUrl withHostName(String hostName);
    }

    /**
     * Unique ID or name of the container (e.g., Docker ID)
     */
    interface EntityBuilderContainerStep {
        EntityBuilderStepSchemaUrl withContainerId(String containerId);
    }

    /**
     * PID of the process
     */
    interface EntityBuilderProcessType {
        EntityBuilderProcessStartTime withProcessPid(long pid);
    }

    /**
     * Start time of the process
     */
    interface EntityBuilderProcessStartTime {
        EntityBuilderStepSchemaUrl withProcessStartTime(long timestamp);
    }

    /**
     * K8s POD uid
     */
    interface EntityBuilderPodType {
        EntityBuilderStepSchemaUrl withPodUid(String podUid);
    }

    /**
     * K8s node uid
     */
    interface EntityBuilderNodeType {
        EntityBuilderStepSchemaUrl withNodeUid(String nodeUid);
    }

    /**
     * Cloud account id
     */
    interface EntityBuilderCloudAccountType {
        EntityBuilderStepSchemaUrl withCloudAccountId(String cloudAccountId);
    }

    interface EntityBuilderStepSchemaUrl {
        EntityBuilderStepId withSchemaUrl(String schemaUrl);
        EntityBuilderStepId withNoSchemaUrl();
    }

    interface EntityBuilderStepId {
        EntityBuilderStepAttributes withId(List<KeyValue> id);

        EntityBuilderStepIdOrAttributes withIdString(String name, String value);
        EntityBuilderStepIdOrAttributes withIdString(Names name, String value);

        EntityBuilderStepIdOrAttributes withIdBoolean(String name, boolean value);
        EntityBuilderStepIdOrAttributes withIdBoolean(Names name, boolean value);

        EntityBuilderStepIdOrAttributes withIdLong(String name, long value);
        EntityBuilderStepIdOrAttributes withIdLong(Names name, long value);

        EntityBuilderStepIdOrAttributes withIdDouble(String name, double value);
        EntityBuilderStepIdOrAttributes withIdDouble(Names name, double value);

        EntityBuilderStepIdOrAttributes withIdArray(String name, final List<AnyValue> value);
        EntityBuilderStepIdOrAttributes withIdArray(Names name, final List<AnyValue> value);

        EntityBuilderStepIdOrAttributes withIdKeyValueList(String name, final List<KeyValue> value);
        EntityBuilderStepIdOrAttributes withIdKeyValueList(Names name, final List<KeyValue> value);

        EntityBuilderStepIdOrAttributes withIdBytes(String name, final byte[] value);
        EntityBuilderStepIdOrAttributes withIdBytes(Names name, final byte[] value);
    }

    interface EntityBuilderStepIdOrAttributes {
        EntityBuilderStepIdOrAttributes withIdString(String name, String value);
        EntityBuilderStepIdOrAttributes withIdString(Names name, String value);

        EntityBuilderStepIdOrAttributes withIdBoolean(String name, boolean value);
        EntityBuilderStepIdOrAttributes withIdBoolean(Names name, boolean value);

        EntityBuilderStepIdOrAttributes withIdLong(String name, long value);
        EntityBuilderStepIdOrAttributes withIdLong(Names name, long value);

        EntityBuilderStepIdOrAttributes withIdDouble(String name, double value);
        EntityBuilderStepIdOrAttributes withIdDouble(Names name, double value);

        EntityBuilderStepIdOrAttributes withIdArray(String name, final List<AnyValue> value);
        EntityBuilderStepIdOrAttributes withIdArray(Names name, final List<AnyValue> value);

        EntityBuilderStepIdOrAttributes withIdKeyValueList(String name, final List<KeyValue> value);
        EntityBuilderStepIdOrAttributes withIdKeyValueList(Names name, final List<KeyValue> value);

        EntityBuilderStepIdOrAttributes withIdBytes(String name, final byte[] value);
        EntityBuilderStepIdOrAttributes withIdBytes(Names name, final byte[] value);

        EntityBuilderStepBuild withDescription(List<KeyValue> description);

        EntityBuilderStepAttributes withDescriptionString(String name, String value);
        EntityBuilderStepAttributes withDescriptionString(Names name, String value);

        EntityBuilderStepAttributes withDescriptionBoolean(String name, boolean value);
        EntityBuilderStepAttributes withDescriptionBoolean(Names name, boolean value);

        EntityBuilderStepAttributes withDescriptionLong(String name, long value);
        EntityBuilderStepAttributes withDescriptionLong(Names name, long value);

        EntityBuilderStepAttributes withDescriptionDouble(String name, double value);
        EntityBuilderStepAttributes withDescriptionDouble(Names name, double value);

        EntityBuilderStepAttributes withDescriptionArray(String name, final List<AnyValue> value);
        EntityBuilderStepAttributes withDescriptionArray(Names name, final List<AnyValue> value);

        EntityBuilderStepAttributes withDescriptionKeyValueList(String name, final List<KeyValue> value);
        EntityBuilderStepAttributes withDescriptionKeyValueList(Names name, final List<KeyValue> value);

        EntityBuilderStepAttributes withDescriptionBytes(String name, final byte[] value);
        EntityBuilderStepAttributes withDescriptionBytes(Names name, final byte[] value);

        Entity build();
    }

    interface EntityBuilderStepAttributes {
        EntityBuilderStepBuild withDescription(List<KeyValue> description);

        EntityBuilderStepAttributes withDescriptionString(String name, String value);
        EntityBuilderStepAttributes withDescriptionString(Names name, String value);

        EntityBuilderStepAttributes withDescriptionBoolean(String name, boolean value);
        EntityBuilderStepAttributes withDescriptionBoolean(Names name, boolean value);

        EntityBuilderStepAttributes withDescriptionLong(String name, long value);
        EntityBuilderStepAttributes withDescriptionLong(Names name, long value);

        EntityBuilderStepAttributes withDescriptionDouble(String name, double value);
        EntityBuilderStepAttributes withDescriptionDouble(Names name, double value);

        EntityBuilderStepAttributes withDescriptionArray(String name, final List<AnyValue> value);
        EntityBuilderStepAttributes withDescriptionArray(Names name, final List<AnyValue> value);

        EntityBuilderStepAttributes withDescriptionKeyValueList(String name, final List<KeyValue> value);
        EntityBuilderStepAttributes withDescriptionKeyValueList(Names name, final List<KeyValue> value);

        EntityBuilderStepAttributes withDescriptionBytes(String name, final byte[] value);
        EntityBuilderStepAttributes withDescriptionBytes(Names name, final byte[] value);

        Entity build();
    }

    interface EntityBuilderStepBuild {
        Entity build();
    }
}
