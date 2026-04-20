package com.threeamigos.common.util.implementations.messagehandler.tracecontext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TraceContextGenerator unit tests")
@Tag("unit")
@Tag("messageHandler")
class TraceContextGeneratorUnitTest {

    @Test
    @DisplayName("generateTraceId() should return a compliant trace-id")
    void generateTraceIdShouldReturnCompliantTraceId() {
        String traceId = TraceContextGenerator.generateTraceId();
        assertEquals(32, traceId.length());
        assertTrue(TraceContextValidator.isValidTraceId(traceId));
    }

    @Test
    @DisplayName("generateParentId() should return a compliant parent-id")
    void generateParentIdShouldReturnCompliantParentId() {
        String parentId = TraceContextGenerator.generateParentId();
        assertEquals(16, parentId.length());
        assertTrue(TraceContextValidator.isValidParentId(parentId));
    }

    @Test
    @DisplayName("generation should not collapse to a single repeated value")
    void generationShouldNotCollapseToSingleRepeatedValue() {
        Set<String> traceIds = new HashSet<String>();
        Set<String> parentIds = new HashSet<String>();
        for (int i = 0; i < 32; i++) {
            traceIds.add(TraceContextGenerator.generateTraceId());
            parentIds.add(TraceContextGenerator.generateParentId());
        }

        assertTrue(traceIds.size() > 1);
        assertTrue(parentIds.size() > 1);
    }
}
