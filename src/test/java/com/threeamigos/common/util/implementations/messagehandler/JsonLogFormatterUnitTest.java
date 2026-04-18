package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.ContextInfo;
import com.threeamigos.common.util.interfaces.messagehandler.LogLevelEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonLogFormatter unit tests")
@Tag("unit")
@Tag("messageHandler")
class JsonLogFormatterUnitTest {

    private final JsonLogFormatter formatter = new JsonLogFormatter();
    private final ContextInfo contextInfo = new ContextInfoImpl();

    @Test
    @DisplayName("format() should return a single line starting with '{' and ending with '}'")
    void formatShouldReturnSingleJsonObject() {
        String result = formatter.format(LogLevelEnum.INFO, "hello", contextInfo);
        assertTrue(result.startsWith("{"), "Output should start with '{'");
        assertTrue(result.endsWith("}"), "Output should end with '}'");
        assertFalse(result.contains("\n"), "Output should be a single line");
    }

    @Test
    @DisplayName("format() should contain timestamp, level, and message keys")
    void formatShouldContainRequiredKeys() {
        String result = formatter.format(LogLevelEnum.INFO, "hello world", contextInfo);
        assertTrue(result.contains("\"timestamp\""), "Output should contain 'timestamp' key");
        assertTrue(result.contains("\"level\""), "Output should contain 'level' key");
        assertTrue(result.contains("\"message\""), "Output should contain 'message' key");
        assertTrue(result.contains("\"INFO\""), "Output should contain the level value");
        assertTrue(result.contains("\"hello world\""), "Output should contain the message value");
    }

    @Test
    @DisplayName("format() should escape double-quotes in message")
    void formatShouldEscapeDoubleQuotes() {
        String result = formatter.format(LogLevelEnum.INFO, "say \"hello\"", contextInfo);
        assertTrue(result.contains("\\\"hello\\\""), "Double-quotes should be escaped as \\\"");
    }

    @Test
    @DisplayName("format() should escape backslashes in message")
    void formatShouldEscapeBackslashes() {
        String result = formatter.format(LogLevelEnum.INFO, "path\\to\\file", contextInfo);
        assertTrue(result.contains("path\\\\to\\\\file"), "Backslashes should be escaped as \\\\");
    }

    @Test
    @DisplayName("format() should escape newlines in message")
    void formatShouldEscapeNewlines() {
        String result = formatter.format(LogLevelEnum.INFO, "line1\nline2", contextInfo);
        assertTrue(result.contains("\\n"), "Newlines should be escaped as \\n");
        assertFalse(result.contains("\n"), "No literal newlines should appear in the JSON value");
    }

    @Test
    @DisplayName("format() should escape carriage returns in message")
    void formatShouldEscapeCarriageReturns() {
        String result = formatter.format(LogLevelEnum.INFO, "line\r", contextInfo);
        assertTrue(result.contains("\\r"), "Carriage returns should be escaped as \\r");
    }

    @Test
    @DisplayName("format() should escape tab characters in message")
    void formatShouldEscapeTabs() {
        String result = formatter.format(LogLevelEnum.INFO, "col1\tcol2", contextInfo);
        assertTrue(result.contains("\\t"), "Tab characters should be escaped as \\t");
    }

    @Test
    @DisplayName("format() should escape ASCII control characters as \\uXXXX")
    void formatShouldEscapeControlCharacters() {
        String result = formatter.format(LogLevelEnum.INFO, "ctrl\u0001char", contextInfo);
        assertTrue(result.contains("\\u0001"), "Control character U+0001 should be escaped as \\u0001");
    }

    @Test
    @DisplayName("formatException(Exception) should contain EXCEP level and stackTrace key")
    void formatExceptionShouldContainExcepLevelAndStackTrace() {
        RuntimeException ex = new RuntimeException("boom");
        String result = formatter.formatException(ex, contextInfo);
        assertTrue(result.startsWith("{"), "Output should be a JSON object");
        assertTrue(result.contains("\"level\":\"EXCEP\""), "Output should contain EXCEP level");
        assertTrue(result.contains("\"stackTrace\""), "Output should contain 'stackTrace' key");
        assertTrue(result.contains("boom"), "Output should contain the exception message");
        // Stack trace newlines should be escaped
        assertFalse(result.contains("\n"), "No literal newlines should appear in JSON");
    }

    @Test
    @DisplayName("formatException(String, Exception) should contain prefix and stackTrace key")
    void formatExceptionWithPrefixShouldContainPrefixAndStackTrace() {
        RuntimeException ex = new RuntimeException("root");
        String result = formatter.formatException("ctx", ex, contextInfo);
        assertTrue(result.contains("ctx"), "Output should contain the prefix");
        assertTrue(result.contains("\"stackTrace\""), "Output should contain 'stackTrace' key");
        assertFalse(result.contains("\n"), "No literal newlines should appear in JSON");
    }

    @Test
    @DisplayName("format() should include a 'context' object when context is non-empty")
    void formatShouldIncludeContextObjectWhenNonEmpty() {
        contextInfo.add("userId", "42");
        String result = formatter.format(LogLevelEnum.INFO, "msg", contextInfo);
        assertTrue(result.contains("\"context\""), "Output should contain 'context' key");
        assertTrue(result.contains("\"userId\":\"42\""), "Output should contain the context entry");
        assertTrue(result.endsWith("}"), "Output should still end with '}'");
    }

    @Test
    @DisplayName("format() should include multiple context entries separated by commas")
    void formatShouldIncludeMultipleContextEntries() {
        contextInfo.add("a", "1");
        contextInfo.add("b", "2");
        String result = formatter.format(LogLevelEnum.INFO, "msg", contextInfo);
        assertTrue(result.contains("\"a\":\"1\""), "Output should contain first context entry");
        assertTrue(result.contains("\"b\":\"2\""), "Output should contain second context entry");
    }

    @Test
    @DisplayName("format() should JSON-escape special characters in context keys and values")
    void formatShouldEscapeSpecialCharsInContext() {
        contextInfo.add("k\"ey", "val\"ue");
        String result = formatter.format(LogLevelEnum.INFO, "msg", contextInfo);
        assertTrue(result.contains("k\\\"ey"), "Context key should be JSON-escaped");
        assertTrue(result.contains("val\\\"ue"), "Context value should be JSON-escaped");
    }

    @Test
    @DisplayName("formatException(Exception) should include 'context' object when context is non-empty")
    void formatExceptionShouldIncludeContextObjectWhenNonEmpty() {
        contextInfo.add("reqId", "abc");
        RuntimeException ex = new RuntimeException("boom");
        String result = formatter.formatException(ex, contextInfo);
        assertTrue(result.contains("\"context\""), "Output should contain 'context' key");
        assertTrue(result.contains("\"reqId\":\"abc\""), "Output should contain the context entry");
    }

    @Test
    @DisplayName("formatException(String, Exception) should include 'context' object when context is non-empty")
    void formatExceptionWithPrefixShouldIncludeContextObjectWhenNonEmpty() {
        contextInfo.add("reqId", "abc");
        RuntimeException ex = new RuntimeException("boom");
        String result = formatter.formatException("pfx", ex, contextInfo);
        assertTrue(result.contains("\"context\""), "Output should contain 'context' key");
        assertTrue(result.contains("\"reqId\":\"abc\""), "Output should contain the context entry");
    }

    @Test
    @DisplayName("format() should promote CLASS_NAME to root level when present")
    void formatShouldPromoteClassNameToRootLevel() {
        contextInfo.add(ContextInfo.CLASS_NAME, "com.example.Foo");
        String result = formatter.format(LogLevelEnum.INFO, "msg", contextInfo);
        assertTrue(result.contains("\"className\":\"com.example.Foo\""), "className should appear at root level");
        assertFalse(result.contains("\"context\""), "CLASS_NAME alone should not produce a context object");
    }

    @Test
    @DisplayName("format() should place className between level and message")
    void formatShouldPlaceClassNameBetweenLevelAndMessage() {
        contextInfo.add(ContextInfo.CLASS_NAME, "com.example.Foo");
        String result = formatter.format(LogLevelEnum.INFO, "msg", contextInfo);
        int levelIdx = result.indexOf("\"level\"");
        int classNameIdx = result.indexOf("\"className\"");
        int messageIdx = result.indexOf("\"message\"");
        assertTrue(levelIdx < classNameIdx && classNameIdx < messageIdx,
                "className should appear after level and before message");
    }

    @Test
    @DisplayName("format() should exclude CLASS_NAME from the context object when other entries are also present")
    void formatShouldExcludeClassNameFromContextObject() {
        contextInfo.add(ContextInfo.CLASS_NAME, "com.example.Foo");
        contextInfo.add("userId", "42");
        String result = formatter.format(LogLevelEnum.INFO, "msg", contextInfo);
        assertTrue(result.contains("\"className\":\"com.example.Foo\""), "className should appear at root level");
        assertTrue(result.contains("\"context\""), "Other context entries should still produce a context object");
        assertTrue(result.contains("\"userId\":\"42\""), "Other context entries should be in context object");
        // CLASS_NAME should not appear inside the context object
        int contextIdx = result.indexOf("\"context\"");
        assertFalse(result.substring(contextIdx).contains("\"" + ContextInfo.CLASS_NAME + "\""),
                "CLASS_NAME should not appear inside the context object");
    }

    @Test
    @DisplayName("formatException(Exception) should promote CLASS_NAME to root level")
    void formatExceptionShouldPromoteClassNameToRootLevel() {
        contextInfo.add(ContextInfo.CLASS_NAME, "com.example.Bar");
        RuntimeException ex = new RuntimeException("boom");
        String result = formatter.formatException(ex, contextInfo);
        assertTrue(result.contains("\"className\":\"com.example.Bar\""), "className should appear at root level");
        assertFalse(result.contains("\"context\""), "CLASS_NAME alone should not produce a context object");
    }

    @Test
    @DisplayName("formatException(String, Exception) should promote CLASS_NAME to root level")
    void formatExceptionWithPrefixShouldPromoteClassNameToRootLevel() {
        contextInfo.add(ContextInfo.CLASS_NAME, "com.example.Bar");
        RuntimeException ex = new RuntimeException("boom");
        String result = formatter.formatException("pfx", ex, contextInfo);
        assertTrue(result.contains("\"className\":\"com.example.Bar\""), "className should appear at root level");
        assertFalse(result.contains("\"context\""), "CLASS_NAME alone should not produce a context object");
    }
}
