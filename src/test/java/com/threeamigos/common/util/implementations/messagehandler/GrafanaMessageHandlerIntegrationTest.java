package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.otel.TracerProvider;
import com.threeamigos.common.util.implementations.messagehandler.otel.formatters.ExportLogsServiceRequestLogRecordFormatter;
import com.threeamigos.common.util.implementations.messagehandler.utils.GrafanaLogRecordDispatcher;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("GrafanaMessageHandler integration tests")
@Tag("integration")
@Tag("messageHandler")
class GrafanaMessageHandlerIntegrationTest {

    @Test
    @DisplayName("should send log to local Loki and retrieve it through query API")
    void shouldSendLogToLocalLokiAndRetrieveItThroughQueryApi() throws Exception {
        String pushEndpoint = readEnvOrDefault("GRAFANA_LOKI_PUSH_ENDPOINT", "http://localhost:3100/loki/api/v1/push");
        String queryEndpoint = readEnvOrDefault("GRAFANA_LOKI_QUERY_ENDPOINT", "http://localhost:3100/loki/api/v1/query_range");

        URL pushUrl = new URL(pushEndpoint);
        URL queryUrl = new URL(queryEndpoint);
        assumeTrue(isPortReachable(pushUrl.getHost(), effectivePort(pushUrl), 400),
                "Grafana/Loki push endpoint is not reachable at " + pushEndpoint);
        assumeTrue(isPortReachable(queryUrl.getHost(), effectivePort(queryUrl), 400),
                "Grafana/Loki query endpoint is not reachable at " + queryEndpoint);

        String serviceName = "MyTestService";
        String serviceVersion = "1.0-alpha";
        String message = "Hello Grafana! " + System.currentTimeMillis();

        TracerProvider provider = TracerProvider.builder()
                .serviceName(serviceName)
                .serviceVersion(serviceVersion)
                .build();
        Tracer tracer = provider.getTracer(serviceName, serviceVersion);
        LogRecordFactory logRecordFactory = tracer.getLogRecordFactory();

        MessageHandler handler = new GrafanaMessageHandler(
                logRecordFactory,
                new ExportLogsServiceRequestLogRecordFormatter(),
                new GrafanaLogRecordDispatcher(pushEndpoint),
                false,
                0,
                false);
        List<String> errors = new ArrayList<String>();
        ((GrafanaMessageHandler) handler).setErrorConsumer(errors::add);

        Span span = tracer.createSpan("grafana-integration-span");
        try {
            handler.info(message);
            boolean found = waitForMessage(queryEndpoint, serviceName, message, 25_000L);
            assertTrue(found, "Could not find message in Loki query response");
        } finally {
            span.end();
            handler.close();
        }

        assertEquals(0, errors.size(), "Unexpected dispatch errors: " + errors);
    }

    private static boolean waitForMessage(final String queryEndpoint,
                                          final String serviceName,
                                          final String message,
                                          final long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        String queryExpr = "{service_name=\"" + serviceName + "\"} |= \"" + message + "\"";
        String encodedQuery = URLEncoder.encode(queryExpr, "UTF-8");
        while (System.currentTimeMillis() < deadline) {
            String url = queryEndpoint + "?query=" + encodedQuery + "&limit=20";
            HttpResult result = get(url);
            if (result.statusCode >= 200 && result.statusCode < 300 && result.body.contains(message)) {
                return true;
            }
            Thread.sleep(500L);
        }
        return false;
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

    private static String readEnvOrDefault(final String envName, final String defaultValue) {
        String value = System.getenv(envName);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
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

    private static final class HttpResult {
        private final int statusCode;
        private final String body;

        private HttpResult(final int statusCode, final String body) {
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
        }
    }
}
