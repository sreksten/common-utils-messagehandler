package com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues;

import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;

/**
 * Known values for {@link OTelTags#HOST_ARCH}.
 */
public enum HostArchKnownValuesEnum implements OTelTagKnownValue {
    AMD64("amd64"),
    ARM32("arm32"),
    ARM64("arm64"),
    IA64("ia64"),
    PPC32("ppc32"),
    PPC64("ppc64"),
    S390X("s390x"),
    X86("x86");

    private final String value;

    HostArchKnownValuesEnum(final String value) {
        this.value = value;
    }

    @Override
    public OTelTags getTag() {
        return OTelTags.HOST_ARCH;
    }

    @Override
    public String getValue() {
        return value;
    }
}

