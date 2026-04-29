package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    @DisplayName("create should enrich trace correlation from explicit span context before resolver")
    void createShouldEnrichTraceCorrelationFromExplicitSpanContextBeforeResolver() {
        LogRecordFactory delegate = mock(LogRecordFactory.class);
        LogRecordImpl record = new LogRecordImpl();
        SpanContext explicit = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x03,
                false,
                new TraceStateImpl());
        AtomicInteger resolverCalls = new AtomicInteger();

        when(delegate.create()).thenReturn(record);

        EnrichingLogRecordFactory sut = new EnrichingLogRecordFactory(
                delegate,
                null,
                null,
                Collections.emptyList(),
                explicit,
                () -> {
                    resolverCalls.incrementAndGet();
                    return new SpanContextImpl(
                            "4b8efff798038103d269b633813fc60c",
                            "ddd19b7ec3c1b174",
                            (byte) 0x01,
                            false,
                            new TraceStateImpl());
                },
                null);

        LogRecord enriched = sut.create();

        assertSame(record, enriched);
        assertEquals(explicit.getTraceId(), record.getTraceId());
        assertEquals(explicit.getSpanId(), record.getSpanId());
        assertEquals(0x03, record.getTraceFlags());
        assertEquals(0, resolverCalls.get());
    }

    @Test
    @DisplayName("create should resolve scope and trace correlation from resolver when direct values are absent")
    void createShouldResolveScopeAndTraceCorrelationFromResolverWhenDirectValuesAbsent() {
        LogRecordFactory delegate = mock(LogRecordFactory.class);
        LogRecordImpl record = new LogRecordImpl();
        SpanContext resolverSpan = new SpanContextImpl(
                "4b8efff798038103d269b633813fc60c",
                "ddd19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());
        InstrumentationScope resolverScope = InstrumentationScopeFactory.create(
                "resolver-scope",
                "1.0.0",
                "https://resolver.schema",
                Collections.emptyList());

        when(delegate.create()).thenReturn(record);

        EnrichingLogRecordFactory sut = new EnrichingLogRecordFactory(
                delegate,
                null,
                null,
                Collections.emptyList(),
                new SpanContextImpl(),
                () -> resolverSpan,
                () -> resolverScope);

        LogRecord enriched = sut.create();

        assertSame(record, enriched);
        assertEquals(resolverSpan.getTraceId(), record.getTraceId());
        assertEquals(resolverSpan.getSpanId(), record.getSpanId());
        assertEquals(0x01, record.getTraceFlags());
        assertNotNull(record.getInstrumentationScope());
        assertEquals("resolver-scope", record.getInstrumentationScope().getName());
    }

    @Test
    @DisplayName("create should preserve caller provided values and only fill missing attributes")
    void createShouldPreserveCallerProvidedValuesAndOnlyFillMissingAttributes() {
        LogRecordFactory delegate = mock(LogRecordFactory.class);
        Resource originalResource = ResourceFactory.create(null, null, null);
        Resource providerResource = ResourceFactory.create("https://provider.schema", null, null);
        InstrumentationScope originalScope = InstrumentationScopeFactory.create(
                "caller-scope",
                "1.0.0",
                "https://caller.schema",
                Collections.emptyList());
        InstrumentationScope providerScope = InstrumentationScopeFactory.create(
                "provider-scope",
                "1.0.0",
                "https://provider.schema",
                Collections.emptyList());
        AtomicInteger scopeResolverCalls = new AtomicInteger();

        LogRecordImpl record = new LogRecordImpl();
        record.setResource(originalResource);
        record.setInstrumentationScope(originalScope);
        record.setTraceId("7b8efff798038103d269b633813fc60c");
        record.setSpanId("aaa19b7ec3c1b174");
        record.setTraceFlags(0x00);
        record.setAttributes(Collections.singletonList(
                KeyValueFactory.of("existing", AnyValueFactory.ofString("present"))));

        when(delegate.create()).thenReturn(record);

        EnrichingLogRecordFactory sut = new EnrichingLogRecordFactory(
                delegate,
                providerResource,
                providerScope,
                Arrays.asList(
                        KeyValueFactory.of("existing", AnyValueFactory.ofString("override-attempt")),
                        KeyValueFactory.of("new", AnyValueFactory.ofString("added"))),
                new SpanContextImpl(
                        "5b8efff798038103d269b633813fc60c",
                        "eee19b7ec3c1b174",
                        (byte) 0x01,
                        false,
                        new TraceStateImpl()),
                null,
                () -> {
                    scopeResolverCalls.incrementAndGet();
                    return InstrumentationScopeFactory.create("resolver", null, null, Collections.emptyList());
                });

        LogRecord enriched = sut.create();

        assertSame(record, enriched);
        assertSame(originalResource, record.getResource());
        assertSame(originalScope, record.getInstrumentationScope());
        assertEquals("7b8efff798038103d269b633813fc60c", record.getTraceId());
        assertEquals("aaa19b7ec3c1b174", record.getSpanId());
        assertEquals(0x00, record.getTraceFlags());
        assertEquals(2, record.getAttributes().size());
        List<String> keys = record.getAttributes().stream().map(KeyValue::getKey).collect(Collectors.toList());
        assertEquals(Arrays.asList("existing", "new"), keys);
        assertEquals("present", record.getAttributes().get(0).getValue().asString());
        assertEquals(0, scopeResolverCalls.get());
    }
}
