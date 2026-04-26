package com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues;

import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;

/**
 * Known values for {@link OTelTags#K8S_VOLUME_TYPE}.
 */
public enum K8sVolumeTypeKnownValuesEnum implements OTelTagKnownValue {
    CONFIG_MAP("configMap"),
    DOWNWARD_API("downwardAPI"),
    EMPTY_DIR("emptyDir"),
    LOCAL("local"),
    PERSISTENT_VOLUME_CLAIM("persistentVolumeClaim"),
    SECRET("secret");

    private final String value;

    K8sVolumeTypeKnownValuesEnum(final String value) {
        this.value = value;
    }

    @Override
    public OTelTags getTag() {
        return OTelTags.K8S_VOLUME_TYPE;
    }

    @Override
    public String getValue() {
        return value;
    }
}

