package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.ContextInfo;
import com.threeamigos.common.util.interfaces.messagehandler.LogLevelEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PlainTextLogFormatter unit tests")
@Tag("unit")
@Tag("messageHandler")
class PlainTextLogFormatterUnitTest {

    private final PlainTextLogFormatter formatter = new PlainTextLogFormatter();
    private final ContextInfo contextInfo = new ContextInfoImpl();

    @Test
    @DisplayName("format() should contain the level and message")
    void formatShouldContainLevelAndMessage() {
        String result = formatter.format(LogLevelEnum.INFO, "hello world", contextInfo);
        assertTrue(result.contains("INFO"), "Output should contain the level");
        assertTrue(result.contains("hello world"), "Output should contain the message");
    }

    @Test
    @DisplayName("format() should left-pad short levels to 5 characters")
    void formatShouldPadShortLevels() {
        String info = formatter.format(LogLevelEnum.INFO, "msg", contextInfo);
        String warn = formatter.format(LogLevelEnum.WARN, "msg", contextInfo);
        // Both INFO and WARN should be followed by a space in the bracketed section
        assertTrue(info.contains("[INFO ]"), "INFO should be padded to 5 chars");
        assertTrue(warn.contains("[WARN ]"), "WARN should be padded to 5 chars");
    }

    @Test
    @DisplayName("format() should not add extra padding to 5-char levels")
    void formatShouldNotPad5CharLevels() {
        String error = formatter.format(LogLevelEnum.ERROR, "msg", contextInfo);
        String debug = formatter.format(LogLevelEnum.DEBUG, "msg", contextInfo);
        String trace = formatter.format(LogLevelEnum.TRACE, "msg", contextInfo);
        assertTrue(error.contains("[ERROR]"), "ERROR should not be padded");
        assertTrue(debug.contains("[DEBUG]"), "DEBUG should not be padded");
        assertTrue(trace.contains("[TRACE]"), "TRACE should not be padded");
    }

    @Test
    @DisplayName("format() should include an ISO-8601 timestamp")
    void formatShouldIncludeTimestamp() {
        String result = formatter.format(LogLevelEnum.INFO, "msg", contextInfo);
        // ISO-8601 timestamps contain 'T' as separator and '+' or 'Z' for offset
        assertTrue(result.contains("T"), "Output should contain an ISO-8601 timestamp");
    }

    @Test
    @DisplayName("formatException(Exception) should contain EXCEP level, exception message, and stack trace")
    void formatExceptionShouldContainExcepLevelAndStackTrace() {
        RuntimeException ex = new RuntimeException("something went wrong");
        String result = formatter.formatException(ex, contextInfo);
        assertTrue(result.contains("EXCEP"), "Output should contain 'EXCEP' level");
        assertTrue(result.contains("something went wrong"), "Output should contain the exception message");
        assertTrue(result.contains("RuntimeException"), "Output should contain the exception class name");
        assertTrue(result.contains("at "), "Output should contain at least one stack frame");
    }

    @Test
    @DisplayName("formatException(String, Exception) should contain the prefix and stack trace")
    void formatExceptionWithPrefixShouldContainPrefixAndStackTrace() {
        RuntimeException ex = new RuntimeException("root cause");
        String result = formatter.formatException("context info", ex, contextInfo);
        assertTrue(result.contains("EXCEP"), "Output should contain 'EXCEP' level");
        assertTrue(result.contains("context info"), "Output should contain the prefix");
        assertTrue(result.contains("root cause"), "Output should contain the exception message");
        assertTrue(result.contains("at "), "Output should contain at least one stack frame");
    }

    @Test
    @DisplayName("format() should append context entries as {key=value} suffix when context is non-empty")
    void formatShouldAppendContextWhenNonEmpty() {
        contextInfo.add("userId", 42);
        String result = formatter.format(LogLevelEnum.INFO, "msg", contextInfo);
        assertTrue(result.contains("{userId=42}"), "Output should contain context entries");
    }

    @Test
    @DisplayName("format() should append multiple context entries separated by ', '")
    void formatShouldAppendMultipleContextEntries() {
        contextInfo.add("a", 1);
        contextInfo.add("b", 2);
        String result = formatter.format(LogLevelEnum.INFO, "msg", contextInfo);
        assertTrue(result.contains("{"), "Output should contain opening brace for context");
        assertTrue(result.contains("a=1"), "Output should contain first entry");
        assertTrue(result.contains("b=2"), "Output should contain second entry");
        assertTrue(result.contains(", "), "Entries should be separated by ', '");
    }

    @Test
    @DisplayName("formatException(Exception) should append context entries when context is non-empty")
    void formatExceptionShouldAppendContextWhenNonEmpty() {
        contextInfo.add("reqId", "abc");
        RuntimeException ex = new RuntimeException("oops");
        String result = formatter.formatException(ex, contextInfo);
        assertTrue(result.contains("{reqId=abc}"), "Output should contain context entries");
    }

    @Test
    @DisplayName("formatException(String, Exception) should append context entries when context is non-empty")
    void formatExceptionWithPrefixShouldAppendContextWhenNonEmpty() {
        contextInfo.add("reqId", "abc");
        RuntimeException ex = new RuntimeException("oops");
        String result = formatter.formatException("pfx", ex, contextInfo);
        assertTrue(result.contains("{reqId=abc}"), "Output should contain context entries");
    }

    // ---- CLASS_NAME promotion ----

    @Test
    @DisplayName("format() should insert className between timestamp and level when CLASS_NAME is present")
    void formatShouldInsertClassNameBetweenTimestampAndLevel() {
        contextInfo.add(ContextInfo.CLASS_NAME, "com.example.Foo");
        String result = formatter.format(LogLevelEnum.INFO, "msg", contextInfo);
        assertTrue(result.contains("[com.example.Foo]"), "Output should contain the class name in brackets");
        int classNameIdx = result.indexOf("[com.example.Foo]");
        int levelIdx = result.indexOf("[INFO");
        assertTrue(classNameIdx < levelIdx, "className should appear before the level label");
        assertFalse(result.contains("{"), "CLASS_NAME alone should not produce a context suffix");
    }

    @Test
    @DisplayName("format() should not produce a context suffix when CLASS_NAME is the only context entry")
    void formatShouldNotProduceContextSuffixWhenOnlyClassNamePresent() {
        contextInfo.add(ContextInfo.CLASS_NAME, "com.example.Foo");
        String result = formatter.format(LogLevelEnum.INFO, "msg", contextInfo);
        assertFalse(result.contains("{"), "CLASS_NAME alone should not produce a context suffix");
    }

    @Test
    @DisplayName("format() should exclude CLASS_NAME from context suffix when other entries are present")
    void formatShouldExcludeClassNameFromContextSuffix() {
        contextInfo.add(ContextInfo.CLASS_NAME, "com.example.Foo");
        contextInfo.add("userId", "42");
        String result = formatter.format(LogLevelEnum.INFO, "msg", contextInfo);
        assertTrue(result.contains("[com.example.Foo]"), "className should appear at its dedicated position");
        assertTrue(result.contains("{userId=42}"), "Other context entries should still appear in suffix");
        assertFalse(result.contains("className="), "CLASS_NAME should not appear in the context suffix");
    }

    @Test
    @DisplayName("formatException(Exception) should insert className between timestamp and level")
    void formatExceptionShouldInsertClassNameBetweenTimestampAndLevel() {
        contextInfo.add(ContextInfo.CLASS_NAME, "com.example.Bar");
        RuntimeException ex = new RuntimeException("boom");
        String result = formatter.formatException(ex, contextInfo);
        assertTrue(result.contains("[com.example.Bar]"), "Output should contain the class name in brackets");
        assertFalse(result.contains("{"), "CLASS_NAME alone should not produce a context suffix");
    }

    @Test
    @DisplayName("formatException(String, Exception) should insert className between timestamp and level")
    void formatExceptionWithPrefixShouldInsertClassNameBetweenTimestampAndLevel() {
        contextInfo.add(ContextInfo.CLASS_NAME, "com.example.Bar");
        RuntimeException ex = new RuntimeException("boom");
        String result = formatter.formatException("pfx", ex, contextInfo);
        assertTrue(result.contains("[com.example.Bar]"), "Output should contain the class name in brackets");
        assertFalse(result.contains("{"), "CLASS_NAME alone should not produce a context suffix");
    }

    // ---- abbreviate() ----

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "com.example.Foo,          c.e.Foo",
        "com.example.service.Bar,  c.e.s.Bar",
        "Foo,                      Foo",
        "a.Foo,                    a.Foo",
        "com.threeamigos.common.util.implementations.messagehandler.PlainTextLogFormatter, c.t.c.u.i.m.PlainTextLogFormatter"
    })
    @DisplayName("abbreviate() should shorten each package segment to its first letter")
    void abbreviateShouldShortenPackageSegments(String input, String expected) {
        assertEquals(expected.trim(), PlainTextLogFormatter.abbreviate(input.trim()));
    }

    @Test
    @DisplayName("isAbbreviateClassName() should return false by default")
    void isAbbreviateClassNameShouldReturnFalseByDefault() {
        assertFalse(formatter.isAbbreviateClassName());
    }

    @Test
    @DisplayName("format() should use abbreviated class name when abbreviation is enabled")
    void formatShouldUseAbbreviatedClassNameWhenEnabled() {
        formatter.setAbbreviateClassName(true);
        contextInfo.add(ContextInfo.CLASS_NAME, "com.example.Foo");
        String result = formatter.format(LogLevelEnum.INFO, "msg", contextInfo);
        assertTrue(result.contains("[c.e.Foo]"), "Output should contain abbreviated class name");
        assertFalse(result.contains("com.example.Foo"), "Full class name should not appear when abbreviated");
    }

    @Test
    @DisplayName("format() should use full class name when abbreviation is disabled")
    void formatShouldUseFullClassNameWhenNotAbbreviated() {
        contextInfo.add(ContextInfo.CLASS_NAME, "com.example.Foo");
        String result = formatter.format(LogLevelEnum.INFO, "msg", contextInfo);
        assertTrue(result.contains("[com.example.Foo]"), "Full class name should appear when abbreviation is off");
    }
}
