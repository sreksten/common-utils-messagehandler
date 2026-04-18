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
import java.util.StringJoiner;

/**
 * A {@link LogFormatter} that produces human-readable plain-text log lines.
 * <p>
 * Each line is prefixed with an ISO-8601 timestamp and a fixed-width level label:
 * <pre>
 * [2026-04-17T00:58:28+02:00] [INFO ] User logged in
 * [2026-04-17T00:58:28+02:00] [ERROR] Something went wrong
 * [2026-04-17T00:58:28+02:00] [EXCEP] java.lang.NullPointerException: null
 * java.lang.NullPointerException: null
 *     at com.example.Foo.bar(Foo.java:42)
 *     ...
 * </pre>
 * When {@link ContextInfo#CLASS_NAME} is present in the context, it is inserted between the
 * timestamp and the level label:
 * <pre>
 * [2026-04-17T00:58:28+02:00] [com.example.Foo] [INFO ] User logged in
 * </pre>
 * When {@link #setAbbreviateClassName(boolean) abbreviation} is enabled, each package segment
 * is shortened to its first letter (Logback-style class name abbreviation), while the simple
 * class name is kept in full:
 * <pre>
 * [2026-04-17T00:58:28+02:00] [c.e.Foo] [INFO ] User logged in
 * </pre>
 * When a non-empty {@link ContextInfo} is provided, its remaining entries (excluding
 * {@link ContextInfo#CLASS_NAME}) are appended after the message as a brace-enclosed,
 * comma-separated list of {@code key=value} pairs:
 * <pre>
 * [2026-04-17T00:58:28+02:00] [INFO ] User logged in {userId=42, requestId=abc}
 * </pre>
 * This is the default formatter used by all {@code AbstractOutputMessageHandler} subclasses.
 *
 * @author Stefano Reksten
 */
public class PlainTextLogFormatter implements LogFormatter {

    private boolean abbreviateClassName = false;

    /**
     * Controls whether the class name from {@link ContextInfo#CLASS_NAME} is abbreviated
     * using Logback-style package abbreviation: each package segment is reduced to its first
     * letter, while the simple class name at the end is kept in full.
     * <p>
     * For example, {@code com.example.service.MyService} becomes {@code c.e.s.MyService}.
     * <p>
     * Defaults to {@code false}.
     *
     * @param abbreviate {@code true} to enable abbreviation, {@code false} to use the full name
     */
    public void setAbbreviateClassName(boolean abbreviate) {
        this.abbreviateClassName = abbreviate;
    }

    /**
     * Returns {@code true} if class name abbreviation is currently enabled.
     *
     * @return {@code true} if abbreviation is active
     */
    public boolean isAbbreviateClassName() {
        return abbreviateClassName;
    }

    @Nonnull
    @Override
    public String format(@Nonnull final LogLevelEnum level, @Nonnull final String message, @Nonnull ContextInfo contextInfo) {
        String date = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String classNameSegment = formatClassNameSegment(contextInfo);
        String ctx = formatContext(contextInfo);
        return String.format("[%s]%s [%-5s] %s%s", date, classNameSegment, level.name(), message, ctx);
    }

    @Nonnull
    @Override
    public String formatException(@Nonnull final Exception exception, @Nonnull ContextInfo contextInfo) {
        String date = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String classNameSegment = formatClassNameSegment(contextInfo);
        String ctx = formatContext(contextInfo);
        StringWriter sw = new StringWriter();
        exception.printStackTrace(new PrintWriter(sw, true));
        return String.format("[%s]%s [EXCEP] %s%s%n%s",
                date, classNameSegment, ExceptionMessageFormatter.detail(exception), ctx, sw);
    }

    @Nonnull
    @Override
    public String formatException(@Nonnull final String prefix, @Nonnull final Exception exception, @Nonnull ContextInfo contextInfo) {
        String date = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String classNameSegment = formatClassNameSegment(contextInfo);
        String ctx = formatContext(contextInfo);
        StringWriter sw = new StringWriter();
        exception.printStackTrace(new PrintWriter(sw, true));
        return String.format("[%s]%s [EXCEP] %s%s%n%s",
                date, classNameSegment, ExceptionMessageFormatter.withPrefix(prefix, exception), ctx, sw);
    }

    private String formatClassNameSegment(ContextInfo contextInfo) {
        Object className = contextInfo.get(ContextInfo.CLASS_NAME);
        if (className == null) {
            return "";
        }
        String name = abbreviateClassName ? abbreviate(className.toString()) : className.toString();
        return " [" + name + "]";
    }

    /**
     * Abbreviates a fully-qualified class name using Logback-style package abbreviation:
     * each package segment is reduced to its first letter, and the simple class name
     * (the last dot-separated token) is kept in full.
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code com.example.Foo} → {@code c.e.Foo}</li>
     *   <li>{@code Foo} → {@code Foo} (no package — unchanged)</li>
     * </ul>
     *
     * @param className the fully qualified class name to abbreviate
     * @return the abbreviated class name
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

    private static String formatContext(ContextInfo contextInfo) {
        Map<String, Object> values = contextInfo.getValues();
        boolean hasOnlyClassName = values.size() == 1 && values.containsKey(ContextInfo.CLASS_NAME);
        if (values.isEmpty() || hasOnlyClassName) {
            return "";
        }
        StringJoiner sj = new StringJoiner(", ", " {", "}");
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (ContextInfo.CLASS_NAME.equals(entry.getKey())) {
                continue;
            }
            sj.add(entry.getKey() + "=" + entry.getValue());
        }
        return sj.toString();
    }
}
