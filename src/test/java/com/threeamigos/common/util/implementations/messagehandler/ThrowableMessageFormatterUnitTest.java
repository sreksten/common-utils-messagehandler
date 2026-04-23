package com.threeamigos.common.util.implementations.messagehandler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("ThrowableMessageFormatter unit tests")
@Tag("unit")
@Tag("messageHandler")
class ThrowableMessageFormatterUnitTest {

    @Test
    @DisplayName("detail should return throwable message when present")
    void detailShouldReturnThrowableMessageWhenPresent() {
        RuntimeException exception = new RuntimeException("boom");
        assertEquals("boom", ThrowableMessageFormatter.detail(exception));
    }

    @Test
    @DisplayName("detail should fallback to toString when message is null")
    void detailShouldFallbackToToStringWhenMessageIsNull() {
        RuntimeException exception = new RuntimeException((String) null);
        assertEquals(exception.toString(), ThrowableMessageFormatter.detail(exception));
    }

    @Test
    @DisplayName("withPrefix should fallback to detail for null, empty and same-detail message")
    void withPrefixShouldFallbackToDetailForNullEmptyAndSameDetailMessage() {
        RuntimeException exception = new RuntimeException("boom");

        assertEquals("boom", ThrowableMessageFormatter.withPrefix(null, exception));
        assertEquals("boom", ThrowableMessageFormatter.withPrefix("", exception));
        assertEquals("boom", ThrowableMessageFormatter.withPrefix("boom", exception));
    }

    @Test
    @DisplayName("withPrefix should prepend custom message when different from detail")
    void withPrefixShouldPrependCustomMessageWhenDifferentFromDetail() {
        RuntimeException exception = new RuntimeException("boom");
        assertEquals("prefix: boom", ThrowableMessageFormatter.withPrefix("prefix", exception));
    }
}
