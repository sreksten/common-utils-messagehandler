package com.threeamigos.common.util.implementations.messagehandler.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("JsonStringPrettifier unit tests")
@Tag("unit")
@Tag("messageHandler")
class JsonStringPrettifierUnitTest {

    @Test
    @DisplayName("constructor should reject zero or negative indentation")
    void constructorShouldRejectNonPositiveIndentation() {
        assertThrows(IllegalArgumentException.class, () -> new JsonStringPrettifier(0));
        assertThrows(IllegalArgumentException.class, () -> new JsonStringPrettifier(-1));
    }

    @Test
    @DisplayName("prettify should reject null json")
    void prettifyShouldRejectNullJson() {
        JsonStringPrettifier sut = new JsonStringPrettifier();
        assertThrows(NullPointerException.class, () -> sut.prettify(null));
    }

    @Test
    @DisplayName("prettify should reject blank json")
    void prettifyShouldRejectBlankJson() {
        JsonStringPrettifier sut = new JsonStringPrettifier();
        assertThrows(IllegalArgumentException.class, () -> sut.prettify("   \n\t  "));
    }

    @Test
    @DisplayName("prettify should format nested json with spaces only")
    void prettifyShouldFormatNestedJsonWithSpacesOnly() {
        JsonStringPrettifier sut = new JsonStringPrettifier();
        String input = "{\"a\":1,\"b\":[true,false,null],\"c\":{\"d\":\"x\",\"e\":[{},[]]}}";

        String result = sut.prettify(input);

        String expected = String.join("\n",
                "{",
                "  \"a\": 1,",
                "  \"b\": [",
                "    true,",
                "    false,",
                "    null",
                "  ],",
                "  \"c\": {",
                "    \"d\": \"x\",",
                "    \"e\": [",
                "      {},",
                "      []",
                "    ]",
                "  }",
                "}");
        assertEquals(expected, result);
        assertFalse(result.contains("\t"));
    }

    @Test
    @DisplayName("prettify should preserve escaped text inside strings")
    void prettifyShouldPreserveEscapedTextInsideStrings() {
        JsonStringPrettifier sut = new JsonStringPrettifier();
        String input = "{\"msg\":\"line1\\\\n\\\\t\\\\\\\"quoted\\\\\\\"\",\"path\":\"C:\\\\\\\\temp\\\\\\\\file.json\"}";

        String result = sut.prettify(input);

        String expected = String.join("\n",
                "{",
                "  \"msg\": \"line1\\\\n\\\\t\\\\\\\"quoted\\\\\\\"\",",
                "  \"path\": \"C:\\\\\\\\temp\\\\\\\\file.json\"",
                "}");
        assertEquals(expected, result);
        assertFalse(result.contains("\t"));
    }

    @Test
    @DisplayName("prettify should support custom space indentation")
    void prettifyShouldSupportCustomSpaceIndentation() {
        JsonStringPrettifier sut = new JsonStringPrettifier(4);
        String input = "{\"outer\":{\"inner\":1}}";

        String result = sut.prettify(input);

        String expected = String.join("\n",
                "{",
                "    \"outer\": {",
                "        \"inner\": 1",
                "    }",
                "}");
        assertEquals(expected, result);
        assertFalse(result.contains("\t"));
    }

    @Test
    @DisplayName("prettify should reject malformed json delimiters")
    void prettifyShouldRejectMalformedJsonDelimiters() {
        JsonStringPrettifier sut = new JsonStringPrettifier();
        assertThrows(IllegalArgumentException.class, () -> sut.prettify("]"));
        assertThrows(IllegalArgumentException.class, () -> sut.prettify("{]"));
        assertThrows(IllegalArgumentException.class, () -> sut.prettify("{\"a\":[1,2}"));
        assertThrows(IllegalArgumentException.class, () -> sut.prettify("{\"a\":1"));
    }

    @Test
    @DisplayName("prettify should reject invalid separators and unterminated strings")
    void prettifyShouldRejectInvalidSeparatorsAndUnterminatedStrings() {
        JsonStringPrettifier sut = new JsonStringPrettifier();
        assertThrows(IllegalArgumentException.class, () -> sut.prettify("1,2"));
        assertThrows(IllegalArgumentException.class, () -> sut.prettify("[1:2]"));
        assertThrows(IllegalArgumentException.class, () -> sut.prettify(":"));
        assertThrows(IllegalArgumentException.class, () -> sut.prettify("{\"a\":\"x"));
        assertThrows(IllegalArgumentException.class, () -> sut.prettify("{\"a\":\"x\\\\"));
    }
}
