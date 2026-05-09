package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ExportLogsServiceRequestLogRecordFormatter;
import com.threeamigos.common.util.implementations.messagehandler.utils.JaegerLogRecordDispatcher;
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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("JaegerMessageHandler integration tests")
@Tag("integration")
@Tag("messageHandler")
class JaegerMessageHandlerIntegrationTest {

    @Test
    @DisplayName("should send log as span event through jaeger dispatcher and retrieve it from Jaeger v2")
    void shouldSendLogAsSpanEventThroughJaegerDispatcherAndRetrieveItFromJaegerV2() throws Exception {
        String collectorEndpoint = readEnvOrDefault("JAEGER_OTLP_TRACES_ENDPOINT", "http://localhost:4318/v1/traces");
        String queryBaseEndpoint = readEnvOrDefault("JAEGER_QUERY_TRACE_ENDPOINT", "http://localhost:16686");

        URL collectorUrl = new URL(collectorEndpoint);
        URL queryUrl = new URL(queryBaseEndpoint);
        assumeTrue(isPortReachable(collectorUrl.getHost(), effectivePort(collectorUrl), 400),
                "Jaeger collector is not reachable at " + collectorEndpoint);
        assumeTrue(isPortReachable(queryUrl.getHost(), effectivePort(queryUrl), 400),
                "Jaeger query API is not reachable at " + queryBaseEndpoint);

        String serviceName = "MyTestService";
        String serviceVersion = "1.0-alpha";
        String message = "Hello Jaeger! " + System.currentTimeMillis();

        TracerProvider provider = TracerProvider.builder()
                .serviceName(serviceName)
                .serviceVersion(serviceVersion)
                .build();
        Tracer tracer = provider.getTracer(serviceName, serviceVersion);
        LogRecordFactory logRecordFactory = tracer.getLogRecordFactory();
        JaegerMessageHandler handler = new JaegerMessageHandler(
                logRecordFactory,
                new ExportLogsServiceRequestLogRecordFormatter(),
                new JaegerLogRecordDispatcher(collectorEndpoint),
                false,
                0,
                false);
        List<String> errors = new ArrayList<>();
        handler.setErrorConsumer(errors::add);

        Span span = tracer.createSpan("jaeger-v2-integration-span");
        try {
            handler.info(message);
            boolean found = waitForMessage(queryBaseEndpoint, span.getSpanContext().getTraceId(), message, 20_000L);
            assertTrue(found, "Could not find message in Jaeger query response for traceId="
                    + span.getSpanContext().getTraceId());
        } finally {
            span.end();
            handler.close();
        }
        assertEquals(0, errors.size(), "Unexpected dispatch errors: " + errors);
    }

    private static boolean isPortReachable(final String host, final int port, final int timeoutMillis) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static int effectivePort(final URL url) {
        if (url.getPort() > 0) {
            return url.getPort();
        }
        return "https".equalsIgnoreCase(url.getProtocol()) ? 443 : 80;
    }

    private static String readEnvOrDefault(final String envName, final String defaultValue) {
        String value = System.getenv(envName);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static boolean waitForMessage(final String queryBaseEndpoint,
                                          final String traceIdHex,
                                          final String expectedMessage,
                                          final long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        String encodedTraceId = URLEncoder.encode(traceIdHex, "UTF-8");
        while (System.currentTimeMillis() < deadline) {
            HttpResult v3Result = get(normalizeBaseUrl(queryBaseEndpoint) + "/api/v3/traces/" + encodedTraceId);
            if (v3Result.statusCode >= 200 && v3Result.statusCode < 300 && v3Result.body.contains(expectedMessage)) {
                return true;
            }
            HttpResult legacyResult = get(normalizeBaseUrl(queryBaseEndpoint) + "/api/traces/" + encodedTraceId);
            if (legacyResult.statusCode >= 200 && legacyResult.statusCode < 300
                    && legacyResult.body.contains(expectedMessage)) {
                return true;
            }
            Thread.sleep(400L);
        }
        return false;
    }

    private static String normalizeBaseUrl(final String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
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
    private static final class HttpResult {
        private final int statusCode;
        private final String body;

        private HttpResult(final int statusCode, final String body) {
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
        }
    }
}
