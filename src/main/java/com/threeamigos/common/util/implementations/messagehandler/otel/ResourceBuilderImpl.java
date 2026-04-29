package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Entity;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 * @author Stefano Reksten
 */
class ResourceBuilderImpl extends KeyValueBuilderImpl implements ResourceBuilderInterface,
        ResourceBuilderInterface.ResourceBuilderStepServiceName,
        ResourceBuilderInterface.ResourceBuilderStepServiceNamespace,
        ResourceBuilderInterface.ResourceBuilderStepServiceVersion,
        ResourceBuilderInterface.ResourceBuilderStepServiceInstanceId,
        ResourceBuilderInterface.ResourceBuilderStepDeploymentEnvironmentName,
        ResourceBuilderInterface.ResourceBuilderStepSchemaUrl,
        ResourceBuilderInterface.ResourceBuilderStepEntity,
        ResourceBuilderInterface.ResourceBuilderStepKeyValues {

    private final Map<String, String> overridingAttributes = new HashMap<>();
    private String schemaUrl;
    private final List<Entity> entities = new ArrayList<>();

    public ResourceBuilderStepServiceNamespace withServiceName(String serviceName) {
        overridingAttributes.put(OTelTags.SERVICE_NAME.getValue(), OpenTelemetryAttributeValidator.requireNonBlank(
                serviceName,
                OTelTags.SERVICE_NAME.getValue()));
        return this;
    }

    @Override
    public ResourceBuilderStepServiceVersion withServiceNamespace(String serviceNamespace) {
        overridingAttributes.put(OTelTags.SERVICE_NAMESPACE.getValue(), OpenTelemetryAttributeValidator.requireNonBlank(
                serviceNamespace,
                OTelTags.SERVICE_NAMESPACE.getValue()));
        return this;
    }

    @Override
    public ResourceBuilderStepServiceVersion withNoServiceNamespace() {
        return this;
    }

    @Override
    public ResourceBuilderStepServiceInstanceId withServiceVersion(String serviceVersion) {
        overridingAttributes.put(OTelTags.SERVICE_VERSION.getValue(), OpenTelemetryAttributeValidator.requireNonBlank(
                serviceVersion,
                OTelTags.SERVICE_VERSION.getValue()));
        return this;
    }

    @Override
    public ResourceBuilderStepServiceInstanceId withNoServiceVersion() {
        return this;
    }

    @Override
    public ResourceBuilderStepDeploymentEnvironmentName withServiceInstanceId(String serviceInstanceId) {
        overridingAttributes.put(OTelTags.SERVICE_INSTANCE_ID.getValue(), OpenTelemetryAttributeValidator.requireNonBlank(
                serviceInstanceId,
                OTelTags.SERVICE_INSTANCE_ID.getValue()));
        return this;
    }

    @Override
    public ResourceBuilderStepDeploymentEnvironmentName withNoServiceInstanceId() {
        return this;
    }

    @Override
    public ResourceBuilderStepSchemaUrl withDeploymentEnvironmentName(String deploymentEnvironmentName) {
        overridingAttributes.put(OTelTags.DEPLOYMENT_ENVIRONMENT_NAME.getValue(), OpenTelemetryAttributeValidator.requireNonBlank(
                deploymentEnvironmentName,
                OTelTags.DEPLOYMENT_ENVIRONMENT_NAME.getValue()));
        return this;
    }

    @Override
    public ResourceBuilderStepSchemaUrl withNoDeploymentEnvironmentName() {
        return this;
    }

    @Override
    public ResourceBuilderStepEntity withSchemaUrl(String schemaUrl) {
        this.schemaUrl = OpenTelemetryAttributeValidator.requireNonBlank(schemaUrl, "schemaUrl");
        return this;
    }

    @Override
    public ResourceBuilderStepEntity withNoSchemaUrl() {
        return this;
    }

    @Override
    public ResourceBuilderStepEntity withEntity(Entity entity) {
        if (entity == null) {
            OpenTelemetryAttributeValidator.handleBundled("entityMustNotBeNull");
            return this;
        }
        entities.add(entity);
        return this;
    }

    @Override
    public ResourceBuilderStepKeyValues withNoEntity() {
        return this;
    }

    @Override
    public Resource build() {

        List<KeyValue> filteredAttributes = attributes
                .stream()
                .filter(a -> !overridingAttributes.containsKey(a.getKey()))
                .collect(Collectors.toList());
        
        filteredAttributes.addAll(overridingAttributes.entrySet()
                .stream()
                .map(e -> KeyValueFactory.of(e.getKey(), AnyValueFactory.ofString(e.getValue())))
                .collect(Collectors.toList()));

        filteredAttributes.addAll(TelemetrySDKData.getDefaultAttributes());

        return ResourceFactory.create(schemaUrl, entities, filteredAttributes);
    }

}
