package com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues;

import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;

/**
 * Known values for {@link OTelTags#K8S_NODE_CONDITION_STATUS}.
 */
public enum K8sNodeConditionStatusKnownValuesEnum implements OTelTagKnownValue {
    TRUE("true"),
    FALSE("false"),
    UNKNOWN("unknown");

    private final String value;

    K8sNodeConditionStatusKnownValuesEnum(final String value) {
        this.value = value;
    }

    @Override
    public OTelTags getTag() {
        return OTelTags.K8S_NODE_CONDITION_STATUS;
    }

    @Override
    public String getValue() {
        return value;
    }
}

