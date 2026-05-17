package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@DisplayName("LogRecordFactoryImpl enrichment unit tests")
@Tag("unit")
@Tag("messageHandler")
class LogRecordFactoryImplEnrichmentUnitTest {

    @org.junit.jupiter.api.AfterEach
    void cleanupLenientAndTrap() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(false);
        OpenTelemetryAttributeValidator.setLogTrapForTests(null);
    }

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

        LogRecordFactoryImpl sut =
                new LogRecordFactoryImpl(delegate, null, null, null);

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

        LogRecordFactoryImpl sut = new LogRecordFactoryImpl(
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

        LogRecordFactoryImpl sut =
                new LogRecordFactoryImpl(delegate, null, null, null);

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

        LogRecordFactoryImpl sut = new LogRecordFactoryImpl(
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

        LogRecordFactoryImpl sut = new LogRecordFactoryImpl(
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

        LogRecordFactoryImpl sut = new LogRecordFactoryImpl(
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
    @DisplayName("create should append message as event to active span when correlation matches")
    void createShouldAppendMessageAsEventToActiveSpanWhenCorrelationMatches() {
        LogRecordFactory delegate = mock(LogRecordFactory.class);
        Span activeSpan = mock(Span.class);
        SpanContext resolverSpan = new SpanContextImpl(
                "4b8efff798038103d269b633813fc60c",
                "ddd19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());
        LogRecordImpl record = new LogRecordImpl();
        record.setBody(AnyValueFactory.ofString("hello event"));
        record.setSeverityText("INFO");
        when(delegate.create()).thenReturn(record);
        when(activeSpan.isRecording()).thenReturn(true);
        when(activeSpan.getSpanContext()).thenReturn(resolverSpan);

        LogRecordFactoryImpl sut = new LogRecordFactoryImpl(
                delegate,
                null,
                null,
                Collections.<KeyValue>emptyList(),
                null,
                () -> resolverSpan,
                null,
                () -> activeSpan);

        sut.create();

        assertEquals(resolverSpan.getTraceId(), record.getTraceId());
        assertEquals(resolverSpan.getSpanId(), record.getSpanId());
        verify(activeSpan).addEvent(eq("log"), anyList(), any());
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

        LogRecordFactoryImpl sut = new LogRecordFactoryImpl(
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

    @Test
    @DisplayName("enrichment should set only missing span correlation fields")
    void enrichmentShouldSetOnlyMissingSpanCorrelationFields() {
        LogRecordFactory delegate = mock(LogRecordFactory.class);
        LogRecordImpl record = new LogRecordImpl();
        record.setTraceId("7b8efff798038103d269b633813fc60c");
        record.setSpanId(" ");
        record.setTraceFlags(0);
        SpanContext explicit = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());
        when(delegate.create()).thenReturn(record);

        LogRecordFactoryImpl sut = new LogRecordFactoryImpl(
                delegate, null, null, Collections.<KeyValue>emptyList(), explicit);

        LogRecord enriched = sut.create();
        assertSame(record, enriched);
        assertEquals("7b8efff798038103d269b633813fc60c", record.getTraceId());
        assertEquals(explicit.getSpanId(), record.getSpanId());
        assertEquals(0x01, record.getTraceFlags());
    }

    @Test
    @DisplayName("enrichRecord should throw in strict mode when record is null")
    void enrichRecordShouldThrowInStrictModeWhenRecordIsNull() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(false);
        LogRecordFactoryImpl sut = new LogRecordFactoryImpl(
                mock(LogRecordFactory.class),
                null,
                null,
                Collections.emptyList());

        assertThrows(IllegalArgumentException.class, () -> sut.enrichRecord(null));
    }

    @Test
    @DisplayName("enrichRecord should return empty record in lenient mode when record is null")
    void enrichRecordShouldReturnEmptyRecordInLenientModeWhenRecordIsNull() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        OpenTelemetryAttributeValidator.setLogTrapForTests((message, throwable) -> {
            // no-op trap for lenient assertions
        });
        LogRecordFactoryImpl sut = new LogRecordFactoryImpl(
                mock(LogRecordFactory.class),
                null,
                null,
                Collections.emptyList());

        LogRecord enriched = assertDoesNotThrow(() -> sut.enrichRecord(null));
        assertNotNull(enriched);
        assertTrue(enriched instanceof LogRecordImpl);
    }

    @Test
    @DisplayName("resolver failures should be swallowed and keep record usable")
    void resolverFailuresShouldBeSwallowedAndKeepRecordUsable() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        OpenTelemetryAttributeValidator.setLogTrapForTests((message, throwable) -> {
            // no-op trap for resolver failure paths
        });
        LogRecordFactory delegate = mock(LogRecordFactory.class);
        LogRecordImpl record = new LogRecordImpl();
        when(delegate.create()).thenReturn(record);

        LogRecordFactoryImpl sut = new LogRecordFactoryImpl(
                delegate,
                null,
                null,
                Collections.emptyList(),
                null,
                () -> {
                    throw new RuntimeException("span resolver failure");
                },
                () -> {
                    throw new RuntimeException("scope resolver failure");
                });

        LogRecord enriched = assertDoesNotThrow(() -> sut.create());
        assertSame(record, enriched);
        assertNull(record.getInstrumentationScope());
        assertNull(record.getTraceId());
        assertNull(record.getSpanId());
    }

    @Test
    @DisplayName("explicit invalid span context should fall back to resolver context")
    void explicitInvalidSpanContextShouldFallBackToResolverContext() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        OpenTelemetryAttributeValidator.setLogTrapForTests((message, throwable) -> {
            // no-op trap for invalid explicit span path
        });
        LogRecordFactory delegate = mock(LogRecordFactory.class);
        LogRecordImpl record = new LogRecordImpl();
        SpanContext resolverSpan = new SpanContextImpl(
                "4b8efff798038103d269b633813fc60c",
                "ddd19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());
        when(delegate.create()).thenReturn(record);

        SpanContext invalidExplicit = new SpanContext() {
            @Override
            public String getTraceId() {
                return null;
            }

            @Override
            public byte[] getTraceIdBytes() {
                return new byte[0];
            }

            @Override
            public String getSpanId() {
                return null;
            }

            @Override
            public byte[] getSpanIdBytes() {
                return new byte[0];
            }

            @Override
            public byte getTraceFlags() {
                return 0;
            }

            @Override
            public boolean isSampled() {
                return false;
            }

            @Override
            public boolean isRandom() {
                return false;
            }

            @Override
            public boolean isValid() {
                throw new RuntimeException("invalid explicit context");
            }

            @Override
            public boolean isRemote() {
                return false;
            }

            @Override
            public com.threeamigos.common.util.interfaces.messagehandler.otel.TraceState getTraceState() {
                return null;
            }
        };

        LogRecordFactoryImpl sut = new LogRecordFactoryImpl(
                delegate,
                null,
                null,
                Collections.emptyList(),
                invalidExplicit,
                () -> resolverSpan,
                null);

        LogRecord enriched = sut.create();
        assertSame(record, enriched);
        assertEquals(resolverSpan.getTraceId(), record.getTraceId());
        assertEquals(resolverSpan.getSpanId(), record.getSpanId());
    }

    @Test
    @DisplayName("resource/scope constructors should enrich records with defaults and explicit span context")
    void resourceScopeConstructorsShouldEnrichRecordsWithDefaultsAndExplicitSpanContext() {
        Resource resource = ResourceFactory.create("https://resource.schema", null, null);
        InstrumentationScope scope = InstrumentationScopeFactory.create(
                "orders",
                "1.0.0",
                "https://scope.schema",
                Collections.<KeyValue>emptyList());
        KeyValue common = KeyValueFactory.of("env", AnyValueFactory.ofString("test"));
        SpanContext explicit = new SpanContextImpl(
                "5b8efff798038103d269b633813fc60c",
                "eee19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());

        LogRecordFactoryImpl withResourceAndScope = new LogRecordFactoryImpl(
                resource,
                scope,
                Collections.singletonList(common));
        LogRecord recordA = withResourceAndScope.create();
        assertSame(resource, recordA.getResource());
        assertSame(scope, recordA.getInstrumentationScope());
        assertEquals(1, recordA.getAttributes().size());

        LogRecordFactoryImpl withExplicitContext = new LogRecordFactoryImpl(
                resource,
                scope,
                Collections.singletonList(common),
                explicit);
        LogRecord recordB = withExplicitContext.create();
        assertEquals(explicit.getTraceId(), recordB.getTraceId());
        assertEquals(explicit.getSpanId(), recordB.getSpanId());
    }

    @Test
    @DisplayName("enrichRecord should enrich mutable records and preserve non-mutable records as-is")
    void enrichRecordShouldEnrichMutableRecordsAndPreserveNonMutableRecordsAsIs() {
        Resource resource = ResourceFactory.create("https://resource.schema", null, null);
        InstrumentationScope scope = InstrumentationScopeFactory.create(
                "orders",
                "1.0.0",
                "https://scope.schema",
                Collections.<KeyValue>emptyList());
        LogRecordFactoryImpl sut = new LogRecordFactoryImpl(
                resource,
                scope,
                Collections.<KeyValue>emptyList());

        LogRecordImpl mutable = new LogRecordImpl();
        LogRecord enriched = sut.enrichRecord(mutable);
        assertSame(mutable, enriched);
        assertSame(resource, mutable.getResource());
        assertSame(scope, mutable.getInstrumentationScope());

        LogRecord nonMutable = mock(LogRecord.class);
        assertSame(nonMutable, sut.enrichRecord(nonMutable));
    }

    @Test
    @DisplayName("active span event bridging should skip invalid contexts mismatches and blank bodies")
    void activeSpanEventBridgingShouldSkipInvalidContextsMismatchesAndBlankBodies() {
        LogRecordFactory delegate = mock(LogRecordFactory.class);
        Span activeSpan = mock(Span.class);
        SpanContext activeContext = new SpanContextImpl(
                "4b8efff798038103d269b633813fc60c",
                "ddd19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());
        when(activeSpan.isRecording()).thenReturn(true);
        when(activeSpan.getSpanContext()).thenReturn(activeContext);

        LogRecordImpl traceMismatch = new LogRecordImpl();
        traceMismatch.setTraceId("7b8efff798038103d269b633813fc60c");
        traceMismatch.setSpanId(activeContext.getSpanId());
        traceMismatch.setBody(AnyValueFactory.ofString("hello"));
        when(delegate.create()).thenReturn(traceMismatch);

        LogRecordFactoryImpl sut = new LogRecordFactoryImpl(
                delegate,
                null,
                null,
                Collections.<KeyValue>emptyList(),
                null,
                () -> activeContext,
                null,
                () -> activeSpan);
        sut.create();
        verify(activeSpan, never()).addEvent(eq("log"), anyList(), any());

        LogRecordImpl spanMismatch = new LogRecordImpl();
        spanMismatch.setTraceId(activeContext.getTraceId());
        spanMismatch.setSpanId("aaa19b7ec3c1b174");
        spanMismatch.setBody(AnyValueFactory.ofString("hello"));
        when(delegate.create()).thenReturn(spanMismatch);
        sut.create();
        verify(activeSpan, never()).addEvent(eq("log"), anyList(), any());

        LogRecordImpl blankBody = new LogRecordImpl();
        blankBody.setTraceId(activeContext.getTraceId());
        blankBody.setSpanId(activeContext.getSpanId());
        blankBody.setBody(AnyValueFactory.ofString("   "));
        when(delegate.create()).thenReturn(blankBody);
        sut.create();
        verify(activeSpan, never()).addEvent(eq("log"), anyList(), any());

        SpanContext invalid = new SpanContextImpl();
        when(activeSpan.getSpanContext()).thenReturn(invalid);
        LogRecordImpl matching = new LogRecordImpl();
        matching.setBody(AnyValueFactory.ofString("hello"));
        when(delegate.create()).thenReturn(matching);
        sut.create();
        verify(activeSpan, never()).addEvent(eq("log"), anyList(), any());
    }

    @Test
    @DisplayName("active span resolver failures should not break log record creation")
    void activeSpanResolverFailuresShouldNotBreakLogRecordCreation() {
        OpenTelemetryAttributeValidator.setLenientModeForTests(true);
        OpenTelemetryAttributeValidator.setLogTrapForTests((message, throwable) -> {
            // no-op for resolver failure branch
        });

        LogRecordFactory delegate = mock(LogRecordFactory.class);
        LogRecordImpl record = new LogRecordImpl();
        record.setBody(AnyValueFactory.ofString("msg"));
        when(delegate.create()).thenReturn(record);

        LogRecordFactoryImpl sut = new LogRecordFactoryImpl(
                delegate,
                null,
                null,
                Collections.<KeyValue>emptyList(),
                null,
                null,
                null,
                () -> {
                    throw new RuntimeException("active span resolver failed");
                });

        LogRecord enriched = assertDoesNotThrow(new org.junit.jupiter.api.function.ThrowingSupplier<LogRecord>() {
            @Override
            public LogRecord get() {
                return sut.create();
            }
        });
        assertSame(record, enriched);
    }

    @Test
    @DisplayName("attribute merge should ignore null and blank keys and avoid unnecessary writes")
    void attributeMergeShouldIgnoreNullAndBlankKeysAndAvoidUnnecessaryWrites() throws Exception {
        LogRecordImpl record = new LogRecordImpl();
        List<KeyValue> rawAttributes = Arrays.<KeyValue>asList(
                null,
                new KeyValue() {
                    @Override
                    public String getKey() {
                        return null;
                    }

                    @Override
                    public com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue getValue() {
                        return AnyValueFactory.ofString("x");
                    }
                },
                KeyValueFactory.of("existing", AnyValueFactory.ofString("present")));
        Field attributesField = LogRecordImpl.class.getDeclaredField("attributes");
        attributesField.setAccessible(true);
        attributesField.set(record, rawAttributes);

        LogRecordFactoryImpl sut = new LogRecordFactoryImpl(
                null,
                null,
                null,
                Arrays.<KeyValue>asList(
                        null,
                        new KeyValue() {
                            @Override
                            public String getKey() {
                                return " ";
                            }

                            @Override
                            public com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue getValue() {
                                return AnyValueFactory.ofString("ignored");
                            }
                        },
                        KeyValueFactory.of("existing", AnyValueFactory.ofString("override-attempt"))));

        LogRecord enriched = sut.enrichRecord(record);
        assertSame(record, enriched);
        assertEquals(3, record.getAttributes().size());
    }

    @Test
    @DisplayName("active span event bridge should handle null non-recording and null-context spans")
    void activeSpanEventBridgeShouldHandleNullNonRecordingAndNullContextSpans() {
        LogRecordFactory delegate = mock(LogRecordFactory.class);
        LogRecordImpl record = new LogRecordImpl();
        record.setBody(AnyValueFactory.ofString("hello"));
        when(delegate.create()).thenReturn(record);

        LogRecordFactoryImpl withNullActiveSpan = new LogRecordFactoryImpl(
                delegate, null, null, Collections.<KeyValue>emptyList(), null, null, null, () -> null);
        assertSame(record, withNullActiveSpan.create());

        Span nonRecording = mock(Span.class);
        when(nonRecording.isRecording()).thenReturn(false);
        LogRecordFactoryImpl withNonRecordingActiveSpan = new LogRecordFactoryImpl(
                delegate, null, null, Collections.<KeyValue>emptyList(), null, null, null, () -> nonRecording);
        assertSame(record, withNonRecordingActiveSpan.create());
        verify(nonRecording, never()).addEvent(eq("log"), anyList(), any());

        Span nullContextSpan = mock(Span.class);
        when(nullContextSpan.isRecording()).thenReturn(true);
        when(nullContextSpan.getSpanContext()).thenReturn(null);
        LogRecordFactoryImpl withNullContextActiveSpan = new LogRecordFactoryImpl(
                delegate, null, null, Collections.<KeyValue>emptyList(), null, null, null, () -> nullContextSpan);
        assertSame(record, withNullContextActiveSpan.create());
        verify(nullContextSpan, never()).addEvent(eq("log"), anyList(), any());
    }

    @Test
    @DisplayName("active span event bridge should skip null body and omit blank severity attribute")
    void activeSpanEventBridgeShouldSkipNullBodyAndOmitBlankSeverityAttribute() {
        LogRecordFactory delegate = mock(LogRecordFactory.class);
        Span activeSpan = mock(Span.class);
        SpanContext context = new SpanContextImpl(
                "4b8efff798038103d269b633813fc60c",
                "ddd19b7ec3c1b174",
                (byte) 0x01,
                false,
                new TraceStateImpl());
        when(activeSpan.isRecording()).thenReturn(true);
        when(activeSpan.getSpanContext()).thenReturn(context);

        LogRecordImpl nullBody = new LogRecordImpl();
        nullBody.setTraceId(context.getTraceId());
        nullBody.setSpanId(context.getSpanId());
        when(delegate.create()).thenReturn(nullBody);

        LogRecordFactoryImpl sut = new LogRecordFactoryImpl(
                delegate, null, null, Collections.<KeyValue>emptyList(), null, null, null, () -> activeSpan);
        sut.create();
        verify(activeSpan, never()).addEvent(eq("log"), anyList(), any());

        LogRecordImpl blankSeverity = new LogRecordImpl();
        blankSeverity.setTraceId(context.getTraceId());
        blankSeverity.setSpanId(context.getSpanId());
        blankSeverity.setBody(AnyValueFactory.ofString("hello"));
        blankSeverity.setSeverityText("   ");
        when(delegate.create()).thenReturn(blankSeverity);
        sut.create();

        ArgumentCaptor<List<KeyValue>> attributesCaptor = ArgumentCaptor.forClass(List.class);
        verify(activeSpan).addEvent(eq("log"), attributesCaptor.capture(), any());
        assertEquals(1, attributesCaptor.getValue().size());
        assertEquals("log.message", attributesCaptor.getValue().get(0).getKey());
    }

    @Test
    @DisplayName("trace enrichment and matching helpers should cover remaining correlation branches")
    void traceEnrichmentAndMatchingHelpersShouldCoverRemainingCorrelationBranches() throws Exception {
        SpanContext context = new SpanContextImpl(
                "4b8efff798038103d269b633813fc60c",
                "ddd19b7ec3c1b174",
                (byte) 0x03,
                false,
                new TraceStateImpl());

        Method enrichTraceCorrelation = LogRecordFactoryImpl.class.getDeclaredMethod(
                "enrichTraceCorrelation",
                LogRecordImpl.class,
                SpanContext.class);
        enrichTraceCorrelation.setAccessible(true);

        LogRecordImpl complete = new LogRecordImpl();
        complete.setTraceId(context.getTraceId());
        complete.setSpanId(context.getSpanId());
        complete.setTraceFlags(0);
        enrichTraceCorrelation.invoke(null, complete, context);
        assertEquals(0, complete.getTraceFlags());

        LogRecordImpl missingSpan = new LogRecordImpl();
        missingSpan.setTraceId(context.getTraceId());
        missingSpan.setTraceFlags(0);
        enrichTraceCorrelation.invoke(null, missingSpan, context);
        assertEquals(context.getSpanId(), missingSpan.getSpanId());
        assertEquals(0x03, missingSpan.getTraceFlags());

        LogRecordImpl missingSpanWithNonZeroFlags = new LogRecordImpl();
        missingSpanWithNonZeroFlags.setTraceId(context.getTraceId());
        missingSpanWithNonZeroFlags.setTraceFlags(7);
        enrichTraceCorrelation.invoke(null, missingSpanWithNonZeroFlags, context);
        assertEquals(7, missingSpanWithNonZeroFlags.getTraceFlags());

        Method matchesActiveSpan = LogRecordFactoryImpl.class.getDeclaredMethod(
                "matchesActiveSpan",
                LogRecordImpl.class,
                SpanContext.class);
        matchesActiveSpan.setAccessible(true);

        LogRecordImpl traceMismatch = new LogRecordImpl();
        traceMismatch.setTraceId("7b8efff798038103d269b633813fc60c");
        traceMismatch.setSpanId(context.getSpanId());
        assertEquals(Boolean.FALSE, matchesActiveSpan.invoke(null, traceMismatch, context));

        LogRecordImpl spanMismatch = new LogRecordImpl();
        spanMismatch.setTraceId(context.getTraceId());
        spanMismatch.setSpanId("aaa19b7ec3c1b174");
        assertEquals(Boolean.FALSE, matchesActiveSpan.invoke(null, spanMismatch, context));

        LogRecordImpl blankCorrelation = new LogRecordImpl();
        assertEquals(Boolean.TRUE, matchesActiveSpan.invoke(null, blankCorrelation, context));

        LogRecordImpl match = new LogRecordImpl();
        match.setTraceId(context.getTraceId());
        match.setSpanId(context.getSpanId());
        assertEquals(Boolean.TRUE, matchesActiveSpan.invoke(null, match, context));
    }
}
