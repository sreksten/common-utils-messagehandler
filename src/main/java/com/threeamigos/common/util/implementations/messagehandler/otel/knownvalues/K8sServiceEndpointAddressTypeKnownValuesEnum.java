package com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues;

import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;

/**
 * Known values for {@link OTelTags#K8S_SERVICE_ENDPOINT_ADDRESS_TYPE}.
 */
public enum K8sServiceEndpointAddressTypeKnownValuesEnum implements OTelTagKnownValue {
    IPV4("IPv4"),
    IPV6("IPv6"),
    FQDN("FQDN");

    private final String value;

    K8sServiceEndpointAddressTypeKnownValuesEnum(final String value) {
        this.value = value;
    }

    @Override
    public OTelTags getTag() {
        return OTelTags.K8S_SERVICE_ENDPOINT_ADDRESS_TYPE;
    }

    @Override
    public String getValue() {
        return value;
    }
}

