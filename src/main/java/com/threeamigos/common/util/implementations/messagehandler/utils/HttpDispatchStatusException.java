package com.threeamigos.common.util.implementations.messagehandler.utils;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.IOException;

/**
 * IOException raised when an HTTP dispatcher receives a non-success status code.
 */
public class HttpDispatchStatusException extends IOException {

    private final String endpoint;
    private final int statusCode;
    private final String responseBody;

    /**
     * Creates a status-based dispatch exception.
     *
     * @param endpoint endpoint URL that returned a non-success status
     * @param statusCode returned HTTP status code
     * @param responseBody response body (nullable)
     */
    public HttpDispatchStatusException(final @Nonnull String endpoint,
                                       final int statusCode,
                                       final @Nullable String responseBody) {
        super(buildMessage(endpoint, statusCode, responseBody));
        this.endpoint = endpoint;
        this.statusCode = statusCode;
        this.responseBody = responseBody == null ? "" : responseBody;
    }

    /**
     * @return endpoint URL associated with this failure
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * @return non-success HTTP status code
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * @return response body returned by the endpoint (empty string when absent)
     */
    public String getResponseBody() {
        return responseBody;
    }

    private static String buildMessage(final String endpoint,
                                       final int statusCode,
                                       final String responseBody) {
        StringBuilder messageBuilder = new StringBuilder(192);
        messageBuilder.append("HTTP dispatch failed with status ")
                .append(statusCode)
                .append(" on endpoint ")
                .append(endpoint == null ? "<unknown>" : endpoint);
        if (responseBody != null && !responseBody.trim().isEmpty()) {
            messageBuilder.append(", responseBody=").append(responseBody);
        }
        return messageBuilder.toString();
    }
}
