package com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues;

import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;

/**
 * Known values for {@link OTelTags#K8S_SERVICE_ENDPOINT_CONDITION}.
 */
public enum K8sServiceEndpointConditionKnownValuesEnum implements OTelTagKnownValue {
    READY("ready"),
    SERVING("serving"),
    TERMINATING("terminating");

    private final String value;

    K8sServiceEndpointConditionKnownValuesEnum(final String value) {
        this.value = value;
    }

    @Override
    public OTelTags getTag() {
        return OTelTags.K8S_SERVICE_ENDPOINT_CONDITION;
    }

    @Override
    public String getValue() {
        return value;
    }
}

