package com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues;

import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;

/**
 * Known values for {@link OTelTags#K8S_SERVICE_TRAFFIC_DISTRIBUTION}.
 */
public enum K8sServiceTrafficDistributionKnownValuesEnum implements OTelTagKnownValue {
    PREFER_SAME_ZONE("PreferSameZone"),
    PREFER_SAME_NODE("PreferSameNode");

    private final String value;

    K8sServiceTrafficDistributionKnownValuesEnum(final String value) {
        this.value = value;
    }

    @Override
    public OTelTags getTag() {
        return OTelTags.K8S_SERVICE_TRAFFIC_DISTRIBUTION;
    }

    @Override
    public String getValue() {
        return value;
    }
}

