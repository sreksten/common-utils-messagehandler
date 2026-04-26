package com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues;

import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;

/**
 * Known values for {@link OTelTags#K8S_NAMESPACE_PHASE}.
 */
public enum K8sNamespacePhaseKnownValuesEnum implements OTelTagKnownValue {
    ACTIVE("active"),
    TERMINATING("terminating");

    private final String value;

    K8sNamespacePhaseKnownValuesEnum(final String value) {
        this.value = value;
    }

    @Override
    public OTelTags getTag() {
        return OTelTags.K8S_NAMESPACE_PHASE;
    }

    @Override
    public String getValue() {
        return value;
    }
}

