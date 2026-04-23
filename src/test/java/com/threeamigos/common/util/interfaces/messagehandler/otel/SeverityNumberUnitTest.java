package com.threeamigos.common.util.interfaces.messagehandler.otel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    @DisplayName("Category helper methods should identify all four variants and reject others")
    void categoryHelperMethodsShouldIdentifyAllVariants() {
        assertTrue(SeverityNumber.TRACE.isTrace());
        assertTrue(SeverityNumber.TRACE2.isTrace());
        assertTrue(SeverityNumber.TRACE3.isTrace());
        assertTrue(SeverityNumber.TRACE4.isTrace());
        assertFalse(SeverityNumber.INFO.isTrace());

        assertTrue(SeverityNumber.DEBUG.isDebug());
        assertTrue(SeverityNumber.DEBUG2.isDebug());
        assertTrue(SeverityNumber.DEBUG3.isDebug());
        assertTrue(SeverityNumber.DEBUG4.isDebug());
        assertFalse(SeverityNumber.INFO.isDebug());

        assertTrue(SeverityNumber.INFO.isInfo());
        assertTrue(SeverityNumber.INFO2.isInfo());
        assertTrue(SeverityNumber.INFO3.isInfo());
        assertTrue(SeverityNumber.INFO4.isInfo());
        assertFalse(SeverityNumber.WARN.isInfo());

        assertTrue(SeverityNumber.WARN.isWarn());
        assertTrue(SeverityNumber.WARN2.isWarn());
        assertTrue(SeverityNumber.WARN3.isWarn());
        assertTrue(SeverityNumber.WARN4.isWarn());
        assertFalse(SeverityNumber.ERROR.isWarn());

        assertTrue(SeverityNumber.ERROR.isError());
        assertTrue(SeverityNumber.ERROR2.isError());
        assertTrue(SeverityNumber.ERROR3.isError());
        assertTrue(SeverityNumber.ERROR4.isError());
        assertFalse(SeverityNumber.FATAL.isError());

        assertTrue(SeverityNumber.FATAL.isFatal());
        assertTrue(SeverityNumber.FATAL2.isFatal());
        assertTrue(SeverityNumber.FATAL3.isFatal());
        assertTrue(SeverityNumber.FATAL4.isFatal());
        assertFalse(SeverityNumber.UNSPECIFIED.isFatal());
    }
}
