package com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues;

import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;

/**
 * Known values for {@link OTelTags#CLOUD_PROVIDER}.
 */
public enum CloudProviderKnownValuesEnum implements OTelTagKnownValue {
    AKAMAI_CLOUD("akamai_cloud"),
    ALIBABA_CLOUD("alibaba_cloud"),
    AWS("aws"),
    AZURE("azure"),
    GCP("gcp"),
    HEROKU("heroku"),
    HETZNER("hetzner"),
    IBM_CLOUD("ibm_cloud"),
    ORACLE_CLOUD("oracle_cloud"),
    TENCENT_CLOUD("tencent_cloud"),
    VULTR("vultr");

    private final String value;

    CloudProviderKnownValuesEnum(final String value) {
        this.value = value;
    }

    @Override
    public OTelTags getTag() {
        return OTelTags.CLOUD_PROVIDER;
    }

    @Override
    public String getValue() {
        return value;
    }
}

