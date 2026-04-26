package com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues;

import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;

/**
 * Known values for {@link OTelTags#PROCESS_CONTEXT_SWITCH_TYPE}.
 */
public enum ProcessContextSwitchTypeKnownValuesEnum implements OTelTagKnownValue {
    VOLUNTARY("voluntary"),
    INVOLUNTARY("involuntary");

    private final String value;

    ProcessContextSwitchTypeKnownValuesEnum(final String value) {
        this.value = value;
    }

    @Override
    public OTelTags getTag() {
        return OTelTags.PROCESS_CONTEXT_SWITCH_TYPE;
    }

    @Override
    public String getValue() {
        return value;
    }
}

