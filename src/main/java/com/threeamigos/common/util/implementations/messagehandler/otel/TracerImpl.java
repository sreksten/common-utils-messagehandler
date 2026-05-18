package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.AbstractMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.GrafanaMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.JaegerMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.JULMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.Log4JMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.SLF4JMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.SwingMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.VoidMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Filter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanDispatcher;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SpanContext;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;
import com.threeamigos.common.util.implementations.messagehandler.tracecontext.TraceContextGenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * An immutable implementation of a {@link Tracer}.
 * <p>
 * Package-private on purpose: callers should obtain tracers only via {@link TracerProvider},
 * e.g. {@link TracerProvider#getTracer(String, String)} and related provider factory methods.
 *
 * @author Stefano Reksten
 */
class TracerImpl implements Tracer {

    private final TracerProvider owner;
    private final InstrumentationScope instrumentationScope;
    private final boolean enabled;
    private final Filter filter;

    TracerImpl(final String instrumentationName,
               final String version,
               final String schemaUrl,
               final Collection<KeyValue> attributes) {
        this(null, instrumentationName, version, schemaUrl, attributes, true, null);
    }

    TracerImpl(final String instrumentationName,
               final String version,
               final String schemaUrl,
               final Collection<KeyValue> attributes,
               final boolean enabled) {
        this(null, instrumentationName, version, schemaUrl, attributes, enabled, null);
    }

    TracerImpl(final TracerProvider owner,
               final String instrumentationName,
               final String version,
               final String schemaUrl,
               final Collection<KeyValue> attributes) {
        this(owner, instrumentationName, version, schemaUrl, attributes, true, null);
    }

    TracerImpl(final TracerProvider owner,
               final String instrumentationName,
               final String version,
               final String schemaUrl,
               final Collection<KeyValue> attributes,
               final boolean enabled,
               final Filter filter) {
        String resolvedInstrumentationName = instrumentationName;
        if (resolvedInstrumentationName == null || resolvedInstrumentationName.trim().isEmpty()) {
            OpenTelemetryAttributeValidator.reportBundled("nullInstrumentationNameProvided");
            resolvedInstrumentationName = "";
        }
        String instrumentationName1 = resolvedInstrumentationName;
        Collection<KeyValue> attributes1 = attributes == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(attributes));
        this.instrumentationScope = InstrumentationScopeFactory.create(
                instrumentationName1,
                version,
                schemaUrl,
                new ArrayList<>(attributes1));
        this.owner = owner;
        this.enabled = enabled;
        this.filter = filter;
    }

    @Override
    public Span createSpan(final String name) {
        return createSpan(name, null);
    }

    @Override
    public Span createSpan(final String name, final SpanContext parentSpanContext) {
        String normalizedName = OpenTelemetryAttributeValidator.requireNonBlank(name, "spanName");
        if (!enabled) {
            return Span.wrap(null);
        }
        SpanContext spanContext;
        String parentSpanId = null;
        if (parentSpanContext == null || !parentSpanContext.isValid()) {
            spanContext = new SpanContextImpl(
                    TraceContextGenerator.generateTraceId(),
                    TraceContextGenerator.generateParentId(),
                    (byte) 0x00,
                    false,
                    new TraceStateImpl());
        } else {
            parentSpanId = parentSpanContext.getSpanId();
            spanContext = new SpanContextImpl(
                    parentSpanContext.getTraceId(),
                    TraceContextGenerator.generateParentId(),
                    parentSpanContext.getTraceFlags(),
                    false,
                    parentSpanContext.getTraceState());
        }
        AutoCloseable scopeToken = attachCorrelationScope(spanContext);
        SpanDispatcher spanDispatcher = owner == null ? null : owner.getDefaultSpanDispatcher();
        Span span = new SpanImpl(
                normalizedName,
                spanContext,
                instrumentationScope,
                java.time.Instant.now(),
                true,
                scopeToken,
                parentSpanId,
                spanDispatcher);
        if (owner != null) {
            owner.getCorrelationResolver().setActiveSpan(span);
        }
        return span;
    }

    @Override
    public InstrumentationScope getInstrumentationScope() {
        return instrumentationScope;
    }

    @Override
    public LogRecordFactory getLogRecordFactory() {
        if (owner == null) {
            return new LogRecordFactoryImpl();
        }
        return owner.getLogRecordFactory(instrumentationScope);
    }

    @Override
    public ConsoleMessageHandler getConsoleMessageHandler() {
        return getConsoleMessageHandler(null);
    }

    @Override
    public ConsoleMessageHandler getConsoleMessageHandler(final Filter filter) {
        return createAndFilterSimpleHandler(() -> createFactory(null).createConsole(), filter, null);
    }

    @Override
    public FileMessageHandler getFileMessageHandler(final String filePath) {
        return getFileMessageHandler(filePath, null);
    }

    @Override
    public FileMessageHandler getFileMessageHandler(final String filePath, final Filter filter) {
        String resolvedPath = filePath;
        if (resolvedPath == null || resolvedPath.trim().isEmpty()) {
            resolvedPath = owner == null ? "message-handler.log" : owner.getDefaultFilePath();
        }
        final String filePathForFactory = resolvedPath;
        return createAndFilterSimpleHandler(
                () -> createFactory(filePathForFactory).createFile(filePathForFactory),
                filter,
                null);
    }

    @Override
    public FileMessageHandler getFileMessageHandler(final File file) {
        return getFileMessageHandler(file, null);
    }

    @Override
    public FileMessageHandler getFileMessageHandler(final File file, final Filter filter) {
        String path = file == null ? null : file.getPath();
        return getFileMessageHandler(path, filter);
    }

    @Override
    public InMemoryMessageHandler getInMemoryMessageHandler() {
        return getInMemoryMessageHandler(null);
    }

    @Override
    public InMemoryMessageHandler getInMemoryMessageHandler(final Filter filter) {
        return createAndFilterSimpleHandler(() -> createFactory(null).createInMemory(), filter, null);
    }

    @Override
    public JULMessageHandler getJULMessageHandler(final java.util.logging.Logger logger) {
        return getJULMessageHandler(logger, null);
    }

    @Override
    public JULMessageHandler getJULMessageHandler(final java.util.logging.Logger logger, final Filter filter) {
        TracerMessageHandlerFactory factory = createFactory(null);
        JULMessageHandler handler = factory.createJUL(logger);
        applyFilterLevels(handler, null, resolveEffectiveFilter(filter));
        return handler;
    }

    @Override
    public Log4JMessageHandler getLog4JMessageHandler(final org.apache.logging.log4j.Logger logger) {
        return getLog4JMessageHandler(logger, null);
    }

    @Override
    public Log4JMessageHandler getLog4JMessageHandler(final org.apache.logging.log4j.Logger logger, final Filter filter) {
        TracerMessageHandlerFactory factory = createFactory(null);
        Log4JMessageHandler handler = factory.createLog4J(logger);
        applyFilterLevels(handler, null, resolveEffectiveFilter(filter));
        return handler;
    }

    @Override
    public SLF4JMessageHandler getSLF4JMessageHandler(final org.slf4j.Logger logger) {
        return getSLF4JMessageHandler(logger, null);
    }

    @Override
    public SLF4JMessageHandler getSLF4JMessageHandler(final org.slf4j.Logger logger, final Filter filter) {
        TracerMessageHandlerFactory factory = createFactory(null);
        SLF4JMessageHandler handler = factory.createSLF4J(logger);
        applyFilterLevels(handler, null, resolveEffectiveFilter(filter));
        return handler;
    }

    @Override
    public SwingMessageHandler getSwingMessageHandler() {
        return getSwingMessageHandler(null);
    }

    @Override
    public SwingMessageHandler getSwingMessageHandler(final Filter filter) {
        return createAndFilterSimpleHandler(() -> createFactory(null).createSwing(), filter, null);
    }

    @Override
    public JaegerMessageHandler getJaegerMessageHandler(final String endpointUrl) {
        return getJaegerMessageHandler(endpointUrl, (Filter) null);
    }

    @Override
    public JaegerMessageHandler getJaegerMessageHandler(final String endpointUrl, final Filter filter) {
        return createAndFilterSimpleHandler(() -> createFactory(null).createJaeger(endpointUrl), filter, null);
    }

    @Override
    public JaegerMessageHandler getJaegerMessageHandler(final String endpointUrl,
                                                        final String username,
                                                        final String password) {
        return getJaegerMessageHandler(endpointUrl, username, password, null, 10_000, 10_000, null,
                false, 0, false, null);
    }

    @Override
    public JaegerMessageHandler getJaegerMessageHandler(final String endpointUrl,
                                                        final String username,
                                                        final String password,
                                                        final String bearerToken,
                                                        final int connectTimeoutMillis,
                                                        final int readTimeoutMillis,
                                                        final Map<String, String> additionalHeaders,
                                                        final boolean async,
                                                        final int queueCapacity,
                                                        final boolean registerShutdownHook) {
        return getJaegerMessageHandler(endpointUrl, username, password, bearerToken,
                connectTimeoutMillis, readTimeoutMillis, additionalHeaders, async, queueCapacity,
                registerShutdownHook, null);
    }

    @Override
    public JaegerMessageHandler getJaegerMessageHandler(final String endpointUrl,
                                                        final String username,
                                                        final String password,
                                                        final String bearerToken,
                                                        final int connectTimeoutMillis,
                                                        final int readTimeoutMillis,
                                                        final Map<String, String> additionalHeaders,
                                                        final boolean async,
                                                        final int queueCapacity,
                                                        final boolean registerShutdownHook,
                                                        final Filter filter) {
        return createAndFilterSimpleHandler(
                () -> createFactory(null).createJaeger(
                        endpointUrl,
                        username,
                        password,
                        bearerToken,
                        connectTimeoutMillis,
                        readTimeoutMillis,
                        additionalHeaders,
                        async,
                        queueCapacity,
                        registerShutdownHook),
                filter,
                null);
    }

    @Override
    public GrafanaMessageHandler getGrafanaMessageHandler(final String endpointUrl) {
        return getGrafanaMessageHandler(endpointUrl, (Filter) null);
    }

    @Override
    public GrafanaMessageHandler getGrafanaMessageHandler(final String endpointUrl, final Filter filter) {
        return createAndFilterSimpleHandler(() -> createFactory(null).createGrafana(endpointUrl), filter, null);
    }

    @Override
    public GrafanaMessageHandler getGrafanaMessageHandler(final String endpointUrl,
                                                          final String username,
                                                          final String password) {
        return getGrafanaMessageHandler(endpointUrl, username, password, null, 10_000, 10_000, null,
                false, 0, false, null);
    }

    @Override
    public GrafanaMessageHandler getGrafanaMessageHandler(final String endpointUrl,
                                                          final String username,
                                                          final String password,
                                                          final String bearerToken,
                                                          final int connectTimeoutMillis,
                                                          final int readTimeoutMillis,
                                                          final Map<String, String> additionalHeaders,
                                                          final boolean async,
                                                          final int queueCapacity,
                                                          final boolean registerShutdownHook) {
        return getGrafanaMessageHandler(endpointUrl, username, password, bearerToken,
                connectTimeoutMillis, readTimeoutMillis, additionalHeaders, async, queueCapacity,
                registerShutdownHook, null);
    }

    @Override
    public GrafanaMessageHandler getGrafanaMessageHandler(final String endpointUrl,
                                                          final String username,
                                                          final String password,
                                                          final String bearerToken,
                                                          final int connectTimeoutMillis,
                                                          final int readTimeoutMillis,
                                                          final Map<String, String> additionalHeaders,
                                                          final boolean async,
                                                          final int queueCapacity,
                                                          final boolean registerShutdownHook,
                                                          final Filter filter) {
        return createAndFilterSimpleHandler(
                () -> createFactory(null).createGrafana(
                        endpointUrl,
                        username,
                        password,
                        bearerToken,
                        connectTimeoutMillis,
                        readTimeoutMillis,
                        additionalHeaders,
                        async,
                        queueCapacity,
                        registerShutdownHook),
                filter,
                null);
    }

    @Override
    public VoidMessageHandler getVoidMessageHandler() {
        return createAndFilterSimpleHandler(() -> createFactory(null).createVoid(), null, null);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    private void applyFilterLevels(final MessageHandler handler,
                                   final Class<?> sourceClass,
                                   final Filter effectiveFilter) {
        if (!(handler instanceof AbstractMessageHandler) || effectiveFilter == null) {
            return;
        }
        AbstractMessageHandler abstractHandler = (AbstractMessageHandler) handler;
        for (SeverityNumber severityNumber : SeverityNumber.values()) {
            if (severityNumber == SeverityNumber.UNSPECIFIED) {
                continue;
            }
            LogRecord probe = createFilterProbe(severityNumber);
            if (sourceClass != null && probe instanceof LogRecordImpl) {
                List<KeyValue> attrs = Collections.singletonList(
                        KeyValueFactory.of("code.namespace", AnyValueFactory.ofString(sourceClass.getName())));
                ((LogRecordImpl) probe).setAttributes(attrs);
            }
            boolean enabledForClass = effectiveFilter.filter(probe) != null;
            abstractHandler.setEnabled(severityNumber, enabledForClass);
        }
    }

    LogRecord createFilterProbe(final SeverityNumber severityNumber) {
        return new LogRecordFactoryImpl().create(severityNumber, "");
    }

    private Filter resolveEffectiveFilter(final Filter methodFilter) {
        return methodFilter == null ? filter : methodFilter;
    }

    private <T extends MessageHandler> T createAndFilterSimpleHandler(final Supplier<T> handlerSupplier,
                                                                      final Filter methodFilter,
                                                                      final Class<?> sourceClass) {
        T handler = handlerSupplier.get();
        applyFilterLevels(handler, sourceClass, resolveEffectiveFilter(methodFilter));
        return handler;
    }

    private TracerMessageHandlerFactory createFactory(final String filePath) {
        if (owner == null) {
            return new TracerMessageHandlerFactory(new LogRecordFactoryImpl(), "message-handler.log");
        }
        return owner.buildMessageHandlerFactory(instrumentationScope, filePath);
    }

    private AutoCloseable attachCorrelationScope(final SpanContext spanContext) {
        if (owner == null) {
            return null;
        }
        CorrelationResolver resolver = owner.getCorrelationResolver();
        return resolver.attach(spanContext, instrumentationScope);
    }
}
