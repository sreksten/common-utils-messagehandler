package com.threeamigos.common.util.interfaces.messagehandler.transport;

/**
 * Immutable result of a single HTTP transport call.
 *
 * @author Stefano Reksten
 */
public final class HttpTransportResponse {

    private final int statusCode;
    private final String body;

    /**
     * Constructs a response.
     *
     * @param statusCode HTTP status code returned by the server
     * @param body       response body; {@code null} is normalised to an empty string
     */
    public HttpTransportResponse(final int statusCode, final String body) {
        this.statusCode = statusCode;
        this.body = body == null ? "" : body;
    }

    /**
     * @return HTTP status code
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * @return response body (never {@code null})
     */
    public String getBody() {
        return body;
    }
}
