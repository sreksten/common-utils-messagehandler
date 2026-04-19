package com.threeamigos.common.util.interfaces.messagehandler.otel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("SeverityNumber unit tests")
@Tag("unit")
@Tag("messageHandler")
class SeverityNumberUnitTest {

    @Test
    @DisplayName("getValue() should expose the configured numeric mapping")
    void getValueShouldExposeNumericMapping() {
        assertEquals(0, SeverityNumber.UNSPECIFIED.getValue());
        assertEquals(1, SeverityNumber.TRACE.getValue());
        assertEquals(24, SeverityNumber.FATAL4.getValue());
    }

    @Test
    @DisplayName("fromValue() should resolve all valid values")
    void fromValueShouldResolveAllValidValues() {
        for (SeverityNumber severityNumber : SeverityNumber.values()) {
            assertSame(severityNumber, SeverityNumber.fromValue(severityNumber.getValue()));
        }
    }

    @Test
    @DisplayName("fromValue() should return UNSPECIFIED for out-of-range values")
    void fromValueShouldReturnUnspecifiedForOutOfRangeValues() {
        assertSame(SeverityNumber.UNSPECIFIED, SeverityNumber.fromValue(-1));
        assertSame(SeverityNumber.UNSPECIFIED, SeverityNumber.fromValue(25));
        assertSame(SeverityNumber.UNSPECIFIED, SeverityNumber.fromValue(Integer.MAX_VALUE));
    }
}
