package com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues;

import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;

/**
 * Known values for {@link OTelTags#SERVICE_CRITICALITY}.
 */
public enum ServiceCriticalityKnownValuesEnum implements OTelTagKnownValue {
    CRITICAL("critical"),
    HIGH("high"),
    MEDIUM("medium"),
    LOW("low");

    private final String value;

    ServiceCriticalityKnownValuesEnum(final String value) {
        this.value = value;
    }

    @Override
    public OTelTags getTag() {
        return OTelTags.SERVICE_CRITICALITY;
    }

    @Override
    public String getValue() {
        return value;
    }
}

