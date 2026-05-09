package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordFactoryImpl;
import com.threeamigos.common.util.implementations.messagehandler.utils.OTelCollectorDispatcher;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("OTelCollectorDispatcher with handlers unit tests")
@Tag("unit")
@Tag("messageHandler")
class OTelCollectorDispatcherWithHandlersUnitTest {

    @Test
    @DisplayName("jaeger and grafana handlers should fan out through collector dispatcher")
    void handlersShouldFanOutThroughCollectorDispatcher() {
        AtomicInteger first = new AtomicInteger(0);
        AtomicInteger second = new AtomicInteger(0);
        LogRecordDispatcher d1 = (record, formatter) -> first.incrementAndGet();
        LogRecordDispatcher d2 = (record, formatter) -> second.incrementAndGet();

        OTelCollectorDispatcher collector = new OTelCollectorDispatcher();
        collector.addDispatcher(d1);
        collector.addDispatcher(d2);

        JaegerMessageHandler jaegerHandler = new JaegerMessageHandler(
                new LogRecordFactoryImpl(),
                lr -> "{}",
                collector,
                false,
                0,
                false);
        GrafanaMessageHandler grafanaHandler = new GrafanaMessageHandler(
                new LogRecordFactoryImpl(),
                lr -> "{}",
                collector,
                false,
                0,
                false);

        try {
            jaegerHandler.info("first");
            grafanaHandler.info("second");
        } finally {
            jaegerHandler.close();
            grafanaHandler.close();
        }

        assertEquals(2, first.get());
        assertEquals(2, second.get());
    }
}
