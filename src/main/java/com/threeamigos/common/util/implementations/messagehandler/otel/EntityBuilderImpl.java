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
        EntityBuilderInterface.EntityBuilderStepType,
        EntityBuilderInterface.EntityBuilderServiceStep,
        EntityBuilderInterface.EntityBuilderHostStep,
        EntityBuilderInterface.EntityBuilderContainerStep,
        EntityBuilderInterface.EntityBuilderProcessType,
        EntityBuilderInterface.EntityBuilderProcessStartTime,
        EntityBuilderInterface.EntityBuilderPodType,
        EntityBuilderInterface.EntityBuilderNodeType,
        EntityBuilderInterface.EntityBuilderCloudAccountType,
        EntityBuilderInterface.EntityBuilderStepSchemaUrl,
        EntityBuilderInterface.EntityBuilderStepId,
        EntityBuilderInterface.EntityBuilderStepIdOrAttributes,
        EntityBuilderInterface.EntityBuilderStepAttributes,
        EntityBuilderInterface.EntityBuilderStepBuild {

    private String type;
    private String schemaUrl;
    private List<KeyValue> id = new ArrayList<>();
    private List<KeyValue> description = new ArrayList<>();
    
    @Override
    public EntityBuilderStepSchemaUrl withType(String type) {
        this.type = type;
        return this;
    }

    @Override
    public EntityBuilderServiceStep withServiceType() {
        this.type = "service";
        return this;
    }

    @Override
    public EntityBuilderStepSchemaUrl withServiceName(String serviceName) {
        id.add(KeyValueFactory.of(Names.ATTR_SERVICE_NAME, AnyValueFactory.ofString(serviceName)));
        return this;
    }

    @Override
    public EntityBuilderHostStep withHostType() {
        this.type = "host";
        return this;
    }

    @Override
    public EntityBuilderStepSchemaUrl withHostId(String hostId) {
        id.add(KeyValueFactory.of(Names.ATTR_HOST_ID, AnyValueFactory.ofString(hostId)));
        return this;
    }

    @Override
    public EntityBuilderStepSchemaUrl withHostName(String hostName) {
        id.add(KeyValueFactory.of(Names.ATTR_HOST_NAME, AnyValueFactory.ofString(hostName)));
        return this;
    }

    @Override
    public EntityBuilderContainerStep withContainerType() {
        this.type = "host";
        return this;
    }

    @Override
    public EntityBuilderStepSchemaUrl withContainerId(String containerId) {
        id.add(KeyValueFactory.of(Names.ATTR_CONTAINER_ID, AnyValueFactory.ofString(containerId)));
        return this;
    }

    @Override
    public EntityBuilderProcessType withProcessType() {
        this.type = "process";
        return this;
    }

    @Override
    public EntityBuilderProcessStartTime withProcessPid(long pid) {
        id.add(KeyValueFactory.of(Names.ATTR_PROCESS_PID, AnyValueFactory.ofLong(pid)));
        return this;
    }

    @Override
    public EntityBuilderStepSchemaUrl withProcessStartTime(long timestamp) {
        id.add(KeyValueFactory.of(Names.ATTR_PROCESS_CREATION_TIME, AnyValueFactory.ofLong(timestamp)));
        return this;
    }

    @Override
    public EntityBuilderPodType withK8sPodType(String podUid) {
        this.type = "k8s.pod";
        return this;
    }

    @Override
    public EntityBuilderStepSchemaUrl withPodUid(String podUid) {
        id.add(KeyValueFactory.of(Names.ATTR_K8S_POD_UID, AnyValueFactory.ofString(podUid)));
        return this;
    }

    @Override
    public EntityBuilderNodeType withK8SNodeType(String nodeUid) {
        this.type = "k8s.node";
        return this;
    }

    @Override
    public EntityBuilderStepSchemaUrl withNodeUid(String nodeUid) {
        id.add(KeyValueFactory.of(Names.ATTR_K8S_NODE_UID, AnyValueFactory.ofString(nodeUid)));
        return this;
    }

    @Override
    public EntityBuilderCloudAccountType withCloudAccountType(String cloudAccountId) {
        this.type = "cloud.account";
        return this;
    }

    @Override
    public EntityBuilderStepSchemaUrl withCloudAccountId(String cloudAccountId) {
        id.add(KeyValueFactory.of(Names.ATTR_CLOUD_ACCOUNT_ID, AnyValueFactory.ofString(cloudAccountId)));
        return this;
    }

    @Override
    public EntityBuilderStepId withSchemaUrl(String schemaUrl) {
        this.schemaUrl = schemaUrl;
        return this;
    }

    @Override
    public EntityBuilderStepId withNoSchemaUrl() {
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withId(List<KeyValue> id) {
        this.id.addAll(id);
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdString(String name, String value) {
        id.add(KeyValueFactory.of(name, AnyValueFactory.ofString(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdString(Names name, String value) {
        id.add(KeyValueFactory.of(name, AnyValueFactory.ofString(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdBoolean(String name, boolean value) {
        id.add(KeyValueFactory.of(name, AnyValueFactory.ofBoolean(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdBoolean(Names name, boolean value) {
        id.add(KeyValueFactory.of(name, AnyValueFactory.ofBoolean(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdLong(String name, long value) {
        id.add(KeyValueFactory.of(name, AnyValueFactory.ofLong(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdLong(Names name, long value) {
        id.add(KeyValueFactory.of(name, AnyValueFactory.ofLong(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdDouble(String name, double value) {
        id.add(KeyValueFactory.of(name, AnyValueFactory.ofDouble(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdDouble(Names name, double value) {
        id.add(KeyValueFactory.of(name, AnyValueFactory.ofDouble(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdArray(String name, List<AnyValue> value) {
        id.add(KeyValueFactory.of(name, AnyValueFactory.ofArray(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdArray(Names name, List<AnyValue> value) {
        id.add(KeyValueFactory.of(name, AnyValueFactory.ofArray(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdKeyValueList(String name, List<KeyValue> value) {
        id.add(KeyValueFactory.of(name, AnyValueFactory.ofKvList(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdKeyValueList(Names name, List<KeyValue> value) {
        id.add(KeyValueFactory.of(name, AnyValueFactory.ofKvList(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdBytes(String name, byte[] value) {
        id.add(KeyValueFactory.of(name, AnyValueFactory.ofBytes(value)));
        return this;
    }

    @Override
    public EntityBuilderStepIdOrAttributes withIdBytes(Names name, byte[] value) {
        id.add(KeyValueFactory.of(name, AnyValueFactory.ofBytes(value)));
        return this;
    }

    @Override
    public EntityBuilderStepBuild withDescription(List<KeyValue> description) {
        this.description.addAll(description);
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionString(String name, String value) {
        description.add(KeyValueFactory.of(name, AnyValueFactory.ofString(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionString(Names name, String value) {
        description.add(KeyValueFactory.of(name, AnyValueFactory.ofString(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionBoolean(String name, boolean value) {
        description.add(KeyValueFactory.of(name, AnyValueFactory.ofBoolean(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionBoolean(Names name, boolean value) {
        description.add(KeyValueFactory.of(name, AnyValueFactory.ofBoolean(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionLong(String name, long value) {
        description.add(KeyValueFactory.of(name, AnyValueFactory.ofLong(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionLong(Names name, long value) {
        description.add(KeyValueFactory.of(name, AnyValueFactory.ofLong(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionDouble(String name, double value) {
        description.add(KeyValueFactory.of(name, AnyValueFactory.ofDouble(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionDouble(Names name, double value) {
        description.add(KeyValueFactory.of(name, AnyValueFactory.ofDouble(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionArray(String name, List<AnyValue> value) {
        description.add(KeyValueFactory.of(name, AnyValueFactory.ofArray(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionArray(Names name, List<AnyValue> value) {
        description.add(KeyValueFactory.of(name, AnyValueFactory.ofArray(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionKeyValueList(String name, List<KeyValue> value) {
        description.add(KeyValueFactory.of(name, AnyValueFactory.ofKvList(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionKeyValueList(Names name, List<KeyValue> value) {
        description.add(KeyValueFactory.of(name, AnyValueFactory.ofKvList(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionBytes(String name, byte[] value) {
        description.add(KeyValueFactory.of(name, AnyValueFactory.ofBytes(value)));
        return this;
    }

    @Override
    public EntityBuilderStepAttributes withDescriptionBytes(Names name, byte[] value) {
        description.add(KeyValueFactory.of(name, AnyValueFactory.ofBytes(value)));
        return this;
    }

    @Override
    public Entity build() {
        return EntityFactory.create(type, schemaUrl, id, description);
    }

}
