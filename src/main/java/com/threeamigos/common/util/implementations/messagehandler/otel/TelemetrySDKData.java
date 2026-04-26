package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;

import java.util.Collections;
import java.util.List;

/**
 *
 * @author Stefano Reksten
 */
public final class TelemetrySDKData {

    private static final List<KeyValue> DEFAULT_ATTRIBUTES =
            Collections.unmodifiableList(
                KeyValueListBuilderFactory.getBuilder()
                        .withString(OTelTags.TELEMETRY_SDK_LANGUAGE, "java")
                        .withString(OTelTags.TELEMETRY_SDK_NAME, "message-handler")
                        .withString(OTelTags.TELEMETRY_SDK_VERSION, "1.0.0")
                        .build()
            );

    public static List<KeyValue> getDefaultAttributes() {
        return DEFAULT_ATTRIBUTES;
    }

    private TelemetrySDKData() {}
}
