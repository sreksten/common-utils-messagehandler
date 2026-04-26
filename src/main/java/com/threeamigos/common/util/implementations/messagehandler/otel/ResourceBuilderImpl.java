package com.threeamigos.common.util.implementations.messagehandler.otel;

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
public class ResourceBuilderImpl extends KeyValueBuilderImpl implements ResourceBuilderInterface,
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
        if (serviceName == null || serviceName.isEmpty()) {
            serviceName = "unknown_service";
        }
        overridingAttributes.put(Names.ATTR_SERVICE_NAME.getValue(), serviceName);
        return this;
    }

    @Override
    public ResourceBuilderStepServiceVersion withServiceNamespace(String serviceNamespace) {
        if (serviceNamespace == null || serviceNamespace.isEmpty()) {
            return this;
        }
        overridingAttributes.put(Names.ATTR_SERVICE_NAMESPACE.getValue(), serviceNamespace);
        return this;
    }

    @Override
    public ResourceBuilderStepServiceVersion withNoServiceNamespace() {
        return this;
    }

    @Override
    public ResourceBuilderStepServiceInstanceId withServiceVersion(String serviceVersion) {
        if (serviceVersion == null || serviceVersion.isEmpty()) {
            return this;
        }
        overridingAttributes.put(Names.ATTR_SERVICE_VERSION.getValue(), serviceVersion);
        return this;
    }

    @Override
    public ResourceBuilderStepServiceInstanceId withNoServiceVersion() {
        return this;
    }

    @Override
    public ResourceBuilderStepDeploymentEnvironmentName withServiceInstanceId(String serviceInstanceId) {
        if (serviceInstanceId == null || serviceInstanceId.isEmpty()) {
            return this;
        }
        overridingAttributes.put(Names.ATTR_SERVICE_INSTANCE_ID.getValue(), serviceInstanceId);
        return this;
    }

    @Override
    public ResourceBuilderStepDeploymentEnvironmentName withNoServiceInstanceId() {
        return this;
    }

    @Override
    public ResourceBuilderStepSchemaUrl withDeploymentEnvironmentName(String deploymentEnvironmentName) {
        if (deploymentEnvironmentName == null || deploymentEnvironmentName.isEmpty()) {
            return this;
        }
        overridingAttributes.put(Names.ATTR_DEPLOYMENT_ENVIRONMENT_NAME.getValue(), deploymentEnvironmentName);
        return this;
    }

    @Override
    public ResourceBuilderStepSchemaUrl withNoDeploymentEnvironmentName() {
        return this;
    }

    @Override
    public ResourceBuilderStepEntity withSchemaUrl(String schemaUrl) {
        if (schemaUrl == null || schemaUrl.isEmpty()) {
            return this;
        }
        this.schemaUrl = schemaUrl;
        return this;
    }

    @Override
    public ResourceBuilderStepEntity withNoSchemaUrl() {
        return this;
    }

    @Override
    public ResourceBuilderStepEntity withEntity(Entity entity) {
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
