package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("CompositeMessageHandler unit test")
@Tag("unit")
@Tag("messageHandler")
class CompositeMessageHandlerUnitTest {

    private static final String FIRST_MESSAGE = "First message";
    private static final String SECOND_MESSAGE = "Second message";

    private MessageHandler firstMessageHandler;
    private MessageHandler secondMessageHandler;

    @BeforeEach
    void setup() {
        firstMessageHandler = mock(MessageHandler.class);
        secondMessageHandler = mock(MessageHandler.class);
    }

    @Test
    @DisplayName("Collection constructor should throw exception if null collection provided")
    void collectionConstructorShouldThrowExceptionIfNullCollectionProvided() {
        assertThrows(NullPointerException.class, () -> new CompositeMessageHandler((Collection<MessageHandler>) null));
    }

    @Test
    @DisplayName("getHandlerCount should return 0 for empty composite")
    void getHandlerCountShouldReturnZeroForEmptyComposite() {
        CompositeMessageHandler sut = new CompositeMessageHandler();
        assertEquals(0, sut.getHandlerCount());
    }

    @Test
    @DisplayName("getHandlerCount should reflect handlers added via constructor")
    void getHandlerCountShouldReflectConstructorHandlers() {
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        assertEquals(2, sut.getHandlerCount());
    }

    @Test
    @DisplayName("getHandlerCount should increment after addMessageHandler")
    void getHandlerCountShouldIncrementAfterAdd() {
        CompositeMessageHandler sut = new CompositeMessageHandler();
        sut.addMessageHandler(firstMessageHandler);
        assertEquals(1, sut.getHandlerCount());
        sut.addMessageHandler(secondMessageHandler);
        assertEquals(2, sut.getHandlerCount());
    }

    @Test
    @DisplayName("getHandlerCount should decrement after removeMessageHandler")
    void getHandlerCountShouldDecrementAfterRemove() {
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        sut.removeMessageHandler(firstMessageHandler);
        assertEquals(1, sut.getHandlerCount());
        sut.removeMessageHandler(secondMessageHandler);
        assertEquals(0, sut.getHandlerCount());
    }

    @Test
    @DisplayName("Collection constructor should keep track of arguments")
    void collectionConstructorShouldKeepTrackOfArguments() {
        // Given
        Collection<MessageHandler> messageHandlersParameter = new ArrayList<>();
        messageHandlersParameter.add(firstMessageHandler);
        messageHandlersParameter.add(secondMessageHandler);
        CompositeMessageHandler sut = new CompositeMessageHandler(messageHandlersParameter);
        // When
        Collection<MessageHandler> messageHandlers = sut.getMessageHandlers();
        // Then
        assertThat(messageHandlers, containsInAnyOrder(firstMessageHandler, secondMessageHandler));
    }

    @Test
    @DisplayName("Varargs constructor should throw exception if null array provided")
    void varargsConstructorShouldThrowExceptionIfNullArrayProvided() {
        assertThrows(NullPointerException.class, () -> new CompositeMessageHandler((MessageHandler[]) null));
    }

    @Test
    @DisplayName("Varargs constructor should throw exception if null MessageHandler provided")
    void varargsConstructorShouldThrowExceptionIfNullArgumentProvided() {
        assertThrows(NullPointerException.class, () -> new CompositeMessageHandler(new MessageHandler[]{null}));
    }

    @Test
    @DisplayName("Collection constructor should throw exception if collection contains null")
    void collectionConstructorShouldThrowExceptionIfCollectionContainsNull() {
        Collection<MessageHandler> handlers = new ArrayList<>();
        handlers.add(firstMessageHandler);
        handlers.add(null);
        assertThrows(NullPointerException.class, () -> new CompositeMessageHandler(handlers));
    }

    @Test
    @DisplayName("Varargs constructor should keep track of arguments")
    void varargsConstructorShouldKeepTrackOfArguments() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        Collection<MessageHandler> messageHandlers = sut.getMessageHandlers();
        // Then
        assertThat(messageHandlers, containsInAnyOrder(firstMessageHandler, secondMessageHandler));
    }

    @Test
    @DisplayName("Should throw exception if adding a null handler")
    void shouldThrowExceptionIfAddingNullHandler() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler();
        // Then
        assertThrows(NullPointerException.class, () -> sut.addMessageHandler(null));
    }

    @Test
    @DisplayName("Should add a handler")
    void shouldAddAHandler() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler();
        sut.addMessageHandler(firstMessageHandler);
        // When
        Collection<MessageHandler> messageHandlers = sut.getMessageHandlers();
        // Then
        assertEquals(1, messageHandlers.size());
        assertEquals(firstMessageHandler, messageHandlers.iterator().next(), "Does not contain added handler");
    }

    @Test
    @DisplayName("Should throw exception if removing a null handler")
    void shouldThrowExceptionIfRemovingNullHandler() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler();
        // Then
        assertThrows(NullPointerException.class, () -> sut.removeMessageHandler(null));
    }

    @Test
    @DisplayName("Should remove a handler")
    void shouldRemoveAHandler() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        MessageHandler thirdMessageHandler = mock(MessageHandler.class);
        sut.addMessageHandler(thirdMessageHandler);
        sut.removeMessageHandler(secondMessageHandler);
        // When
        Collection<MessageHandler> messageHandlers = sut.getMessageHandlers();
        // Then
        assertThat(messageHandlers, containsInAnyOrder(firstMessageHandler, thirdMessageHandler));
    }

    @Test
    @DisplayName("No-arg constructor should allow adding and removing handlers later without errors")
    void noArgConstructorShouldAllowLateAddAndRemove() {
        CompositeMessageHandler sut = new CompositeMessageHandler();

        assertDoesNotThrow(() -> {
            sut.info("before-add");
            sut.addMessageHandler(firstMessageHandler);
            sut.info("after-first-add");
            sut.addMessageHandler(secondMessageHandler);
            sut.info("after-second-add");
            sut.removeMessageHandler(firstMessageHandler);
            sut.info("after-first-remove");
            sut.removeMessageHandler(secondMessageHandler);
            sut.info("after-second-remove");
            sut.addMessageHandler(firstMessageHandler);
            sut.info("after-readd");
        });

        verify(firstMessageHandler, times(1)).info(eq("after-first-add"));
        verify(firstMessageHandler, times(1)).info(eq("after-second-add"));
        verify(firstMessageHandler, times(1)).info(eq("after-readd"));
        verify(secondMessageHandler, times(1)).info(eq("after-second-add"));
        verify(secondMessageHandler, times(1)).info(eq("after-first-remove"));
        verifyNoMoreInteractions(firstMessageHandler, secondMessageHandler);
    }

    @Test
    @DisplayName("close should close all registered delegates")
    void closeShouldCloseAllRegisteredDelegates() {
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);

        sut.close();

        verify(firstMessageHandler, times(1)).close();
        verify(secondMessageHandler, times(1)).close();
    }

    @Test
    @DisplayName("close should continue when a delegate close fails and report the error")
    void closeShouldContinueWhenADelegateCloseFailsAndReportTheError() {
        RuntimeException boom = new RuntimeException("boom");
        doThrow(boom).when(firstMessageHandler).close();
        List<String> errors = new ArrayList<String>();

        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        sut.setErrorConsumer(errors::add);

        assertDoesNotThrow(sut::close);

        verify(firstMessageHandler, times(1)).close();
        verify(secondMessageHandler, times(1)).close();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("Exception during dispatch"));
    }

    @Test
    @DisplayName("dispatch reporting should fallback to global inner error consumer when local consumer fails")
    void dispatchReportingShouldFallbackToGlobalInnerErrorConsumerWhenLocalConsumerFails() {
        doThrow(new RuntimeException("delegate-boom")).when(firstMessageHandler).info(anyString());
        List<String> trapped = new ArrayList<String>();
        InnerErrorMessageHandler.setGlobalConsumer(trapped::add);
        try {
            CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler);
            sut.setErrorConsumer(message -> {
                throw new RuntimeException("local-consumer-boom");
            });

            assertDoesNotThrow(() -> sut.info("x"));
            assertFalse(trapped.isEmpty());
            assertTrue(trapped.get(0).contains("Exception during dispatch"));
        } finally {
            InnerErrorMessageHandler.resetGlobalConsumer();
        }
    }

    @Test
    @DisplayName("default constructor should use COMPOSITE_ONLY level control mode")
    void defaultConstructorShouldUseCompositeOnlyLevelControlMode() {
        CompositeMessageHandler sut = new CompositeMessageHandler();
        assertEquals(CompositeMessageHandler.LevelControlMode.COMPOSITE_ONLY, sut.getLevelControlMode());
    }

    @Test
    @DisplayName("constructors should reject null level control mode")
    void constructorsShouldRejectNullLevelControlMode() {
        assertThrows(NullPointerException.class,
                () -> new CompositeMessageHandler((CompositeMessageHandler.LevelControlMode) null));
        assertThrows(NullPointerException.class,
                () -> new CompositeMessageHandler(
                        (CompositeMessageHandler.LevelControlMode) null,
                        java.util.Collections.<MessageHandler>emptyList()));
        assertThrows(NullPointerException.class,
                () -> new CompositeMessageHandler(
                        (CompositeMessageHandler.LevelControlMode) null,
                        firstMessageHandler));
    }

    @Test
    @DisplayName("COMPOSITE_ONLY mode should keep delegate level configuration untouched")
    void compositeOnlyModeShouldKeepDelegateLevelConfigurationUntouched() {
        InMemoryMessageHandler delegate = new InMemoryMessageHandler();
        CompositeMessageHandler sut = new CompositeMessageHandler(CompositeMessageHandler.LevelControlMode.COMPOSITE_ONLY);
        sut.setDebugEnabled(true);
        sut.addMessageHandler(delegate);

        assertFalse(delegate.isDebugEnabled());
        sut.debug("dbg");
        assertTrue(delegate.getAllDebugMessages().isEmpty());
    }

    @Test
    @DisplayName("COMPOSITE_ONLY mode should not propagate enable disable and setEnabled")
    void compositeOnlyModeShouldNotPropagateEnableDisableAndSetEnabled() {
        InMemoryMessageHandler delegate = new InMemoryMessageHandler();
        CompositeMessageHandler sut = new CompositeMessageHandler(
                CompositeMessageHandler.LevelControlMode.COMPOSITE_ONLY,
                delegate);

        sut.enable(SeverityNumber.DEBUG);
        sut.disable(SeverityNumber.WARN);
        sut.setEnabled(SeverityNumber.INFO, false);

        assertFalse(delegate.isDebugEnabled());
        assertTrue(delegate.isWarnEnabled());
        assertTrue(delegate.isInfoEnabled());
    }

    @Test
    @DisplayName("PROPAGATE_TO_DELEGATES mode should align and propagate delegate levels")
    void propagateToDelegatesModeShouldAlignAndPropagateDelegateLevels() {
        InMemoryMessageHandler delegate = new InMemoryMessageHandler();
        CompositeMessageHandler sut = new CompositeMessageHandler(CompositeMessageHandler.LevelControlMode.PROPAGATE_TO_DELEGATES);
        sut.setInfoEnabled(false);
        sut.setDebugEnabled(true);
        sut.addMessageHandler(delegate);

        assertFalse(delegate.isInfoEnabled());
        assertTrue(delegate.isDebugEnabled());

        sut.setDebugEnabled(false);
        assertFalse(delegate.isDebugEnabled());

        sut.enable(SeverityNumber.TRACE);
        assertTrue(delegate.isEnabled(SeverityNumber.TRACE));

        sut.disable(SeverityNumber.TRACE);
        assertFalse(delegate.isEnabled(SeverityNumber.TRACE));
    }

    @Test
    @DisplayName("PROPAGATE_TO_DELEGATES constructor should align already configured level-aware delegates")
    void propagateToDelegatesConstructorShouldAlignAlreadyConfiguredLevelAwareDelegates() {
        InMemoryMessageHandler delegate = new InMemoryMessageHandler();
        delegate.setDebugEnabled(true);
        delegate.setTraceEnabled(true);

        CompositeMessageHandler sut = new CompositeMessageHandler(
                CompositeMessageHandler.LevelControlMode.PROPAGATE_TO_DELEGATES,
                delegate);

        assertEquals(1, sut.getHandlerCount());
        assertFalse(delegate.isDebugEnabled());
        assertFalse(delegate.isTraceEnabled());
        assertTrue(delegate.isInfoEnabled());
        assertTrue(delegate.isWarnEnabled());
        assertTrue(delegate.isErrorEnabled());
        assertTrue(delegate.isFatalEnabled());
    }

    @Test
    @DisplayName("PROPAGATE_TO_DELEGATES mode should propagate setEnabled and grouped setters")
    void propagateToDelegatesModeShouldPropagateSetEnabledAndGroupedSetters() {
        InMemoryMessageHandler delegate = new InMemoryMessageHandler();
        CompositeMessageHandler sut = new CompositeMessageHandler(
                CompositeMessageHandler.LevelControlMode.PROPAGATE_TO_DELEGATES,
                delegate);

        sut.setEnabled(SeverityNumber.DEBUG, true);
        assertTrue(delegate.isEnabled(SeverityNumber.DEBUG));

        sut.setInfoEnabled(false);
        sut.setWarnEnabled(false);
        sut.setErrorEnabled(false);
        sut.setFatalEnabled(false);
        sut.setDebugEnabled(false);
        sut.setTraceEnabled(true);

        assertFalse(delegate.isInfoEnabled());
        assertFalse(delegate.isWarnEnabled());
        assertFalse(delegate.isErrorEnabled());
        assertFalse(delegate.isFatalEnabled());
        assertFalse(delegate.isDebugEnabled());
        assertTrue(delegate.isTraceEnabled());
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("PROPAGATE_TO_DELEGATES grouped setters should not expose partial INFO* state snapshots")
    void propagateToDelegatesGroupedSettersShouldNotExposePartialInfoStateSnapshots() throws Exception {
        CompositeMessageHandler sut = new CompositeMessageHandler(CompositeMessageHandler.LevelControlMode.PROPAGATE_TO_DELEGATES);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean inconsistentInfoQuartetObserved = new AtomicBoolean(false);
        final int toggleIterations = 200_000;

        Thread writer = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < toggleIterations; i++) {
                    sut.setInfoEnabled((i & 1) == 0);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "composite-level-toggle-writer");

        Thread reader = new Thread(() -> {
            try {
                start.await();
                while (writer.isAlive() && !inconsistentInfoQuartetObserved.get()) {
                    detectInconsistentInfoQuartetSnapshot(sut, inconsistentInfoQuartetObserved);
                }
                detectInconsistentInfoQuartetSnapshot(sut, inconsistentInfoQuartetObserved);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "composite-level-toggle-reader");

        writer.start();
        reader.start();
        start.countDown();
        writer.join();
        reader.join();

        assertFalse(inconsistentInfoQuartetObserved.get(),
                "Observed partial INFO* group state; grouped setter updates must be atomic");
    }

    @Test
    @DisplayName("PROPAGATE_TO_DELEGATES mode should ignore non-level-aware delegates for level mutations")
    void propagateToDelegatesModeShouldIgnoreNonLevelAwareDelegatesForLevelMutations() {
        MessageHandler nonLevelAwareDelegate = mock(MessageHandler.class);
        CompositeMessageHandler sut = new CompositeMessageHandler(
                CompositeMessageHandler.LevelControlMode.PROPAGATE_TO_DELEGATES,
                nonLevelAwareDelegate);

        assertDoesNotThrow(() -> {
            sut.setInfoEnabled(false);
            sut.setWarnEnabled(false);
            sut.setErrorEnabled(false);
            sut.setFatalEnabled(false);
            sut.setDebugEnabled(true);
            sut.setTraceEnabled(true);
            sut.setEnabled(SeverityNumber.INFO, true);
            sut.enable(SeverityNumber.DEBUG);
            sut.disable(SeverityNumber.DEBUG);
        });

        verifyNoInteractions(nonLevelAwareDelegate);
    }

    private static void detectInconsistentInfoQuartetSnapshot(final CompositeMessageHandler sut,
                                                              final AtomicBoolean inconsistentObserved) {
        SeverityNumber[] enabled = sut.getEnabledLevels();
        boolean info = containsLevel(enabled, SeverityNumber.INFO);
        boolean info2 = containsLevel(enabled, SeverityNumber.INFO2);
        boolean info3 = containsLevel(enabled, SeverityNumber.INFO3);
        boolean info4 = containsLevel(enabled, SeverityNumber.INFO4);
        if (!(info == info2 && info2 == info3 && info3 == info4)) {
            inconsistentObserved.set(true);
        }
    }

    private static boolean containsLevel(final SeverityNumber[] levels, final SeverityNumber level) {
        for (SeverityNumber current : levels) {
            if (current == level) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("DELEGATE_ONLY mode should throw on composite level mutators")
    void delegateOnlyModeShouldThrowOnCompositeLevelMutators() {
        CompositeMessageHandler sut = new CompositeMessageHandler(CompositeMessageHandler.LevelControlMode.DELEGATE_ONLY);

        Map<String, Executable> mutators = new LinkedHashMap<>();
        mutators.put("setInfoEnabled", () -> sut.setInfoEnabled(true));
        mutators.put("setWarnEnabled", () -> sut.setWarnEnabled(true));
        mutators.put("setErrorEnabled", () -> sut.setErrorEnabled(true));
        mutators.put("setFatalEnabled", () -> sut.setFatalEnabled(true));
        mutators.put("setDebugEnabled", () -> sut.setDebugEnabled(true));
        mutators.put("setTraceEnabled", () -> sut.setTraceEnabled(true));
        mutators.put("setEnabled", () -> sut.setEnabled(SeverityNumber.ERROR, false));
        mutators.put("enable", () -> sut.enable(SeverityNumber.DEBUG));
        mutators.put("disable", () -> sut.disable(SeverityNumber.INFO));

        mutators.forEach((operation, invocation) -> {
            UnsupportedOperationException ex = assertThrows(
                    UnsupportedOperationException.class,
                    invocation,
                    operation + " should be unsupported in DELEGATE_ONLY mode");
            assertTrue(ex.getMessage() != null && !ex.getMessage().isEmpty());
            assertTrue(ex.getMessage().contains(operation));
            assertTrue(ex.getMessage().contains("DELEGATE_ONLY"));
        });
    }

    @Test
    @DisplayName("DELEGATE_ONLY mode should route and let delegates decide their own filtering")
    void delegateOnlyModeShouldRouteAndLetDelegatesDecideTheirOwnFiltering() {
        InMemoryMessageHandler debugEnabledDelegate = new InMemoryMessageHandler();
        debugEnabledDelegate.setDebugEnabled(true);
        InMemoryMessageHandler debugDisabledDelegate = new InMemoryMessageHandler();

        CompositeMessageHandler sut = new CompositeMessageHandler(
                CompositeMessageHandler.LevelControlMode.DELEGATE_ONLY,
                debugEnabledDelegate,
                debugDisabledDelegate);

        sut.debug("delegate-mode");

        assertEquals(1, debugEnabledDelegate.getAllDebugMessages().size());
        assertEquals("delegate-mode", debugEnabledDelegate.getAllDebugMessages().get(0));
        assertTrue(debugDisabledDelegate.getAllDebugMessages().isEmpty());
    }

    @ParameterizedTest
    @DisplayName("Should remember if info level is active")
    @CsvSource({"true, true", "false, false"})
    void shouldRememberIfInfoLevelIsActive(boolean isActive, boolean expectedResult) {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        sut.setInfoEnabled(isActive);
        // Then
        assertEquals(expectedResult, sut.isInfoEnabled(), "Info level does not match expected result");
    }

    @Test
    @DisplayName("Should ignore null info message supplier")
    void shouldIgnoreNullInfoMessageSupplier() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        Supplier<String> infoMessageSupplier = null;
        // Then
        assertDoesNotThrow(() -> sut.info(infoMessageSupplier));
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, never()).info(anyString());
        }
    }

    @Test
    @DisplayName("Should ignore null info message")
    void shouldIgnoreNullInfoMessage() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        String infoMessage = null;
        // Then
        assertDoesNotThrow(() -> sut.info(infoMessage));
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, never()).info(anyString());
        }
    }

    @Test
    @DisplayName("Should propagate supplied info messages to all handlers if info level is active")
    void shouldPropagateSuppliedInfoMessagesToAllHandlersIfActive() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        Supplier<String> infoMessageSupplier = () -> FIRST_MESSAGE;
        Supplier<String> secondInfoMessageSupplier = () -> SECOND_MESSAGE;
        // When
        sut.info(infoMessageSupplier);
        sut.info(secondInfoMessageSupplier);
        // Then
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, times(1)).info(eq(FIRST_MESSAGE));
            verify(messageHandler, times(1)).info(eq(SECOND_MESSAGE));
        }
    }

    @Test
    @DisplayName("Should propagate info messages to all handlers if info level is active")
    void shouldPropagateInfoMessagesToAllHandlersIfActive() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        sut.info(FIRST_MESSAGE);
        sut.info(SECOND_MESSAGE);
        // Then
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, times(1)).info(eq(FIRST_MESSAGE));
            verify(messageHandler, times(1)).info(eq(SECOND_MESSAGE));
        }
    }

    @Test
    @DisplayName("Should not propagate info messages to all handlers if info level is not active")
    void shouldNotPropagateInfoMessagesToAllHandlersIfNotActive() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        sut.setInfoEnabled(false);
        sut.info(FIRST_MESSAGE);
        sut.info(SECOND_MESSAGE);
        // Then
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, times(0)).info(eq(FIRST_MESSAGE));
            verify(messageHandler, times(0)).info(eq(SECOND_MESSAGE));
        }
    }

    @Test
    @DisplayName("Supplier should not be evaluated when info level disabled")
    void supplierShouldNotBeEvaluatedWhenInfoLevelDisabled() {
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        sut.setInfoEnabled(false);
        Supplier<String> supplier = mock(Supplier.class);

        sut.info(supplier);

        verifyNoInteractions(supplier);
    }

    @Test
    @DisplayName("Should not hold lock during delegate execution (allows concurrent add)")
    void shouldNotHoldLockDuringDelegateExecution() {
        CompositeMessageHandler sut = new CompositeMessageHandler();
        MessageHandler slowHandler = mock(MessageHandler.class);
        doAnswer(invocation -> {
            // While executing delegate, attempt to add another handler; should not deadlock
            sut.addMessageHandler(firstMessageHandler);
            return null;
        }).when(slowHandler).info(anyString());
        sut.addMessageHandler(slowHandler);

        sut.info("msg");

        verify(slowHandler, times(1)).info(eq("msg"));
        assertEquals(2, sut.getMessageHandlers().size());
    }

    @ParameterizedTest
    @DisplayName("Should remember if warn level is active")
    @CsvSource({"true, true", "false, false"})
    void shouldRememberIfWarnLevelIsActive(boolean isActive, boolean expectedResult) {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        sut.setWarnEnabled(isActive);
        // Then
        assertEquals(expectedResult, sut.isWarnEnabled(), "Warn level does not match expected result");
    }

    @Test
    @DisplayName("Should ignore null warn message supplier")
    void shouldIgnoreNullWarnMessageSupplier() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        Supplier<String> warnMessageSupplier = null;
        // Then
        assertDoesNotThrow(() -> sut.warn(warnMessageSupplier));
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, never()).warn(anyString());
        }
    }

    @Test
    @DisplayName("Should ignore null warn message")
    void shouldIgnoreNullWarnMessage() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        String warnMessage = null;
        // Then
        assertDoesNotThrow(() -> sut.warn(warnMessage));
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, never()).warn(anyString());
        }
    }

    @Test
    @DisplayName("Should propagate supplied warn messages to all handlers if warn level is active")
    void shouldPropagateSuppliedWarnMessagesToAllHandlersIfActive() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        Supplier<String> warnMessageSupplier = () -> FIRST_MESSAGE;
        Supplier<String> secondWarnMessageSupplier = () -> SECOND_MESSAGE;
        // When
        sut.warn(warnMessageSupplier);
        sut.warn(secondWarnMessageSupplier);
        // Then
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, times(1)).warn(eq(FIRST_MESSAGE));
            verify(messageHandler, times(1)).warn(eq(SECOND_MESSAGE));
        }
    }

    @Test
    @DisplayName("Should propagate warn messages to all handlers if warn level is active")
    void shouldPropagateWarnMessagesToAllHandlersIfActive() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        sut.warn(FIRST_MESSAGE);
        sut.warn(SECOND_MESSAGE);
        // Then
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, times(1)).warn(eq(FIRST_MESSAGE));
            verify(messageHandler, times(1)).warn(eq(SECOND_MESSAGE));
        }
    }

    @Test
    @DisplayName("Should not propagate warn messages to all handlers if warn level is not active")
    void shouldNotPropagateWarnMessagesToAllHandlersIfNotActive() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        sut.setWarnEnabled(false);
        sut.warn(FIRST_MESSAGE);
        sut.warn(SECOND_MESSAGE);
        // Then
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, times(0)).warn(eq(FIRST_MESSAGE));
            verify(messageHandler, times(0)).warn(eq(SECOND_MESSAGE));
        }
    }

    @ParameterizedTest
    @DisplayName("Should remember if fatal level is active")
    @CsvSource({"true, true", "false, false"})
    void shouldRememberIfFatalLevelIsActive(boolean isActive, boolean expectedResult) {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        sut.setFatalEnabled(isActive);
        // Then
        assertEquals(expectedResult, sut.isFatalEnabled(), "Fatal level does not match expected result");
    }

    @Test
    @DisplayName("Should ignore null fatal message supplier")
    void shouldIgnoreNullFatalMessageSupplier() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        Supplier<String> fatalMessageSupplier = null;
        // Then
        assertDoesNotThrow(() -> sut.fatal(fatalMessageSupplier));
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, never()).fatal(anyString());
        }
    }

    @Test
    @DisplayName("Should ignore null fatal message")
    void shouldIgnoreNullFatalMessage() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        String fatalMessage = null;
        // Then
        assertDoesNotThrow(() -> sut.fatal(fatalMessage));
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, never()).fatal(anyString());
        }
    }

    @Test
    @DisplayName("Should propagate supplied fatal messages to all handlers if fatal level is active")
    void shouldPropagateSuppliedFatalMessagesToAllHandlersIfActive() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        Supplier<String> fatalMessageSupplier = () -> FIRST_MESSAGE;
        Supplier<String> secondFatalMessageSupplier = () -> SECOND_MESSAGE;
        // When
        sut.fatal(fatalMessageSupplier);
        sut.fatal(secondFatalMessageSupplier);
        // Then
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, times(1)).fatal(eq(FIRST_MESSAGE));
            verify(messageHandler, times(1)).fatal(eq(SECOND_MESSAGE));
        }
    }

    @Test
    @DisplayName("Should propagate fatal messages to all handlers if fatal level is active")
    void shouldPropagateFatalMessagesToAllHandlersIfActive() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        sut.fatal(FIRST_MESSAGE);
        sut.fatal(SECOND_MESSAGE);
        // Then
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, times(1)).fatal(eq(FIRST_MESSAGE));
            verify(messageHandler, times(1)).fatal(eq(SECOND_MESSAGE));
        }
    }

    @Test
    @DisplayName("Should not propagate fatal messages to all handlers if fatal level is not active")
    void shouldNotPropagateFatalMessagesToAllHandlersIfNotActive() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        sut.setFatalEnabled(false);
        sut.fatal(FIRST_MESSAGE);
        sut.fatal(SECOND_MESSAGE);
        // Then
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, times(0)).fatal(eq(FIRST_MESSAGE));
            verify(messageHandler, times(0)).fatal(eq(SECOND_MESSAGE));
        }
    }

    @Test
    @DisplayName("Supplier should not be evaluated when fatal level disabled")
    void supplierShouldNotBeEvaluatedWhenFatalLevelDisabled() {
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        sut.setFatalEnabled(false);
        Supplier<String> supplier = mock(Supplier.class);

        sut.fatal(supplier);

        verifyNoInteractions(supplier);
    }

    @ParameterizedTest
    @DisplayName("Should remember if error level is active")
    @CsvSource({"true, true", "false, false"})
    void shouldRememberIfErrorLevelIsActive(boolean isActive, boolean expectedResult) {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        sut.setErrorEnabled(isActive);
        // Then
        assertEquals(expectedResult, sut.isErrorEnabled(), "Error level does not match expected result");
    }

    @Test
    @DisplayName("Should ignore null error message supplier")
    void shouldIgnoreNullErrorMessageSupplier() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        Supplier<String> errorMessageSupplier = null;
        // Then
        assertDoesNotThrow(() -> sut.error(errorMessageSupplier));
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, never()).error(anyString());
        }
    }

    @Test
    @DisplayName("Should ignore null error message")
    void shouldIgnoreNullErrorMessage() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        String errorMessage = null;
        // Then
        assertDoesNotThrow(() -> sut.error(errorMessage));
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, never()).error(anyString());
        }
    }

    @Test
    @DisplayName("Should propagate supplied error messages to all handlers if error level is active")
    void shouldPropagateSuppliedErrorMessagesToAllHandlersIfActive() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        Supplier<String> errorMessageSupplier = () -> FIRST_MESSAGE;
        Supplier<String> secondErrorMessageSupplier = () -> SECOND_MESSAGE;
        // When
        sut.error(errorMessageSupplier);
        sut.error(secondErrorMessageSupplier);
        // Then
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, times(1)).error(eq(FIRST_MESSAGE));
            verify(messageHandler, times(1)).error(eq(SECOND_MESSAGE));
        }
    }

    @Test
    @DisplayName("Should propagate error messages to all handlers if error level is active")
    void shouldPropagateErrorMessagesToAllHandlersIfActive() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        sut.error(FIRST_MESSAGE);
        sut.error(SECOND_MESSAGE);
        // Then
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, times(1)).error(eq(FIRST_MESSAGE));
            verify(messageHandler, times(1)).error(eq(SECOND_MESSAGE));
        }
    }

    @Test
    @DisplayName("Should not propagate error messages to all handlers if error level is not active")
    void shouldNotPropagateErrorMessagesToAllHandlersIfNotActive() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        sut.setErrorEnabled(false);
        sut.error(FIRST_MESSAGE);
        sut.error(SECOND_MESSAGE);
        // Then
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, times(0)).error(eq(FIRST_MESSAGE));
            verify(messageHandler, times(0)).error(eq(SECOND_MESSAGE));
        }
    }

    @ParameterizedTest
    @DisplayName("Should remember if debug level is active")
    @CsvSource({"true, true", "false, false"})
    void shouldRememberIfDebugLevelIsActive(boolean isActive, boolean expectedResult) {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        sut.setDebugEnabled(isActive);
        // Then
        assertEquals(expectedResult, sut.isDebugEnabled(), "Debug level does not match expected result");
    }

    @Test
    @DisplayName("Should ignore null debug message supplier")
    void shouldIgnoreNullDebugMessageSupplier() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        sut.setDebugEnabled(true);
        // When
        Supplier<String> debugMessageSupplier = null;
        // Then
        assertDoesNotThrow(() -> sut.debug(debugMessageSupplier));
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, never()).debug(anyString());
        }
    }

    @Test
    @DisplayName("Should ignore null debug message")
    void shouldIgnoreNullDebugMessage() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        sut.setDebugEnabled(true);
        // When
        String debugMessage = null;
        // Then
        assertDoesNotThrow(() -> sut.debug(debugMessage));
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, never()).debug(anyString());
        }
    }

    @Test
    @DisplayName("Should propagate supplied debug messages to all handlers if debug level is active")
    void shouldPropagateSuppliedDebugMessagesToAllHandlersIfActive() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        sut.setDebugEnabled(true);
        Supplier<String> debugMessageSupplier = () -> FIRST_MESSAGE;
        Supplier<String> secondDebugMessageSupplier = () -> SECOND_MESSAGE;
        // When
        sut.debug(debugMessageSupplier);
        sut.debug(secondDebugMessageSupplier);
        // Then
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, times(1)).debug(eq(FIRST_MESSAGE));
            verify(messageHandler, times(1)).debug(eq(SECOND_MESSAGE));
        }
    }

    @Test
    @DisplayName("Should propagate debug messages to all handlers if debug level is active")
    void shouldPropagateDebugMessagesToAllHandlersIfActive() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        sut.setDebugEnabled(true);
        // When
        sut.debug(FIRST_MESSAGE);
        sut.debug(SECOND_MESSAGE);
        // Then
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, times(1)).debug(eq(FIRST_MESSAGE));
            verify(messageHandler, times(1)).debug(eq(SECOND_MESSAGE));
        }
    }

    @Test
    @DisplayName("Should not propagate debug messages to all handlers if debug level is not active")
    void shouldNotPropagateDebugMessagesToAllHandlersIfNotActive() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        sut.setDebugEnabled(false);
        sut.debug(FIRST_MESSAGE);
        sut.debug(SECOND_MESSAGE);
        // Then
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, times(0)).debug(eq(FIRST_MESSAGE));
            verify(messageHandler, times(0)).debug(eq(SECOND_MESSAGE));
        }
    }

    @ParameterizedTest
    @DisplayName("Should remember if trace level is active")
    @CsvSource({"true, true", "false, false"})
    void shouldRememberIfTraceLevelIsActive(boolean isActive, boolean expectedResult) {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        sut.setTraceEnabled(isActive);
        // Then
        assertEquals(expectedResult, sut.isTraceEnabled(), "Trace level does not match expected result");
    }

    @Test
    @DisplayName("Should ignore null trace message supplier")
    void shouldIgnoreNullTraceMessageSupplier() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        sut.setTraceEnabled(true);
        // When
        Supplier<String> traceMessageSupplier = null;
        // Then
        assertDoesNotThrow(() -> sut.trace(traceMessageSupplier));
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, never()).trace(anyString());
        }
    }

    @Test
    @DisplayName("Should ignore null trace message")
    void shouldIgnoreNullTraceMessage() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        sut.setTraceEnabled(true);
        // When
        String traceMessage = null;
        // Then
        assertDoesNotThrow(() -> sut.trace(traceMessage));
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, never()).trace(anyString());
        }
    }

    @Test
    @DisplayName("Should propagate supplied trace messages to all handlers if trace level is active")
    void shouldPropagateSuppliedTraceMessagesToAllHandlersIfActive() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        sut.setTraceEnabled(true);
        Supplier<String> traceMessageSupplier = () -> FIRST_MESSAGE;
        Supplier<String> secondTraceMessageSupplier = () -> SECOND_MESSAGE;
        // When
        sut.trace(traceMessageSupplier);
        sut.trace(secondTraceMessageSupplier);
        // Then
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, times(1)).trace(eq(FIRST_MESSAGE));
            verify(messageHandler, times(1)).trace(eq(SECOND_MESSAGE));
        }
    }

    @Test
    @DisplayName("Should propagate trace messages to all handlers if trace level is active")
    void shouldPropagateTraceMessagesToAllHandlersIfActive() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        sut.setTraceEnabled(true);
        // When
        sut.trace(FIRST_MESSAGE);
        sut.trace(SECOND_MESSAGE);
        // Then
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, times(1)).trace(eq(FIRST_MESSAGE));
            verify(messageHandler, times(1)).trace(eq(SECOND_MESSAGE));
        }
    }

    @Test
    @DisplayName("Should not propagate trace messages to all handlers if trace level is not active")
    void shouldNotPropagateTraceMessagesToAllHandlersIfNotActive() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        sut.setTraceEnabled(false);
        sut.trace(FIRST_MESSAGE);
        sut.trace(SECOND_MESSAGE);
        // Then
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, times(0)).trace(eq(FIRST_MESSAGE));
            verify(messageHandler, times(0)).trace(eq(SECOND_MESSAGE));
        }
    }

    @Test
    @DisplayName("Should ignore a null exception")
    void shouldIgnoreANullException() {
        // Given
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        Exception exception = null;
        // Then
        assertDoesNotThrow(() -> sut.exception(exception));
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, never()).exception(any(Throwable.class));
        }
    }

    @Test
    @DisplayName("Should propagate exceptions to all handlers if exception level is active")
    void shouldPropagateExceptionsToAllHandlersIfActive() {
        // Given
        IllegalArgumentException illegalArgumentException = new IllegalArgumentException("My IllegalArgumentException");
        ClassCastException classCastException = new ClassCastException("My ClassCastException");
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        // When
        sut.exception(illegalArgumentException);
        sut.exception(classCastException);
        // Then
        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, times(1)).exception(eq(illegalArgumentException));
            verify(messageHandler, times(1)).exception(eq(classCastException));
        }
    }

    @Test
    @DisplayName("Should propagate exception with message to all handlers if exception level is active")
    void shouldPropagateExceptionWithMessageToAllHandlersIfActive() {
        IllegalArgumentException exception = new IllegalArgumentException("My IllegalArgumentException");
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);

        sut.exception("prefix", exception);

        for (MessageHandler messageHandler : sut.getMessageHandlers()) {
            verify(messageHandler, times(1)).exception(eq("prefix"), eq(exception));
        }
    }

    @Test
    @DisplayName("exception should treat empty message as plain exception")
    void exceptionShouldTreatEmptyMessageAsPlainException() {
        IllegalArgumentException exception = new IllegalArgumentException("boom");
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);

        sut.exception("", exception);

        verify(firstMessageHandler, times(1)).exception(eq(exception));
        verify(secondMessageHandler, times(1)).exception(eq(exception));
        verify(firstMessageHandler, never()).exception(eq(""), eq(exception));
        verify(secondMessageHandler, never()).exception(eq(""), eq(exception));
    }

    @Test
    @DisplayName("exception should treat message equal to throwable detail as plain exception")
    void exceptionShouldTreatDetailMessageAsPlainException() {
        RuntimeException exception = new RuntimeException((String) null);
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);

        sut.exception(exception.toString(), exception);

        verify(firstMessageHandler, times(1)).exception(eq(exception));
        verify(secondMessageHandler, times(1)).exception(eq(exception));
        verify(firstMessageHandler, never()).exception(eq(exception.toString()), eq(exception));
        verify(secondMessageHandler, never()).exception(eq(exception.toString()), eq(exception));
    }

    @Test
    @DisplayName("forEachHandler should catch Throwable from a child handler and continue to subsequent handlers")
    void forEachHandlerShouldCatchThrowableAndContinueToSubsequentHandlers() {
        List<String> errors = new ArrayList<>();
        doThrow(new Error("forced error")).when(firstMessageHandler).info(eq("msg"));
        CompositeMessageHandler sut = new CompositeMessageHandler(firstMessageHandler, secondMessageHandler);
        sut.setErrorConsumer(errors::add);

        sut.info("msg");

        verify(secondMessageHandler, times(1)).info(eq("msg"));
        assertFalse(errors.isEmpty(), "errorConsumer should have received the dispatch error");
        assertTrue(errors.get(0).contains("forced error"), "Error notification should include the throwable message");
    }

    @Test
    @DisplayName("setErrorConsumer should throw NullPointerException for null argument")
    void setErrorConsumerNullThrowsNpe() {
        CompositeMessageHandler sut = new CompositeMessageHandler();
        assertThrows(NullPointerException.class, () -> sut.setErrorConsumer(null));
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    @DisplayName("Should fan out info messages under very high concurrent load")
    void shouldFanOutInfoMessagesUnderVeryHighConcurrentLoad() throws Exception {
        final int threadCount = 32;
        final int messagesPerThread = 1500;
        final int expectedMessages = threadCount * messagesPerThread;

        InMemoryMessageHandler sink = new InMemoryMessageHandler(expectedMessages + 10);
        CompositeMessageHandler sut = new CompositeMessageHandler(sink);

        ExecutorService producerPool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(producerPool.submit(() -> {
                start.await();
                for (int i = 0; i < messagesPerThread; i++) {
                    int messageIndex = i;
                    sut.info(() -> "stress-composite-" + threadId + "-" + messageIndex);
                }
                return null;
            }));
        }

        start.countDown();
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        producerPool.shutdown();
        assertTrue(producerPool.awaitTermination(10, TimeUnit.SECONDS));

        List<String> allInfoMessages = sink.getAllInfoMessages();
        assertEquals(expectedMessages, allInfoMessages.size(), "Some composite info messages were lost under stress");
        assertEquals(expectedMessages, new HashSet<>(allInfoMessages).size(), "Expected unique stress messages");
    }
}
