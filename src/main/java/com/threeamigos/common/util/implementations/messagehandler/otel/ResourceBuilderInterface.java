package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.Entity;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;

/**
 *
 * @author Stefano Reksten
 */
public interface ResourceBuilderInterface {

    /**
     * The most critical resource attribute, defining the logical name of the service (e.g., checkout-service,
     * frontend-api). If set with a null or empty value, will be changed to "unknown_service".
     */
    interface ResourceBuilderStepServiceName {
        ResourceBuilderStepServiceNamespace withServiceName(String serviceName);
    }

    /**
     * An optional namespace to group services (e.g., shop-production).
     */
    interface ResourceBuilderStepServiceNamespace {
        ResourceBuilderStepServiceVersion withServiceNamespace(String schemaNamespace);
        ResourceBuilderStepServiceVersion withNoServiceNamespace();
    }

    /**
     * The version of the deployed service (e.g., v1.2.3 or commit hash), crucial for analyzing deployment impacts.
     */
    interface ResourceBuilderStepServiceVersion {
        ResourceBuilderStepServiceInstanceId withServiceVersion(String serviceVersion);
        ResourceBuilderStepServiceInstanceId withNoServiceVersion();
    }

    /**
     * A unique identifier for the service instance (e.g., a UUID or pod name). This is required if multiple instances
     * of the same service run concurrently.
     */
    interface ResourceBuilderStepServiceInstanceId {
        ResourceBuilderStepDeploymentEnvironmentName withServiceInstanceId(String serviceInstanceId);
        ResourceBuilderStepDeploymentEnvironmentName withNoServiceInstanceId();
    }

    /**
     * The environment where the service is running (e.g., production, staging)
     */
    interface ResourceBuilderStepDeploymentEnvironmentName {
        ResourceBuilderStepSchemaUrl withDeploymentEnvironmentName(String deploymentEnvironmentName);
        ResourceBuilderStepSchemaUrl withNoDeploymentEnvironmentName();
    }

    interface ResourceBuilderStepSchemaUrl {
        ResourceBuilderStepEntity withSchemaUrl(String schemaUrl);
        ResourceBuilderStepEntity withNoSchemaUrl();
    }

    interface ResourceBuilderStepEntity {
        ResourceBuilderStepEntity withEntity(Entity entity);
        ResourceBuilderStepKeyValues withNoEntity();
    }

    interface ResourceBuilderStepKeyValues extends KeyValueBuilderInterface {
        Resource build();
    }

}
