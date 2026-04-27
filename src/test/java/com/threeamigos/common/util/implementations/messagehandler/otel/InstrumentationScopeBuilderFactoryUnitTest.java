package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.InstrumentationScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("InstrumentationScopeBuilder unit tests")
@Tag("unit")
@Tag("messageHandler")
class InstrumentationScopeBuilderFactoryUnitTest {

    @Test
    @DisplayName("getBuilder() should return InstrumentationScopeBuilderImpl step0")
    void getBuilderShouldReturnStep0() {
        assertNotNull(InstrumentationScopeBuilderFactory.getBuilder());
        assertTrue(InstrumentationScopeBuilderFactory.getBuilder() instanceof InstrumentationScopeBuilderImpl);
    }

    @Test
    @DisplayName("InstrumentationScopeBuilderImpl should store fields and build scope")
    void instrumentationScopeBuilderImplShouldStoreFieldsAndBuildScope() {
        InstrumentationScopeBuilderImpl builder = new InstrumentationScopeBuilderImpl();

        assertSame(builder, builder.withName("scope.name"));
        assertSame(builder, builder.withVersion("1.0.0"));
        assertSame(builder, builder.withScopeUrl("https://opentelemetry.io/schemas/1.27.0"));

        assertSame(builder, builder.withString("k", "v"));
        assertEquals(1, builder.attributes.size());

        InstrumentationScope scope = builder.build();
        assertEquals("scope.name", scope.getName());
        assertEquals("1.0.0", scope.getVersion());
        assertEquals("https://opentelemetry.io/schemas/1.27.0", scope.getSchemaUrl());
        assertEquals(1, scope.getAttributes().size());
        assertEquals("k", scope.getAttributes().get(0).getKey());
    }

    @Test
    @DisplayName("with... methods should reject null or blank values")
    void withMethodsShouldRejectNullOrBlankValues() {
        InstrumentationScopeBuilderImpl builder = new InstrumentationScopeBuilderImpl();
        assertThrows(NullPointerException.class, () -> builder.withName(null));
        assertThrows(IllegalArgumentException.class, () -> builder.withName("   "));

        assertThrows(NullPointerException.class, () -> builder.withName("scope").withVersion(null));
        assertThrows(IllegalArgumentException.class, () -> builder.withName("scope").withVersion(""));

        assertThrows(NullPointerException.class, () -> builder.withName("scope").withScopeUrl(null));
        assertThrows(IllegalArgumentException.class, () -> builder.withName("scope").withScopeUrl(" "));
    }
}
