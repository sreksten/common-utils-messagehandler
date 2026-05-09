package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ExportLogsServiceRequestLogRecordFormatter;
import com.threeamigos.common.util.implementations.messagehandler.utils.GrafanaLogRecordDispatcher;
import com.threeamigos.common.util.implementations.messagehandler.utils.GrafanaSpanDispatcher;
import com.threeamigos.common.util.implementations.messagehandler.utils.JaegerSpanDispatcher;
import com.threeamigos.common.util.implementations.messagehandler.utils.OTelCollectorDispatcher;
import com.threeamigos.common.util.implementations.messagehandler.utils.OTelCollectorSpanDispatcher;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Span;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("OTel collector end-to-end integration tests")
@Tag("integration")
@Tag("messageHandler")
class OTelCollectorEndToEndIntegrationTest {

    @Test
    @DisplayName("should emit logs and spans through collector fan-out and retrieve from Loki and Jaeger")
    void shouldEmitLogsAndSpansThroughCollectorFanOutAndRetrieveFromLokiAndJaeger() throws Exception {
        String jaegerTracesEndpoint = readEnvOrDefault("JAEGER_OTLP_TRACES_ENDPOINT", "http://localhost:4318/v1/traces");
        String tempoTracesEndpoint = readEnvOrDefault("TEMPO_OTLP_TRACES_ENDPOINT", "http://localhost:14318/v1/traces");
        String jaegerQueryBase = readEnvOrDefault("JAEGER_QUERY_TRACE_ENDPOINT", "http://localhost:16686");
        String lokiPushEndpoint = readEnvOrDefault("GRAFANA_LOKI_PUSH_ENDPOINT", "http://localhost:3100/loki/api/v1/push");
        String lokiQueryEndpoint = readEnvOrDefault("GRAFANA_LOKI_QUERY_ENDPOINT", "http://localhost:3100/loki/api/v1/query_range");

        assumeEndpointReachable(jaegerTracesEndpoint, "Jaeger traces endpoint");
        assumeEndpointReachable(tempoTracesEndpoint, "Tempo traces endpoint");
        assumeEndpointReachable(jaegerQueryBase, "Jaeger query endpoint");
        assumeEndpointReachable(lokiPushEndpoint, "Loki push endpoint");
        assumeEndpointReachable(lokiQueryEndpoint, "Loki query endpoint");

        String serviceName = "MyTestService";
        String serviceVersion = "1.0-alpha";
        String spanName = "otel-collector-e2e-span";
        String flowToken = "search-flow-" + System.currentTimeMillis();
        String firstLogMessage = "user started a search [" + flowToken + "]";
        String secondLogMessage = "user displays search results [" + flowToken + "]";
        String thirdLogMessage = "search completed [" + flowToken + "]";
        List<String> expectedMessages = Arrays.asList(firstLogMessage, secondLogMessage, thirdLogMessage);

        OTelCollectorDispatcher logCollector = new OTelCollectorDispatcher();
        logCollector.addDispatcher(new GrafanaLogRecordDispatcher(lokiPushEndpoint));

        OTelCollectorSpanDispatcher spanCollector = new OTelCollectorSpanDispatcher();
        spanCollector.addDispatcher(new JaegerSpanDispatcher(jaegerTracesEndpoint));
        spanCollector.addDispatcher(new GrafanaSpanDispatcher(tempoTracesEndpoint));

        TracerProvider provider = TracerProvider.builder()
                .serviceName(serviceName)
                .serviceVersion(serviceVersion)
                .build();
        provider.setDefaultSpanDispatcher(spanCollector);
        Tracer tracer = provider.getTracer(serviceName, serviceVersion);

        LogRecordFactory logRecordFactory = tracer.getLogRecordFactory();
        GrafanaMessageHandler handler = new GrafanaMessageHandler(
                logRecordFactory,
                new ExportLogsServiceRequestLogRecordFormatter(),
                logCollector,
                false,
                0,
                false);
        List<String> errors = new ArrayList<String>();
        handler.setErrorConsumer(errors::add);

        Span span = tracer.createSpan(spanName);
        String traceId = span.getSpanContext().getTraceId();
        String spanId = span.getSpanContext().getSpanId();
        try {
            handler.info(firstLogMessage);
            handler.info(secondLogMessage);
            handler.info(thirdLogMessage);
            span.end();
        } finally {
            handler.close();
        }

        boolean foundInLoki = waitForLokiMessages(lokiQueryEndpoint, serviceName, flowToken, spanId, expectedMessages, 30_000L);
        boolean foundInJaeger = waitForJaegerTrace(jaegerQueryBase, traceId, spanId, spanName, expectedMessages, 30_000L);

        assertTrue(foundInLoki, "Could not find all logs in Loki for traceId=" + traceId + ", spanId=" + spanId);
        assertTrue(foundInJaeger, "Could not find span and related log events in Jaeger for traceId=" + traceId + ", spanId=" + spanId);
        assertEquals(0, errors.size(), "Unexpected log dispatch errors: " + errors);
    }

    private static boolean waitForLokiMessages(final String queryEndpoint,
                                               final String serviceName,
                                               final String flowToken,
                                               final String spanId,
                                               final List<String> messages,
                                               final long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        String queryExpr = "{service_name=\"" + serviceName + "\"} |= \"" + flowToken + "\"";
        String encodedQuery = URLEncoder.encode(queryExpr, "UTF-8");
        while (System.currentTimeMillis() < deadline) {
            String url = queryEndpoint + "?query=" + encodedQuery + "&limit=20";
            HttpResult result = get(url);
            if (containsAllMessagesForSpan(result, spanId, messages)) {
                return true;
            }
            Thread.sleep(500L);
        }
        return false;
    }

    private static boolean containsAllMessagesForSpan(final HttpResult result,
                                                      final String spanId,
                                                      final List<String> messages) {
        if (result.statusCode < 200 || result.statusCode >= 300) {
            return false;
        }
        if (!result.body.contains(spanId)) {
            return false;
        }
        for (String message : messages) {
            if (!result.body.contains(message)) {
                return false;
            }
        }
        return true;
    }

    private static boolean waitForJaegerTrace(final String queryBase,
                                              final String traceId,
                                              final String spanId,
                                              final String expectedSpanName,
                                              final List<String> expectedMessages,
                                              final long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        String encodedTraceId = URLEncoder.encode(traceId, "UTF-8");
        while (System.currentTimeMillis() < deadline) {
            HttpResult v3 = get(normalizeBaseUrl(queryBase) + "/api/v3/traces/" + encodedTraceId);
            if (containsExpectedJaegerContent(v3, traceId, spanId, expectedSpanName, expectedMessages)) {
                return true;
            }
            HttpResult legacy = get(normalizeBaseUrl(queryBase) + "/api/traces/" + encodedTraceId);
            if (containsExpectedJaegerContent(legacy, traceId, spanId, expectedSpanName, expectedMessages)) {
                return true;
            }
            Thread.sleep(500L);
        }
        return false;
    }

    private static boolean containsExpectedJaegerContent(final HttpResult result,
                                                         final String traceId,
                                                         final String spanId,
                                                         final String expectedSpanName,
                                                         final List<String> expectedMessages) {
        if (result.statusCode < 200 || result.statusCode >= 300) {
            return false;
        }
        if (!result.body.contains(traceId) || !result.body.contains(expectedSpanName) || !result.body.contains(spanId)) {
            return false;
        }
        for (String message : expectedMessages) {
            if (!result.body.contains(message)) {
                return false;
            }
        }
        return true;
    }

    private static HttpResult get(final String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        int statusCode = connection.getResponseCode();
        InputStream inputStream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String responseBody = readAll(inputStream);
        connection.disconnect();
        return new HttpResult(statusCode, responseBody);
    }

    private static String readAll(final InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static String normalizeBaseUrl(final String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static void assumeEndpointReachable(final String endpoint, final String label) throws Exception {
        URL url = new URL(endpoint);
        boolean reachable = isPortReachable(url.getHost(), effectivePort(url), 400);
        assumeTrue(reachable, label + " is not reachable at " + endpoint);
    }

    private static int effectivePort(final URL url) {
        if (url.getPort() > 0) {
            return url.getPort();
        }
        return "https".equalsIgnoreCase(url.getProtocol()) ? 443 : 80;
    }

    private static boolean isPortReachable(final String host, final int port, final int timeoutMillis) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String readEnvOrDefault(final String envName, final String defaultValue) {
        String value = System.getenv(envName);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static final class HttpResult {
        private final int statusCode;
        private final String body;

        private HttpResult(final int statusCode, final String body) {
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
        }
    }
}
