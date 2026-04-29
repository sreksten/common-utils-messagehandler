package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@DisplayName("EnrichingLogRecordFactory unit tests")
@Tag("unit")
@Tag("messageHandler")
class EnrichingLogRecordFactoryUnitTest {

    @Test
    @DisplayName("create methods should return delegate record unchanged when delegate does not return LogRecordImpl")
    void createMethodsShouldReturnDelegateRecordUnchangedWhenDelegateDoesNotReturnLogRecordImpl() {
        LogRecordFactory delegate = mock(LogRecordFactory.class);
        LogRecord genericRecord = mock(LogRecord.class);
        RuntimeException throwable = new RuntimeException("boom");

        when(delegate.create()).thenReturn(genericRecord);
        when(delegate.create(SeverityNumber.INFO, "msg")).thenReturn(genericRecord);
        when(delegate.create("msg", throwable)).thenReturn(genericRecord);
        when(delegate.create(throwable)).thenReturn(genericRecord);

        EnrichingLogRecordFactory sut =
                new EnrichingLogRecordFactory(delegate, null, null, null);

        assertSame(genericRecord, sut.create());
        assertSame(genericRecord, sut.create(SeverityNumber.INFO, "msg"));
        assertSame(genericRecord, sut.create("msg", throwable));
        assertSame(genericRecord, sut.create(throwable));

        verify(delegate).create();
        verify(delegate).create(SeverityNumber.INFO, "msg");
        verify(delegate).create("msg", throwable);
        verify(delegate).create(throwable);
        verifyNoMoreInteractions(delegate);
    }

    @Test
    @DisplayName("create should enrich mutable records with resource scope and merged attributes")
    void createShouldEnrichMutableRecordsWithResourceScopeAndMergedAttributes() {
        LogRecordFactory delegate = mock(LogRecordFactory.class);
        Resource resource = mock(Resource.class);
        InstrumentationScope scope = mock(InstrumentationScope.class);

        KeyValue existing = KeyValueFactory.of("existing", AnyValueFactory.ofString("present"));
        KeyValue common = KeyValueFactory.of("common", AnyValueFactory.ofString("added"));

        LogRecordImpl record = new LogRecordImpl();
        record.setAttributes(Collections.singletonList(existing));

        when(delegate.create()).thenReturn(record);

        EnrichingLogRecordFactory sut = new EnrichingLogRecordFactory(
                delegate,
                resource,
                scope,
                Collections.singletonList(common));

        LogRecord enriched = sut.create();
        List<String> keys = record.getAttributes().stream().map(KeyValue::getKey).collect(Collectors.toList());

        assertSame(record, enriched);
        assertSame(resource, record.getResource());
        assertSame(scope, record.getInstrumentationScope());
        assertEquals(Arrays.asList("existing", "common"), keys);
    }

    @Test
    @DisplayName("create should skip resource scope and common attributes when enrichment inputs are null")
    void createShouldSkipResourceScopeAndCommonAttributesWhenEnrichmentInputsAreNull() {
        LogRecordFactory delegate = mock(LogRecordFactory.class);

        KeyValue original = KeyValueFactory.of("original", AnyValueFactory.ofString("v"));
        LogRecordImpl record = new LogRecordImpl();
        record.setAttributes(Collections.singletonList(original));

        when(delegate.create()).thenReturn(record);

        EnrichingLogRecordFactory sut =
                new EnrichingLogRecordFactory(delegate, null, null, null);

        LogRecord enriched = sut.create();

        assertSame(record, enriched);
        assertNull(record.getResource());
        assertNull(record.getInstrumentationScope());
        assertEquals(1, record.getAttributes().size());
        assertEquals("original", record.getAttributes().get(0).getKey());
    }

    @Test
    @DisplayName("create should not merge attributes when common attributes list is empty")
    void createShouldNotMergeAttributesWhenCommonAttributesListIsEmpty() {
        LogRecordFactory delegate = mock(LogRecordFactory.class);

        KeyValue original = KeyValueFactory.of("original", AnyValueFactory.ofString("v"));
        LogRecordImpl record = new LogRecordImpl();
        record.setAttributes(Collections.singletonList(original));

        when(delegate.create()).thenReturn(record);

        EnrichingLogRecordFactory sut = new EnrichingLogRecordFactory(
                delegate,
                null,
                null,
                Collections.emptyList());

        LogRecord enriched = sut.create();

        assertSame(record, enriched);
        assertEquals(1, record.getAttributes().size());
        assertEquals("original", record.getAttributes().get(0).getKey());
    }
}
