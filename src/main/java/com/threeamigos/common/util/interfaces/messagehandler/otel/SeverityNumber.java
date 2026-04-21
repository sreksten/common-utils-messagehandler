package com.threeamigos.common.util.interfaces.messagehandler.otel;

/**
 * Numerical severity of a log record, as defined by the
 * <a href="https://opentelemetry.io/docs/specs/otel/logs/data-model/#field-severitynumber">
 * OTel Log Data Model §SeverityNumber</a>.
 * <p>
 * The range 1–24 is divided into six severity levels with four sub-ranges each.
 * {@link #UNSPECIFIED} (value 0) indicates that no severity was set.
 *
 * @author Stefano Reksten
 */
public enum SeverityNumber {

    UNSPECIFIED(0),
    TRACE(1),
    TRACE2(2),
    TRACE3(3),
    TRACE4(4),
    DEBUG(5),
    DEBUG2(6),
    DEBUG3(7),
    DEBUG4(8),
    INFO(9),
    INFO2(10),
    INFO3(11),
    INFO4(12),
    WARN(13),
    WARN2(14),
    WARN3(15),
    WARN4(16),
    ERROR(17),
    ERROR2(18),
    ERROR3(19),
    ERROR4(20),
    FATAL(21),
    FATAL2(22),
    FATAL3(23),
    FATAL4(24);

    private final int value;

    SeverityNumber(final int value) {
        this.value = value;
    }

    /**
     * @return the integer value of this severity number (0–24).
     */
    public int getValue() {
        return value;
    }

    /**
     * Returns the {@code SeverityNumber} whose {@link #getValue()} equals {@code value}.
     * Returns {@link #UNSPECIFIED} for any value outside the range 0–24.
     *
     * @param value the integer severity number from an OTLP log record.
     * @return the matching {@code SeverityNumber}, or {@link #UNSPECIFIED} if out of range.
     */
    public static SeverityNumber fromValue(final int value) {
        for (SeverityNumber sn : values()) {
            if (sn.value == value) {
                return sn;
            }
        }
        return UNSPECIFIED;
    }
}
