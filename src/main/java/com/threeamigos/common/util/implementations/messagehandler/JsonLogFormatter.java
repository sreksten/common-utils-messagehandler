package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.ContextInfo;
import com.threeamigos.common.util.interfaces.messagehandler.LogFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.LogLevelEnum;
import jakarta.annotation.Nonnull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * A {@link LogFormatter} that produces NDJSON (newline-delimited JSON) output — one JSON object
 * per log entry — suitable for log aggregation systems such as ELK, Datadog, and Splunk.
 * <p>
 * Each regular log entry produces:
 * <pre>
 * {"timestamp":"2026-04-17T00:58:28+02:00","level":"INFO","message":"User logged in"}
 * </pre>
 * When {@link ContextInfo#CLASS_NAME} is present in the context, it is promoted to a root-level
 * {@code "className"} field placed between {@code "level"} and {@code "message"}:
 * <pre>
 * {"timestamp":"...","level":"INFO","className":"com.example.Foo","message":"User logged in"}
 * </pre>
 * Exception entries also include a {@code stackTrace} field:
 * <pre>
 * {"timestamp":"...","level":"EXCEP","message":"NullPointerException: null","stackTrace":"..."}
 * </pre>
 * When a non-empty {@link ContextInfo} is provided, its remaining entries (excluding
 * {@link ContextInfo#CLASS_NAME}) are serialized as a nested {@code "context"} JSON object:
 * <pre>
 * {"timestamp":"...","level":"INFO","className":"com.example.Foo","message":"User logged in","context":{"userId":"42"}}
 * </pre>
 * All string values are JSON-escaped (handles {@code "}, {@code \}, {@code \n}, {@code \r},
 * {@code \t} and ASCII control characters). No external JSON library is required.
 *
 * @author Stefano Reksten
 */
public class JsonLogFormatter implements LogFormatter {

    @Nonnull
    @Override
    public String format(@Nonnull final LogLevelEnum level, @Nonnull final String message, @Nonnull ContextInfo contextInfo) {
        String ts = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return "{\"timestamp\":\"" + escape(ts)
                + "\",\"level\":\"" + escape(level.name())
                + formatClassName(contextInfo)
                + "\",\"message\":\"" + escape(message) + "\""
                + formatContext(contextInfo) + "}";
    }

    @Nonnull
    @Override
    public String formatException(@Nonnull final Exception exception, @Nonnull ContextInfo contextInfo) {
        String ts = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String detail = ExceptionMessageFormatter.detail(exception);
        StringWriter sw = new StringWriter();
        exception.printStackTrace(new PrintWriter(sw, true));
        return "{\"timestamp\":\"" + escape(ts)
                + "\",\"level\":\"EXCEP\""
                + formatClassName(contextInfo)
                + ",\"message\":\"" + escape(detail)
                + "\",\"stackTrace\":\"" + escape(sw.toString()) + "\""
                + formatContext(contextInfo) + "}";
    }

    @Nonnull
    @Override
    public String formatException(@Nonnull final String prefix, @Nonnull final Exception exception, @Nonnull ContextInfo contextInfo) {
        String ts = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String detail = ExceptionMessageFormatter.withPrefix(prefix, exception);
        StringWriter sw = new StringWriter();
        exception.printStackTrace(new PrintWriter(sw, true));
        return "{\"timestamp\":\"" + escape(ts)
                + "\",\"level\":\"EXCEP\""
                + formatClassName(contextInfo)
                + ",\"message\":\"" + escape(detail)
                + "\",\"stackTrace\":\"" + escape(sw.toString()) + "\""
                + formatContext(contextInfo) + "}";
    }

    private static String formatClassName(ContextInfo contextInfo) {
        Object className = contextInfo.get(ContextInfo.CLASS_NAME);
        if (className == null) {
            return "";
        }
        return ",\"className\":\"" + escape(className.toString()) + "\"";
    }

    private static String formatContext(ContextInfo contextInfo) {
        Map<String, Object> values = contextInfo.getValues();
        boolean hasOnlyClassName = values.size() == 1 && values.containsKey(ContextInfo.CLASS_NAME);
        if (values.isEmpty() || hasOnlyClassName) {
            return "";
        }
        StringBuilder sb = new StringBuilder(",\"context\":{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (ContextInfo.CLASS_NAME.equals(entry.getKey())) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            sb.append('"').append(escape(entry.getKey())).append("\":\"")
              .append(escape(entry.getValue().toString())).append('"');
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escape(final String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
