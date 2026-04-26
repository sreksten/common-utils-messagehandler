package com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues;

import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;

/**
 * Known values for {@link OTelTags#K8S_NODE_CONDITION_TYPE}.
 */
public enum K8sNodeConditionTypeKnownValuesEnum implements OTelTagKnownValue {
    DISK_PRESSURE("DiskPressure"),
    MEMORY_PRESSURE("MemoryPressure"),
    NETWORK_UNAVAILABLE("NetworkUnavailable"),
    PID_PRESSURE("PIDPressure"),
    READY("Ready");

    private final String value;

    K8sNodeConditionTypeKnownValuesEnum(final String value) {
        this.value = value;
    }

    @Override
    public OTelTags getTag() {
        return OTelTags.K8S_NODE_CONDITION_TYPE;
    }

    @Override
    public String getValue() {
        return value;
    }
}

