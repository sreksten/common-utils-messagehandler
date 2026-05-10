package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.implementations.messagehandler.filters.FilterByClassName;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Filter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Resource;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("Roadmap public API integration test")
@Tag("unit")
@Tag("messageHandler")
class RoadmapTest {

    @Test
    @DisplayName("should execute the Proposed Public API snippet verbatim")
    void shouldExecuteProposedPublicApiSnippetVerbatim() {
        final Throwable ex = new RuntimeException("db timeout");

        assertDoesNotThrow(() -> {
            TracerProvider tracerProvider = TracerProvider.builder()
                    .serviceName("checkout-api")
                    .serviceVersion("1.4.2")
                    .serviceInstanceId("checkout-api-01")
                    .deploymentEnvironment("prod")
                    .schemaUrl("https://opentelemetry.io/schemas/1.26.0")
                    .resourceAttribute(OTelTags.SERVICE_NAMESPACE, "payments")
                    .resourceAttribute(OTelTags.CLOUD_PROVIDER, "aws")
                    .commonAttribute("app.region", "eu-west-1")
                    .build();

            Filter filter = new FilterByClassName();

            Tracer tracer = tracerProvider.getTracer("checkout-api", filter);
            MessageHandler handler = tracer.getFileMessageHandler("message-handler.log", filter);

            handler.info("order accepted");
            handler.error("payment failed");
            handler.exception("db timeout", ex);
            handler.close();
        });
    }

    @Test
    @DisplayName("should execute roadmap flow with a fully populated resource built via ResourceBuilder")
    void shouldExecuteRoadmapFlowWithFullyPopulatedResourceBuilder() {
        final Throwable ex = new RuntimeException("db timeout");

        assertDoesNotThrow(() -> {
            ResourceBuilderImpl resourceBuilder = new ResourceBuilderImpl();
            resourceBuilder.withServiceName("checkout-api");
            resourceBuilder.withServiceNamespace("payments");
            resourceBuilder.withServiceVersion("1.4.2");
            resourceBuilder.withServiceInstanceId("checkout-api-01");
            resourceBuilder.withDeploymentEnvironmentName("prod");
            resourceBuilder.withSchemaUrl("https://opentelemetry.io/schemas/1.26.0");

            resourceBuilder.withEntity(EntityBuilderFactory.getBuilder()
                    .withServiceType()
                    .withServiceName("checkout-api")
                    .withSchemaUrl("https://opentelemetry.io/schemas/1.26.0")
                    .withIdString("entity.id", "checkout-entity")
                    .withIdBoolean("entity.id.bool", true)
                    .withIdLong("entity.id.long", 10L)
                    .withIdDouble("entity.id.double", 10.5D)
                    .withIdArray("entity.id.array", Arrays.asList(
                            AnyValueFactory.ofString("id-a"),
                            AnyValueFactory.ofLong(1L)))
                    .withIdKeyValueList("entity.id.kvlist", Collections.singletonList(
                            KeyValueFactory.of("id.nested.k", AnyValueFactory.ofString("id.nested.v"))))
                    .withIdBytes("entity.id.bytes", new byte[]{1, 2, 3})
                    .withDescriptionString("entity.description", "checkout service entity")
                    .withDescriptionBoolean("entity.description.bool", true)
                    .withDescriptionLong("entity.description.long", 11L)
                    .withDescriptionDouble("entity.description.double", 11.5D)
                    .withDescriptionArray("entity.description.array", Arrays.asList(
                            AnyValueFactory.ofString("desc-a"),
                            AnyValueFactory.ofLong(2L)))
                    .withDescriptionKeyValueList("entity.description.kvlist", Collections.singletonList(
                            KeyValueFactory.of("desc.nested.k", AnyValueFactory.ofString("desc.nested.v"))))
                    .withDescriptionBytes("entity.description.bytes", new byte[]{4, 5, 6})
                    .build());
            resourceBuilder.withEntity(EntityBuilderFactory.getBuilder()
                    .withType("browser")
                    .withSchemaUrl("https://opentelemetry.io/schemas/1.26.0")
                    .withIdString("entity2.id", "browser-entity")
                    .withIdBoolean("entity2.id.bool", false)
                    .withIdLong("entity2.id.long", 20L)
                    .withIdDouble("entity2.id.double", 20.5D)
                    .withIdArray("entity2.id.array", Arrays.asList(
                            AnyValueFactory.ofString("id2-a"),
                            AnyValueFactory.ofLong(3L)))
                    .withIdKeyValueList("entity2.id.kvlist", Collections.singletonList(
                            KeyValueFactory.of("id2.nested.k", AnyValueFactory.ofString("id2.nested.v"))))
                    .withIdBytes("entity2.id.bytes", new byte[]{7, 8, 9})
                    .withDescriptionString("entity2.description", "browser entity")
                    .withDescriptionBoolean("entity2.description.bool", false)
                    .withDescriptionLong("entity2.description.long", 21L)
                    .withDescriptionDouble("entity2.description.double", 21.5D)
                    .withDescriptionArray("entity2.description.array", Arrays.asList(
                            AnyValueFactory.ofString("desc2-a"),
                            AnyValueFactory.ofLong(4L)))
                    .withDescriptionKeyValueList("entity2.description.kvlist", Collections.singletonList(
                            KeyValueFactory.of("desc2.nested.k", AnyValueFactory.ofString("desc2.nested.v"))))
                    .withDescriptionBytes("entity2.description.bytes", new byte[]{10, 11, 12})
                    .build());
            resourceBuilder.withNoEntity();

            resourceBuilder.withEmpty("resource.empty");
            resourceBuilder.withString("resource.string", "value");
            resourceBuilder.withBoolean("resource.bool", true);
            resourceBuilder.withLong("resource.long", 42L);
            resourceBuilder.withDouble("resource.double", 42.5D);
            resourceBuilder.withArray("resource.array", Arrays.asList(
                    AnyValueFactory.ofString("a"),
                    AnyValueFactory.ofLong(1L)));
            resourceBuilder.withKeyValueList("resource.kvlist", Collections.singletonList(
                    KeyValueFactory.of("nested.key", AnyValueFactory.ofString("nested.value"))));
            resourceBuilder.withBytes("resource.bytes", new byte[]{1, 2, 3});
            Resource fullResource = resourceBuilder.build();

            TracerProvider tracerProvider = TracerProvider.builder()
                    .serviceName("checkout-api")
                    .serviceVersion("1.4.2")
                    .serviceInstanceId("checkout-api-01")
                    .deploymentEnvironment("prod")
                    .schemaUrl("https://opentelemetry.io/schemas/1.26.0")
                    .resource(fullResource)
                    .resourceAttribute(OTelTags.SERVICE_NAMESPACE, "payments")
                    .resourceAttribute(OTelTags.CLOUD_PROVIDER, "aws")
                    .commonAttribute("app.region", "eu-west-1")
                    .build();

            Filter filter = new FilterByClassName();

            Tracer tracer = tracerProvider.getTracer("checkout-api", filter);
            MessageHandler handler = tracer.getFileMessageHandler("message-handler.log", filter);

            handler.info("order accepted");
            handler.error("payment failed");
            handler.exception("db timeout", ex);
            handler.close();
        });
    }
}
