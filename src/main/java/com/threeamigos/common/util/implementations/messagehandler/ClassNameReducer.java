package com.threeamigos.common.util.implementations.messagehandler;

/**
 *
 * @author Stefano Reksten
 */
public class ClassNameReducer {


    /**
     * Abbreviates a fully qualified class name using Logback-style package abbreviation:
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
    public static String reduce(final String className) {
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
