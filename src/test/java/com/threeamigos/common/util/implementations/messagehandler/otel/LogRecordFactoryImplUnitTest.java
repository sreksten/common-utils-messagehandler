package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LogRecordFactoryImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class LogRecordFactoryImplUnitTest {

    private final LogRecordFactoryImpl factory = new LogRecordFactoryImpl();

    @Test
    @DisplayName("create() should return an empty LogRecord instance")
    void createShouldReturnEmptyLogRecord() {
        LogRecord record = factory.create();
        assertNotNull(record);
        assertEquals(SeverityNumber.UNSPECIFIED, record.getSeverityNumber());
    }

    @Test
    @DisplayName("create(SeverityNumber, String) should set severity and body")
    void createSeverityMessageShouldSetSeverityAndBody() {
        LogRecord record = factory.create(SeverityNumber.WARN, "warn message");
        assertEquals(SeverityNumber.WARN, record.getSeverityNumber());
        assertEquals(SeverityNumber.WARN.name(), record.getSeverityText());
        assertEquals("warn message", record.getBody().asString());
    }

    @Test
    @DisplayName("create(SeverityNumber, String) should reject null inputs")
    void createSeverityMessageShouldRejectNullInputs() {
        assertThrows(IllegalArgumentException.class, () -> factory.create(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> factory.create(SeverityNumber.INFO, null));
    }

    @Test
    @DisplayName("create(Throwable) should populate ERROR severity and exception attributes")
    void createThrowableShouldPopulateErrorSeverityAndExceptionAttributes() {
        RuntimeException throwable = new RuntimeException("boom");

        LogRecord record = factory.create(throwable);
        Map<String, AnyValue> attrs = record.getAttributes().stream()
                .collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue));

        assertEquals(SeverityNumber.ERROR, record.getSeverityNumber());
        assertEquals(SeverityNumber.ERROR.name(), record.getSeverityText());
        assertEquals("boom", record.getBody().asString());
        assertEquals("exception", record.getEventName());

        assertEquals(RuntimeException.class.getName(), attrs.get("exception.type").asString());
        assertEquals("boom", attrs.get("exception.message").asString());
        assertTrue(attrs.get("exception.stacktrace").asString().contains("RuntimeException: boom"));
    }

    @Test
    @DisplayName("create(String, Throwable) should keep message as body and include exception attributes")
    void createMessageThrowableShouldKeepMessageAsBodyAndIncludeExceptionAttributes() {
        IllegalArgumentException throwable = new IllegalArgumentException("invalid value");

        LogRecord record = factory.create("validation failed", throwable);
        Map<String, AnyValue> attrs = record.getAttributes().stream()
                .collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue));

        assertEquals(SeverityNumber.ERROR, record.getSeverityNumber());
        assertEquals("validation failed", record.getBody().asString());
        assertEquals("exception", record.getEventName());
        assertEquals(IllegalArgumentException.class.getName(), attrs.get("exception.type").asString());
        assertEquals("invalid value", attrs.get("exception.message").asString());
        assertTrue(attrs.get("exception.stacktrace").asString().contains("IllegalArgumentException"));
    }

    @Test
    @DisplayName("create(Throwable) should fallback to throwable.toString() when message is null")
    void createThrowableShouldFallbackToToStringWhenMessageIsNull() {
        Throwable throwable = new Throwable();

        LogRecord record = factory.create(throwable);
        Map<String, AnyValue> attrs = record.getAttributes().stream()
                .collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue));

        assertEquals(throwable.toString(), record.getBody().asString());
        assertNotNull(attrs.get("exception.stacktrace"));
        assertEquals(Throwable.class.getName(), attrs.get("exception.type").asString());
    }

    @Test
    @DisplayName("throwable-based factories should reject null throwable")
    void throwableFactoriesShouldRejectNullThrowable() {
        assertThrows(IllegalArgumentException.class, () -> factory.create((Throwable) null));
        assertThrows(IllegalArgumentException.class, () -> factory.create("prefix", null));
    }
}
