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

@DisplayName("Names unit tests")
@Tag("unit")
@Tag("messageHandler")
class NamesUnitTest {

    @Test
    @DisplayName("enum should expose expected sample values")
    void enumShouldExposeExpectedSampleValues() {
        assertEquals("error.type", Names.ATTR_ERROR_TYPE.getValue());
        assertEquals("server.address", Names.ATTR_SERVER_ADDRESS.getValue());
        assertEquals("server.port", Names.ATTR_SERVER_PORT.getValue());
        assertEquals("url.scheme", Names.ATTR_URL_SCHEME.getValue());
        assertEquals("exception", Names.EVENT_EXCEPTION.getValue());
        assertEquals("syslog.facility", Names.ATTR_SYSLOG_FACILITY.getValue());
        assertEquals("host.name", Names.RES_HOST_NAME.getValue());
        assertEquals("http.response.status_code", Names.ATTR_HTTP_RESPONSE_STATUS_CODE.getValue());
        assertEquals("cloud.region", Names.RES_CLOUD_REGION.getValue());
        assertEquals("service.instance.id", Names.ATTR_SERVICE_INSTANCE_ID.getValue());
        assertEquals("exception.type", Names.ATTR_EXCEPTION_TYPE.getValue());
        assertEquals("exception.message", Names.ATTR_EXCEPTION_MESSAGE.getValue());
        assertEquals("exception.stacktrace", Names.ATTR_EXCEPTION_STACKTRACE.getValue());
        assertEquals("code.function.name", Names.ATTR_CODE_FUNCTION_NAME.getValue());
        assertEquals("code.file.path", Names.ATTR_CODE_FILE_PATH.getValue());
        assertEquals("code.line.number", Names.ATTR_CODE_LINE_NUMBER.getValue());
        assertEquals("code.stacktrace", Names.ATTR_CODE_STACKTRACE.getValue());
        assertEquals("code.namespace", Names.ATTR_CODE_NAMESPACE.getValue());
    }

    @Test
    @DisplayName("all names should have non-empty values and be unique")
    void allNamesShouldHaveNonEmptyUniqueValues() {
        Set<String> values = Arrays.stream(Names.values())
                .map(Names::getValue)
                .peek(value -> {
                    assertNotNull(value);
                    assertFalse(value.isEmpty());
                })
                .collect(Collectors.toSet());

        assertEquals(Names.values().length, values.size(), "Enum contains duplicate mapped values");
    }
}
