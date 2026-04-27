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
     * frontend-api).
     * <p>
     * Must be non-null and non-blank.
     */
    interface ResourceBuilderStepServiceName {
        ResourceBuilderStepServiceNamespace withServiceName(String serviceName);
    }

    /**
     * An optional namespace to group services (e.g., shop-production).
     * <p>
     * Use {@link #withNoServiceNamespace()} to intentionally omit this attribute.
     */
    interface ResourceBuilderStepServiceNamespace {
        ResourceBuilderStepServiceVersion withServiceNamespace(String schemaNamespace);
        ResourceBuilderStepServiceVersion withNoServiceNamespace();
    }

    /**
     * The version of the deployed service (e.g., v1.2.3 or commit hash), crucial for analyzing deployment impacts.
     * <p>
     * Use {@link #withNoServiceVersion()} to intentionally omit this attribute.
     */
    interface ResourceBuilderStepServiceVersion {
        ResourceBuilderStepServiceInstanceId withServiceVersion(String serviceVersion);
        ResourceBuilderStepServiceInstanceId withNoServiceVersion();
    }

    /**
     * A unique identifier for the service instance (e.g., a UUID or pod name). This is required if multiple instances
     * of the same service run concurrently.
     * <p>
     * Use {@link #withNoServiceInstanceId()} to intentionally omit this attribute.
     */
    interface ResourceBuilderStepServiceInstanceId {
        ResourceBuilderStepDeploymentEnvironmentName withServiceInstanceId(String serviceInstanceId);
        ResourceBuilderStepDeploymentEnvironmentName withNoServiceInstanceId();
    }

    /**
     * The environment where the service is running (e.g., production, staging)
     * <p>
     * Use {@link #withNoDeploymentEnvironmentName()} to intentionally omit this attribute.
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
