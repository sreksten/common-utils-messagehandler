package com.threeamigos.common.util.implementations.messagehandler.transport;

import com.threeamigos.common.util.interfaces.messagehandler.transport.HttpTransportResponse;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApacheHttpClientTransport unit tests")
@Tag("unit")
@Tag("messageHandler")
class ApacheHttpClientTransportUnitTest {

    @Mock
    private CloseableHttpClient httpClient;

    @Mock
    private CloseableHttpResponse httpResponse;

    @Mock
    private StatusLine statusLine;

    @Mock
    private HttpEntity httpEntity;

    @Test
    @DisplayName("post should return status and body from a 200 response with entity")
    void post_200WithEntity_returnsStatusAndBody() throws Exception {
        when(httpClient.execute(any(HttpUriRequest.class))).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(httpResponse.getEntity()).thenReturn(httpEntity);
        when(httpEntity.getContent()).thenReturn(
                new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8)));

        ApacheHttpClientTransport transport = new ApacheHttpClientTransport(httpClient);
        HttpTransportResponse response = transport.post(
                new URL("http://localhost:4318/v1/logs"),
                "payload".getBytes(StandardCharsets.UTF_8),
                Collections.<String, String>emptyMap(),
                5000, 5000);

        assertEquals(200, response.getStatusCode());
        assertEquals("ok", response.getBody());
    }

    @Test
    @DisplayName("post should return status and error body from a 500 response with entity")
    void post_500WithEntity_returnsStatusAndErrorBody() throws Exception {
        when(httpClient.execute(any(HttpUriRequest.class))).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(500);
        when(httpResponse.getEntity()).thenReturn(httpEntity);
        when(httpEntity.getContent()).thenReturn(
                new ByteArrayInputStream("server error".getBytes(StandardCharsets.UTF_8)));

        ApacheHttpClientTransport transport = new ApacheHttpClientTransport(httpClient);
        HttpTransportResponse response = transport.post(
                new URL("http://localhost:4318/v1/logs"),
                new byte[0],
                Collections.<String, String>emptyMap(),
                5000, 5000);

        assertEquals(500, response.getStatusCode());
        assertEquals("server error", response.getBody());
    }

    @Test
    @DisplayName("post should return empty body when entity is null")
    void post_nullEntity_returnsEmptyBody() throws Exception {
        when(httpClient.execute(any(HttpUriRequest.class))).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(204);
        when(httpResponse.getEntity()).thenReturn(null);

        ApacheHttpClientTransport transport = new ApacheHttpClientTransport(httpClient);
        HttpTransportResponse response = transport.post(
                new URL("http://localhost:4318/v1/logs"),
                new byte[0],
                Collections.<String, String>emptyMap(),
                5000, 5000);

        assertEquals(204, response.getStatusCode());
        assertEquals("", response.getBody());
    }

    @Test
    @DisplayName("post should apply all provided headers to the outgoing request")
    void post_appliesHeadersToRequest() throws Exception {
        when(httpClient.execute(any(HttpUriRequest.class))).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(httpResponse.getEntity()).thenReturn(null);

        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom", "value");

        ApacheHttpClientTransport transport = new ApacheHttpClientTransport(httpClient);
        transport.post(new URL("http://localhost:4318/v1/logs"),
                new byte[0], headers, 5000, 5000);

        ArgumentCaptor<HttpUriRequest> captor = ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(httpClient).execute(captor.capture());
        HttpUriRequest request = captor.getValue();
        assertEquals("application/json", request.getFirstHeader("Content-Type").getValue());
        assertEquals("value", request.getFirstHeader("X-Custom").getValue());
    }

    @Test
    @DisplayName("post should propagate IOException thrown by execute")
    void post_executeThrowsIOException_propagates() throws Exception {
        when(httpClient.execute(any(HttpUriRequest.class))).thenThrow(new IOException("network error"));

        ApacheHttpClientTransport transport = new ApacheHttpClientTransport(httpClient);

        IOException thrown = assertThrows(IOException.class, () ->
                transport.post(new URL("http://localhost:4318/v1/logs"),
                        new byte[0], Collections.<String, String>emptyMap(), 5000, 5000));
        assertEquals("network error", thrown.getMessage());
    }

    @Test
    @DisplayName("close should delegate to the underlying CloseableHttpClient")
    void close_delegatesToUnderlyingClient() throws Exception {
        ApacheHttpClientTransport transport = new ApacheHttpClientTransport(httpClient);
        transport.close();
        verify(httpClient).close();
    }

    @Test
    @DisplayName("constructor should reject null CloseableHttpClient")
    void constructor_nullClient_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                new ApacheHttpClientTransport(null));
    }

    @Test
    @DisplayName("no-arg constructor should create a functional transport instance")
    void noArgConstructor_createsNonNullInstance() {
        ApacheHttpClientTransport transport = new ApacheHttpClientTransport();
        assertNotNull(transport);
    }
}
