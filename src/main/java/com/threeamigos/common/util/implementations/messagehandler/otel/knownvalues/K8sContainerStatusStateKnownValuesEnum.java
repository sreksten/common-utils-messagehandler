package com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues;

import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;

/**
 * Known values for {@link OTelTags#K8S_CONTAINER_STATUS_STATE}.
 */
public enum K8sContainerStatusStateKnownValuesEnum implements OTelTagKnownValue {
    RUNNING("running"),
    WAITING("waiting"),
    TERMINATED("terminated");

    private final String value;

    K8sContainerStatusStateKnownValuesEnum(final String value) {
        this.value = value;
    }

    @Override
    public OTelTags getTag() {
        return OTelTags.K8S_CONTAINER_STATUS_STATE;
    }

    @Override
    public String getValue() {
        return value;
    }
}

