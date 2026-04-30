package com.threeamigos.common.util.interfaces.messagehandler.otel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Telemetry API default methods unit tests")
@Tag("unit")
@Tag("messageHandler")
class TelemetryApiDefaultMethodsUnitTest {

    @Test
    @DisplayName("Logger default isEnabled overloads should delegate to the three-argument signature")
    void loggerDefaultIsEnabledOverloadsShouldDelegate() {
        ProbeLogger logger = new ProbeLogger();
        logger.returnValue = true;

        assertTrue(logger.isEnabled());
        assertTrue(logger.isEnabled(SeverityNumber.INFO));
        assertTrue(logger.isEnabled(SeverityNumber.ERROR, "event"));

        assertEquals(3, logger.calls);
        assertNull(logger.capturedContext);
        assertEquals(SeverityNumber.ERROR, logger.capturedSeverityNumber);
        assertEquals("event", logger.capturedEventName);
    }

    @Test
    @DisplayName("LoggerProvider default overloads should delegate to the varargs signature")
    void loggerProviderDefaultOverloadsShouldDelegate() {
        ProbeLoggerProvider provider = new ProbeLoggerProvider();

        Logger loggerFromName = provider.getLogger("orders");
        assertSame(provider.returnedLogger, loggerFromName);
        assertEquals("orders", provider.capturedName);
        assertNull(provider.capturedVersion);
        assertNull(provider.capturedSchemaUrl);
        assertEquals(0, provider.capturedAttributes.length);

        Logger loggerFromNameAndVersion = provider.getLogger("orders", "1.0.0");
        assertSame(provider.returnedLogger, loggerFromNameAndVersion);
        assertEquals("orders", provider.capturedName);
        assertEquals("1.0.0", provider.capturedVersion);
        assertNull(provider.capturedSchemaUrl);
        assertEquals(0, provider.capturedAttributes.length);

        Logger loggerFromThreeArgs = provider.getLogger("orders", "1.0.0", "https://schema");
        assertSame(provider.returnedLogger, loggerFromThreeArgs);
        assertEquals("orders", provider.capturedName);
        assertEquals("1.0.0", provider.capturedVersion);
        assertEquals("https://schema", provider.capturedSchemaUrl);
        assertEquals(0, provider.capturedAttributes.length);
    }

    @Test
    @DisplayName("MetricsProvider default overloads should delegate to the varargs signature")
    void metricsProviderDefaultOverloadsShouldDelegate() {
        ProbeMetricsProvider provider = new ProbeMetricsProvider();

        Meter meterFromName = provider.getMeter("checkout");
        assertSame(provider.returnedMeter, meterFromName);
        assertEquals("checkout", provider.capturedName);
        assertNull(provider.capturedVersion);
        assertNull(provider.capturedSchemaUrl);
        assertEquals(0, provider.capturedAttributes.length);

        Meter meterFromNameAndVersion = provider.getMeter("checkout", "2.0.0");
        assertSame(provider.returnedMeter, meterFromNameAndVersion);
        assertEquals("checkout", provider.capturedName);
        assertEquals("2.0.0", provider.capturedVersion);
        assertNull(provider.capturedSchemaUrl);
        assertEquals(0, provider.capturedAttributes.length);

        Meter meterFromThreeArgs = provider.getMeter("checkout", "2.0.0", "https://schema");
        assertSame(provider.returnedMeter, meterFromThreeArgs);
        assertEquals("checkout", provider.capturedName);
        assertEquals("2.0.0", provider.capturedVersion);
        assertEquals("https://schema", provider.capturedSchemaUrl);
        assertEquals(0, provider.capturedAttributes.length);
    }

    @Test
    @DisplayName("Meter and Tracer default instrumentation scope methods should return null")
    void meterAndTracerDefaultInstrumentationScopeMethodsShouldReturnNull() {
        Meter meter = new Meter() {
        };

        Tracer tracer = new Tracer() {
            @Override
            public Span createSpan(final String name) {
                return Span.wrap(null);
            }

            @Override
            public Span createSpan(final String name, final SpanContext parentSpanContext) {
                return Span.wrap(parentSpanContext);
            }

            @Override
            public boolean isEnabled() {
                return true;
            }
        };

        assertNull(meter.getInstrumentationScope());
        assertNull(tracer.getInstrumentationScope());

        Span created = tracer.createSpan("sample");
        assertTrue(created != null);
    }

    private static final class ProbeLogger implements Logger {
        private int calls;
        private Context capturedContext;
        private SeverityNumber capturedSeverityNumber;
        private String capturedEventName;
        private boolean returnValue;

        @Override
        public void emit(final LogRecord logRecord) {
            // No-op for this delegation test.
        }

        @Override
        public boolean isEnabled(final Context context,
                                 final SeverityNumber severityNumber,
                                 final String eventName) {
            calls++;
            capturedContext = context;
            capturedSeverityNumber = severityNumber;
            capturedEventName = eventName;
            return returnValue;
        }
    }

    private static final class ProbeLoggerProvider implements LoggerProvider {
        private final Logger returnedLogger = new Logger() {
            @Override
            public void emit(final LogRecord logRecord) {
                // No-op.
            }

            @Override
            public boolean isEnabled(final Context context,
                                     final SeverityNumber severityNumber,
                                     final String eventName) {
                return true;
            }
        };

        private String capturedName;
        private String capturedVersion;
        private String capturedSchemaUrl;
        private KeyValue[] capturedAttributes;

        @Override
        public Logger getLogger(final String name,
                                final String version,
                                final String schemaUrl,
                                final KeyValue... attributes) {
            capturedName = name;
            capturedVersion = version;
            capturedSchemaUrl = schemaUrl;
            capturedAttributes = attributes;
            return returnedLogger;
        }
    }

    private static final class ProbeMetricsProvider implements MetricsProvider {
        private final Meter returnedMeter = new Meter() {
            @Override
            public InstrumentationScope getInstrumentationScope() {
                return null;
            }
        };

        private String capturedName;
        private String capturedVersion;
        private String capturedSchemaUrl;
        private KeyValue[] capturedAttributes;

        @Override
        public Meter getMeter(final String name,
                              final String version,
                              final String schemaUrl,
                              final KeyValue... attributes) {
            capturedName = name;
            capturedVersion = version;
            capturedSchemaUrl = schemaUrl;
            capturedAttributes = attributes;
            return returnedMeter;
        }
    }
}
