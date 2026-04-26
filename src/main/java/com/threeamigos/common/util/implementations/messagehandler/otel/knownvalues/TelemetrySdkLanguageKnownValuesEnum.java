package com.threeamigos.common.util.implementations.messagehandler.otel.knownvalues;

import com.threeamigos.common.util.implementations.messagehandler.otel.OTelTags;

/**
 * Known values for {@link OTelTags#TELEMETRY_SDK_LANGUAGE}.
 */
public enum TelemetrySdkLanguageKnownValuesEnum implements OTelTagKnownValue {
    CPP("cpp"),
    DOTNET("dotnet"),
    ERLANG("erlang"),
    GO("go"),
    JAVA("java"),
    NODEJS("nodejs"),
    PHP("php"),
    PYTHON("python"),
    RUBY("ruby"),
    RUST("rust"),
    SWIFT("swift"),
    WEBJS("webjs");

    private final String value;

    TelemetrySdkLanguageKnownValuesEnum(final String value) {
        this.value = value;
    }

    @Override
    public OTelTags getTag() {
        return OTelTags.TELEMETRY_SDK_LANGUAGE;
    }

    @Override
    public String getValue() {
        return value;
    }
}

