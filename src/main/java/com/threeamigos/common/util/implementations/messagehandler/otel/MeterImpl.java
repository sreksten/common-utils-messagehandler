package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Meter;

/**
 * Immutable meter implementation returned by {@link MetricsProviderImpl}.
 * <p>
 * Package-private on purpose: callers should obtain meters only via {@link MetricsProviderImpl}.
 *
 * @author Stefano Reksten
 */
final class MeterImpl implements Meter {

    private final InstrumentationScope instrumentationScope;

    MeterImpl(final InstrumentationScope instrumentationScope) {
        this.instrumentationScope = instrumentationScope;
    }

    @Override
    public InstrumentationScope getInstrumentationScope() {
        return instrumentationScope;
    }
}
