package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LogRecordImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class LogRecordImplUnitTest {

    @Test
    @DisplayName("defaults should match the data model defaults")
    void defaultsShouldMatchDataModelDefaults() {
        LogRecordImpl record = new LogRecordImpl();
        assertNotNull(record.getTimestamp());
        assertNull(record.getObservedTimestamp());
        assertNull(record.getTraceId());
        assertNull(record.getSpanId());
        assertEquals(0, record.getTraceFlags());
        assertNull(record.getSeverityText());
        assertSame(SeverityNumber.UNSPECIFIED, record.getSeverityNumber());
        assertNull(record.getBody());
        assertNull(record.getResource());
        assertNull(record.getInstrumentationScope());
        assertTrue(record.getAttributes().isEmpty());
        assertEquals(0, record.getDroppedAttributesCount());
        assertNull(record.getEventName());
    }

    @Test
    @DisplayName("timestamp setters should validate null and store explicit value")
    void timestampSettersShouldValidateAndStoreValues() {
        LogRecordImpl record = new LogRecordImpl();
        assertThrows(IllegalArgumentException.class, () -> record.setTimestamp(null));

        Instant ts = Instant.parse("2026-04-19T22:00:00Z");
        Instant observed = Instant.parse("2026-04-19T22:00:01Z");
        record.setTimestamp(ts);
        record.setObservedTimestamp(observed);

        assertEquals(ts, record.getTimestamp());
        assertEquals(observed, record.getObservedTimestamp());
    }

    @Test
    @DisplayName("traceId should accept valid value and reject invalid/all-zero values")
    void traceIdValidationShouldFollowW3cShape() {
        LogRecordImpl record = new LogRecordImpl();
        String valid = "5b8efff798038103d269b633813fc60c";
        record.setTraceId(valid);
        assertEquals(valid, record.getTraceId());

        assertThrows(IllegalArgumentException.class, () -> record.setTraceId("abc"));
        assertThrows(IllegalArgumentException.class, () -> record.setTraceId("5B8EFFF798038103D269B633813FC60C"));
        assertThrows(IllegalArgumentException.class, () -> record.setTraceId("00000000000000000000000000000000"));

        record.setTraceId(null);
        assertNull(record.getTraceId());
    }

    @Test
    @DisplayName("spanId should accept valid value and reject invalid/all-zero values")
    void spanIdValidationShouldFollowW3cShape() {
        LogRecordImpl record = new LogRecordImpl();
        String valid = "eee19b7ec3c1b174";
        record.setSpanId(valid);
        assertEquals(valid, record.getSpanId());

        assertThrows(IllegalArgumentException.class, () -> record.setSpanId("abc"));
        assertThrows(IllegalArgumentException.class, () -> record.setSpanId("EEE19B7EC3C1B174"));
        assertThrows(IllegalArgumentException.class, () -> record.setSpanId("0000000000000000"));

        record.setSpanId(null);
        assertNull(record.getSpanId());
    }

    @Test
    @DisplayName("traceFlags should allow 0..255 and reject out-of-range values")
    void traceFlagsValidationShouldEnforceByteRange() {
        LogRecordImpl record = new LogRecordImpl();
        record.setTraceFlags(0);
        assertEquals(0, record.getTraceFlags());
        record.setTraceFlags(255);
        assertEquals(255, record.getTraceFlags());

        assertThrows(IllegalArgumentException.class, () -> record.setTraceFlags(-1));
        assertThrows(IllegalArgumentException.class, () -> record.setTraceFlags(256));
    }

    @Test
    @DisplayName("severity setters should keep number and text independent")
    void severitySettersShouldKeepNumberAndTextIndependent() {
        LogRecordImpl record = new LogRecordImpl();
        record.setSeverityText("NOTICE");
        assertEquals("NOTICE", record.getSeverityText());

        record.setSeverityNumber(SeverityNumber.INFO2);
        assertSame(SeverityNumber.INFO2, record.getSeverityNumber());
        assertEquals("NOTICE", record.getSeverityText());

        record.setSeverityNumber(null);
        assertSame(SeverityNumber.UNSPECIFIED, record.getSeverityNumber());
        assertEquals("NOTICE", record.getSeverityText());
    }

    @Test
    @DisplayName("body/resource/scope/eventName setters should store values")
    void simpleSettersShouldStoreValues() {
        LogRecordImpl record = new LogRecordImpl();
        AnyValue body = AnyValueImpl.ofString("body");
        Resource resource = ResourceFactory.create(null, null);
        InstrumentationScope scope = InstrumentationScopeFactory.create(null, null, null, null);

        record.setBody(body);
        record.setResource(resource);
        record.setInstrumentationScope(scope);
        record.setEventName("evt");

        assertSame(body, record.getBody());
        assertSame(resource, record.getResource());
        assertSame(scope, record.getInstrumentationScope());
        assertEquals("evt", record.getEventName());
    }

    @Test
    @DisplayName("setAttributes() should copy and expose unmodifiable list")
    void setAttributesShouldCopyAndExposeUnmodifiableList() {
        LogRecordImpl record = new LogRecordImpl();
        List<KeyValue> attrs = new ArrayList<>(Collections.singletonList(
                new KeyValueImpl("k1", AnyValueImpl.ofString("v1"))
        ));

        record.setAttributes(attrs);
        assertEquals(1, record.getAttributes().size());
        assertThrows(UnsupportedOperationException.class,
                () -> record.getAttributes().add(new KeyValueImpl("k2", AnyValueImpl.ofString("v2"))));

        attrs.add(new KeyValueImpl("k2", AnyValueImpl.ofString("v2")));
        assertEquals(1, record.getAttributes().size());
    }

    @Test
    @DisplayName("setAttributes() should accept null as empty list")
    void setAttributesShouldAcceptNullAsEmptyList() {
        LogRecordImpl record = new LogRecordImpl();
        record.setAttributes(null);
        assertTrue(record.getAttributes().isEmpty());
    }

    @Test
    @DisplayName("setAttributes() should reject duplicate keys and invalid entries")
    void setAttributesShouldRejectDuplicateKeysAndInvalidEntries() {
        LogRecordImpl record = new LogRecordImpl();

        List<KeyValue> duplicates = Arrays.asList(
                new KeyValueImpl("dup", AnyValueImpl.ofString("v1")),
                new KeyValueImpl("dup", AnyValueImpl.ofString("v2"))
        );
        assertThrows(IllegalArgumentException.class, () -> record.setAttributes(duplicates));

        KeyValue invalid = new KeyValue() {
            @Override
            public String getKey() {
                return "k";
            }

            @Override
            public AnyValue getValue() {
                return null;
            }
        };
        assertThrows(NullPointerException.class, () -> record.setAttributes(Collections.singletonList(invalid)));
        assertThrows(NullPointerException.class, () -> record.setAttributes(Collections.singletonList(null)));
    }

    @Test
    @DisplayName("setAttributes() should drop entries above the default attribute count limit")
    void setAttributesShouldDropEntriesAboveDefaultLimit() {
        LogRecordImpl record = new LogRecordImpl();
        List<KeyValue> attributes = new ArrayList<>();
        for (int i = 0; i < 129; i++) {
            attributes.add(new KeyValueImpl("k" + i, AnyValueImpl.ofString("v" + i)));
        }

        record.setAttributes(attributes);

        assertEquals(128, record.getAttributes().size());
        assertEquals(1, record.getDroppedAttributesCount());
        assertEquals("k127", record.getAttributes().get(127).getKey());
    }
}
