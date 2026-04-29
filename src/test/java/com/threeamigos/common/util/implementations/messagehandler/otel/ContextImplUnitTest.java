package com.threeamigos.common.util.implementations.messagehandler.otel;

import com.threeamigos.common.util.interfaces.messagehandler.otel.AnyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ContextImpl unit tests")
@Tag("unit")
@Tag("messageHandler")
class ContextImplUnitTest {

    @Test
    @DisplayName("createKey should return opaque keys and recover from null names")
    void createKeyShouldReturnOpaqueKeysAndRecoverFromNullNames() {
        ContextImpl sut = new ContextImpl();

        Context.Key key1 = sut.createKey("key");
        Context.Key key2 = sut.createKey("key");
        Context.Key nullName = sut.createKey(null);

        assertNotNull(key1);
        assertNotNull(key2);
        assertNotNull(nullName);
        assertEquals("key", key1.getName());
        assertEquals("key", key2.getName());
        assertFalse(key1 == key2);
        assertNotNull(nullName.getName());
        assertFalse(nullName.getName().trim().isEmpty());
        assertNotSame("key", nullName.getName());
    }

    @Test
    @DisplayName("set and get should be immutable and tolerate invalid inputs")
    void setAndGetShouldBeImmutableAndIgnoreInvalidInputs() {
        ContextImpl sut = new ContextImpl();
        Context.Key key = sut.createKey("k");
        AnyValue value = AnyValueFactory.ofString("v");

        Context updated = sut.set(key, value);

        assertNull(sut.get(key));
        assertEquals("v", updated.get(key).asString());

        assertSame(sut, sut.set(null, value));

        Context withNullValue = sut.set(key, null);
        assertNotSame(sut, withNullValue);
        assertNull(withNullValue.get(key));

        assertNull(sut.get(null));
    }

    @Test
    @DisplayName("keys created with the same name should remain distinct")
    void keysCreatedWithSameNameShouldRemainDistinct() {
        ContextImpl sut = new ContextImpl();
        Context.Key key1 = sut.createKey("same");
        Context.Key key2 = sut.createKey("same");

        Context updated = sut.set(key1, AnyValueFactory.ofString("v1"));

        assertEquals("v1", updated.get(key1).asString());
        assertNull(updated.get(key2));
    }

    @Test
    @DisplayName("getContext should always return non-null context")
    void getContextShouldAlwaysReturnNonNullContext() {
        ContextImpl sut = new ContextImpl();
        assertNotNull(sut.getContext());
    }

    @Test
    @DisplayName("attach and detach should follow strict LIFO token semantics")
    void attachAndDetachShouldFollowStrictLifoTokenSemantics() {
        ContextImpl sut = new ContextImpl();
        Context.Key key = sut.createKey("k");
        Context ctxA = sut.set(key, AnyValueFactory.ofString("A"));
        Context ctxB = sut.set(key, AnyValueFactory.ofString("B"));

        String tokenA = sut.attachContext(ctxA);
        assertSame(ctxA, sut.getContext());

        String tokenB = sut.attachContext(ctxB);
        assertSame(ctxB, sut.getContext());

        assertFalse(sut.detachContext(tokenA));
        assertSame(ctxB, sut.getContext());

        assertTrue(sut.detachContext(tokenB));
        assertSame(ctxA, sut.getContext());

        assertTrue(sut.detachContext(tokenA));
        assertNull(sut.getContext().get(key));
    }

    @Test
    @DisplayName("attach should ignore null context and detach should reject null token")
    void attachShouldIgnoreNullContextAndDetachShouldRejectNullToken() {
        ContextImpl sut = new ContextImpl();
        assertNull(sut.attachContext(null));
        assertFalse(sut.detachContext(null));
        assertFalse(sut.detachContext("missing"));
    }

    @Test
    @DisplayName("current context should be thread-local isolated")
    void currentContextShouldBeThreadLocalIsolated() throws Exception {
        ContextImpl sut = new ContextImpl();
        Context.Key key = sut.createKey("thread-key");

        Context mainContext = sut.set(key, AnyValueFactory.ofString("main"));
        String mainToken = sut.attachContext(mainContext);
        assertEquals("main", sut.getContext().get(key).asString());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Callable<String[]> worker = () -> {
                AnyValue beforeAttach = sut.getContext().get(key);
                Context threadContext = sut.set(key, AnyValueFactory.ofString("worker"));
                String workerToken = sut.attachContext(threadContext);
                String duringAttach = sut.getContext().get(key).asString();
                boolean detached = sut.detachContext(workerToken);
                AnyValue afterDetach = sut.getContext().get(key);
                return new String[] {
                        beforeAttach == null ? "null" : beforeAttach.asString(),
                        duringAttach,
                        String.valueOf(detached),
                        afterDetach == null ? "null" : afterDetach.asString()
                };
            };

            Future<String[]> future = executor.submit(worker);
            String[] result = future.get();

            assertEquals("null", result[0]);
            assertEquals("worker", result[1]);
            assertEquals("true", result[2]);
            assertEquals("null", result[3]);
        } finally {
            executor.shutdownNow();
        }

        assertEquals("main", sut.getContext().get(key).asString());
        assertTrue(sut.detachContext(mainToken));
        assertNull(sut.getContext().get(key));
    }
}
