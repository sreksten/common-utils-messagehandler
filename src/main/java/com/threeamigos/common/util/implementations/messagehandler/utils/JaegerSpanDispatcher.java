package com.threeamigos.common.util.implementations.messagehandler.utils;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Map;

/**
 * OTLP span dispatcher for Jaeger-compatible trace ingestion endpoints.
 */
public class JaegerSpanDispatcher extends OtlpSpanDispatcher {

    public JaegerSpanDispatcher(final @Nonnull String endpointUrl) {
        super(endpointUrl);
    }

    public JaegerSpanDispatcher(final @Nonnull String endpointUrl,
                                final @Nullable String username,
                                final @Nullable String password) {
        super(endpointUrl, username, password);
    }

    public JaegerSpanDispatcher(final @Nonnull String endpointUrl,
                                final @Nullable String username,
                                final @Nullable String password,
                                final @Nullable String bearerToken,
                                final int connectTimeoutMillis,
                                final int readTimeoutMillis,
                                final @Nullable Map<String, String> additionalHeaders) {
        super(endpointUrl, username, password, bearerToken, connectTimeoutMillis, readTimeoutMillis, additionalHeaders);
    }
}
