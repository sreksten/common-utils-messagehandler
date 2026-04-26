package com.threeamigos.common.util.implementations.messagehandler.otel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("OTelTags unit tests")
@Tag("unit")
@Tag("messageHandler")
class OTelTagsUnitTest {

    @Test
    @DisplayName("enum should expose expected sample values")
    void enumShouldExposeExpectedSampleValues() {
        assertEquals("error.type", OTelTags.ERROR_TYPE.getValue());
        assertEquals("server.address", OTelTags.SERVER_ADDRESS.getValue());
        assertEquals("server.port", OTelTags.SERVER_PORT.getValue());
        assertEquals("url.scheme", OTelTags.URL_SCHEME.getValue());
        assertEquals("exception", OTelTags.EVENT_EXCEPTION.getValue());
        assertEquals("syslog.facility", OTelTags.SYSLOG_FACILITY.getValue());
        assertEquals("host.name", OTelTags.HOST_NAME.getValue());
        assertEquals("http.response.status_code", OTelTags.HTTP_RESPONSE_STATUS_CODE.getValue());
        assertEquals("cloud.region", OTelTags.CLOUD_REGION.getValue());
        assertEquals("cloud.availability_zone", OTelTags.CLOUD_AVAILABILITY_ZONE.getValue());
        assertEquals("cloud.platform", OTelTags.CLOUD_PLATFORM.getValue());
        assertEquals("cloud.resource_id", OTelTags.CLOUD_RESOURCE_ID.getValue());
        assertEquals("service.instance.id", OTelTags.SERVICE_INSTANCE_ID.getValue());
        assertEquals("exception.type", OTelTags.EXCEPTION_TYPE.getValue());
        assertEquals("exception.message", OTelTags.EXCEPTION_MESSAGE.getValue());
        assertEquals("exception.stacktrace", OTelTags.EXCEPTION_STACKTRACE.getValue());
        assertEquals("code.function.name", OTelTags.CODE_FUNCTION_NAME.getValue());
        assertEquals("code.file.path", OTelTags.CODE_FILE_PATH.getValue());
        assertEquals("code.line.number", OTelTags.CODE_LINE_NUMBER.getValue());
        assertEquals("code.stacktrace", OTelTags.CODE_STACKTRACE.getValue());
        assertEquals("code.namespace", OTelTags.CODE_NAMESPACE.getValue());
    }

    @Test
    @DisplayName("all tags should have non-empty values and be unique")
    void allTagsShouldHaveNonEmptyUniqueValues() {
        Set<String> values = Arrays.stream(OTelTags.values())
                .map(OTelTags::getValue)
                .peek(value -> {
                    assertNotNull(value);
                    assertFalse(value.isEmpty());
                })
                .collect(Collectors.toSet());

        assertEquals(OTelTags.values().length, values.size(), "Enum contains duplicate mapped values");
    }
}
