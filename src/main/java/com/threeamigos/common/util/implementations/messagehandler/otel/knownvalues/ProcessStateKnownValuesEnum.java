package com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues;

import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;

/**
 * Known values for {@link OTelTags#PROCESS_STATE}.
 */
public enum ProcessStateKnownValuesEnum implements OTelTagKnownValue {
    RUNNING("running"),
    SLEEPING("sleeping"),
    STOPPED("stopped"),
    DEFUNCT("defunct");

    private final String value;

    ProcessStateKnownValuesEnum(final String value) {
        this.value = value;
    }

    @Override
    public OTelTags getTag() {
        return OTelTags.PROCESS_STATE;
    }

    @Override
    public String getValue() {
        return value;
    }
}

