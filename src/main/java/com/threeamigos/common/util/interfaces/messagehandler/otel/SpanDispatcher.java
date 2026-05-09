package com.threeamigos.common.util.interfaces.messagehandler.otel;

import jakarta.annotation.Nonnull;

import java.io.IOException;

/**
 * Generic dispatcher contract for exporting completed spans.
 */
public interface SpanDispatcher {

    void dispatchSpan(final @Nonnull SpanData spanData) throws IOException;
}
