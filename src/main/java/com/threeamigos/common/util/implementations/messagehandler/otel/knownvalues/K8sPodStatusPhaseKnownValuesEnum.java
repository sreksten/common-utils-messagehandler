package com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues;

import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;

/**
 * Known values for {@link OTelTags#K8S_POD_STATUS_PHASE}.
 */
public enum K8sPodStatusPhaseKnownValuesEnum implements OTelTagKnownValue {
    PENDING("Pending"),
    RUNNING("Running"),
    SUCCEEDED("Succeeded"),
    FAILED("Failed"),
    UNKNOWN("Unknown");

    private final String value;

    K8sPodStatusPhaseKnownValuesEnum(final String value) {
        this.value = value;
    }

    @Override
    public OTelTags getTag() {
        return OTelTags.K8S_POD_STATUS_PHASE;
    }

    @Override
    public String getValue() {
        return value;
    }
}

