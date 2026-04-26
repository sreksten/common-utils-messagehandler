package com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues;

import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;

/**
 * Known values for {@link OTelTags#K8S_SERVICE_TYPE}.
 */
public enum K8sServiceTypeKnownValuesEnum implements OTelTagKnownValue {
    CLUSTER_IP("ClusterIP"),
    NODE_PORT("NodePort"),
    LOAD_BALANCER("LoadBalancer"),
    EXTERNAL_NAME("ExternalName");

    private final String value;

    K8sServiceTypeKnownValuesEnum(final String value) {
        this.value = value;
    }

    @Override
    public OTelTags getTag() {
        return OTelTags.K8S_SERVICE_TYPE;
    }

    @Override
    public String getValue() {
        return value;
    }
}

