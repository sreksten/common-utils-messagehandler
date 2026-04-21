package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.interfaces.messagehandler.ContextInfo;
import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * A plain-text {@link LogRecordFormatter} that mirrors the output layout of
 * {@code PlainTextLogFormatter}, but starts from an OTel {@link LogRecord}.
 * <p>
 * Output format:
 * <pre>
 * [timestamp] [className] [LEVEL] message {k=v, ...}
 * </pre>
 *
 * @author Stefano Reksten
 */
public class PlainTextLogRecordFormatter implements LogRecordFormatter {

    private boolean abbreviateClassName = false;

    /**
     * Controls whether the class name from {@link ContextInfo#CLASS_NAME} should be abbreviated
     * using Logback-style package abbreviation.
     *
     * @param abbreviate {@code true} to abbreviate package segments
     */
    public void setAbbreviateClassName(final boolean abbreviate) {
        this.abbreviateClassName = abbreviate;
    }

    /**
     * @return {@code true} if class-name abbreviation is enabled.
     */
    public boolean isAbbreviateClassName() {
        return abbreviateClassName;
    }

    @Nonnull
    @Override
    public String format(@Nonnull final LogRecord logRecord) {
        Objects.requireNonNull(logRecord, MessageHandlerResourceBundle.get("logRecordMustNotBeNull"));
        String date = formatTimestamp(logRecord.getTimestamp());
        String classNameSegment = formatClassNameSegment(logRecord.getAttributes());
        String level = resolveLevel(logRecord);
        String message = resolveMessage(logRecord);
        String ctx = formatContext(logRecord.getAttributes());
        return String.format("[%s]%s [%-5s] %s%s", date, classNameSegment, level, message, ctx);
    }

    private static String formatTimestamp(final Instant timestamp) {
        ZonedDateTime dateTime = timestamp == null
                ? ZonedDateTime.now()
                : ZonedDateTime.ofInstant(timestamp, ZoneId.systemDefault());
        return dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private String formatClassNameSegment(final List<KeyValue> attributes) {
        String className = extractClassName(attributes);
        if (className == null) {
            return "";
        }
        String name = abbreviateClassName ? abbreviate(className) : className;
        return " [" + name + "]";
    }

    private static String resolveLevel(final LogRecord logRecord) {
        String severityText = logRecord.getSeverityText();
        if (severityText != null && !severityText.trim().isEmpty()) {
            return severityText;
        }
        SeverityNumber severityNumber = logRecord.getSeverityNumber();
        if (severityNumber != null && severityNumber != SeverityNumber.UNSPECIFIED) {
            return severityNumber.name();
        }
        return SeverityNumber.UNSPECIFIED.name();
    }

    private static String resolveMessage(final LogRecord logRecord) {
        AnyValue body = logRecord.getBody();
        if (body != null) {
            String bodyMessage = anyValueToString(body);
            if (!bodyMessage.isEmpty()) {
                return bodyMessage;
            }
        }
        return logRecord.getEventName() != null ? logRecord.getEventName() : "";
    }

    private static String formatContext(final List<KeyValue> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return "";
        }

        boolean hasOnlyClassName = true;
        for (KeyValue attribute : attributes) {
            if (attribute == null) {
                continue;
            }
            if (!ContextInfo.CLASS_NAME.equals(attribute.getKey())) {
                hasOnlyClassName = false;
                break;
            }
        }
        if (hasOnlyClassName) {
            return "";
        }

        StringJoiner sj = new StringJoiner(", ", " {", "}");
        boolean hasEntries = false;
        for (KeyValue attribute : attributes) {
            if (attribute == null) {
                continue;
            }
            String key = attribute.getKey();
            if (ContextInfo.CLASS_NAME.equals(key)) {
                continue;
            }
            String value = attribute.getValue() == null ? "null" : anyValueToString(attribute.getValue());
            sj.add(String.valueOf(key) + "=" + value);
            hasEntries = true;
        }
        return hasEntries ? sj.toString() : "";
    }

    private static String extractClassName(final List<KeyValue> attributes) {
        if (attributes == null) {
            return null;
        }
        for (KeyValue attribute : attributes) {
            if (attribute == null) {
                continue;
            }
            if (!ContextInfo.CLASS_NAME.equals(attribute.getKey())) {
                continue;
            }
            AnyValue value = attribute.getValue();
            if (value == null) {
                return null;
            }
            if (value.getType() == AnyValue.Type.STRING) {
                return value.asString();
            }
            return anyValueToString(value);
        }
        return null;
    }

    private static String anyValueToString(final AnyValue value) {
        AnyValue.Type type = value.getType();
        if (type == null) {
            throw new IllegalArgumentException(MessageHandlerResourceBundle.get("anyValueTypeMustNotBeNull"));
        }

        if (type == AnyValue.Type.EMPTY) {
            return "";
        }
        if (type == AnyValue.Type.STRING) {
            return value.asString();
        }
        if (type == AnyValue.Type.BOOL) {
            return String.valueOf(value.asBoolean());
        }
        if (type == AnyValue.Type.INT) {
            return String.valueOf(value.asLong());
        }
        if (type == AnyValue.Type.DOUBLE) {
            return String.valueOf(value.asDouble());
        }
        if (type == AnyValue.Type.BYTES) {
            return Base64.getEncoder().encodeToString(value.asBytes());
        }
        if (type == AnyValue.Type.ARRAY) {
            return formatArray(value.asArray());
        }
        return formatKvList(value.asKvList());
    }

    private static String formatArray(final List<AnyValue> values) {
        StringJoiner sj = new StringJoiner(", ", "[", "]");
        if (values == null) {
            return sj.toString();
        }
        for (AnyValue value : values) {
            sj.add(value == null ? "null" : anyValueToString(value));
        }
        return sj.toString();
    }

    private static String formatKvList(final List<KeyValue> values) {
        StringJoiner sj = new StringJoiner(", ", "{", "}");
        if (values == null) {
            return sj.toString();
        }
        for (KeyValue value : values) {
            if (value == null) {
                sj.add("null");
            } else {
                String key = String.valueOf(value.getKey());
                String elementValue = value.getValue() == null ? "null" : anyValueToString(value.getValue());
                sj.add(key + "=" + elementValue);
            }
        }
        return sj.toString();
    }

    /**
     * Abbreviates a fully qualified class name using a Logback-style package abbreviation.
     *
     * @param className fully qualified class name
     * @return abbreviated class name
     */
    static String abbreviate(final String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot < 0) {
            return className;
        }
        String packagePart = className.substring(0, lastDot);
        String simpleName = className.substring(lastDot + 1);
        StringBuilder sb = new StringBuilder();
        int start = 0;
        while (start < packagePart.length()) {
            sb.append(packagePart.charAt(start)).append('.');
            int dot = packagePart.indexOf('.', start + 1);
            start = dot < 0 ? packagePart.length() : dot + 1;
        }
        return sb.append(simpleName).toString();
    }
}
