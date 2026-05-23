package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
/**
 * An implementation of the {@link MessageHandler} interface that forwards messages and exceptions
 * to one or more other {@link MessageHandler}s.
 * <p>
 * Level control behavior is configurable via {@link LevelControlMode}:
 * <ul>
 *   <li>{@link LevelControlMode#COMPOSITE_ONLY}: this composite applies its own level gate only;
 *       delegates keep their own level configuration unchanged.</li>
 *   <li>{@link LevelControlMode#PROPAGATE_TO_DELEGATES}: this composite applies its own level gate
 *       and forwards level changes to delegates that are {@link AbstractMessageHandler} instances.
 *       Newly added delegates are aligned with the composite level state.</li>
 *   <li>{@link LevelControlMode#DELEGATE_ONLY}: this composite does not own level mutators.
 *       Calls to level-mutating APIs throw {@link UnsupportedOperationException}; routing is always
 *       forwarded and delegates decide their own filtering.</li>
 * </ul>
 * <p>
 * Default mode is {@link LevelControlMode#COMPOSITE_ONLY}.
 *
 * @author Stefano Reksten
 */
public class CompositeMessageHandler extends AbstractMessageHandler {
    /**
     * Defines how level state is managed between the composite and its delegates.
     */
    public enum LevelControlMode {
        /**
         * The composite keeps its own level state and does not change delegate level state.
         */
        COMPOSITE_ONLY,
        /**
         * The composite keeps its own level state and propagates mutations to level-aware delegates.
         */
        PROPAGATE_TO_DELEGATES,
        /**
         * Level state is managed only on delegates; composite-level mutators are unsupported.
         */
        DELEGATE_ONLY
    }

    private final List<MessageHandler> messageHandlers = new ArrayList<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile Consumer<String> errorConsumer = System.err::println;
    private final LevelControlMode levelControlMode;

    public CompositeMessageHandler() {
        this(LevelControlMode.COMPOSITE_ONLY, new ArrayList<>());
    }
    /**
     * Creates an empty composite with the provided level control mode.
     *
     * @param levelControlMode mode controlling how level state is managed
     */
    public CompositeMessageHandler(final @Nonnull LevelControlMode levelControlMode) {
        this(levelControlMode, new ArrayList<>());
    }
    /**
     * @param handlers a collection of non-null MessageHandlers
     */
    public CompositeMessageHandler(final @Nonnull Collection<MessageHandler> handlers) {
        this(LevelControlMode.COMPOSITE_ONLY, handlers);
    }
    /**
     * Creates a composite in the provided level control mode, preloaded with handlers.
     *
     * @param levelControlMode mode controlling how level state is managed
     * @param handlers a collection of non-null MessageHandlers
     */
    public CompositeMessageHandler(final @Nonnull LevelControlMode levelControlMode,
                                   final @Nonnull Collection<MessageHandler> handlers) {
        this.levelControlMode = Objects.requireNonNull(
                levelControlMode,
                MessageHandlerResourceBundle.get("nullCompositeLevelControlModeProvided"));
        Objects.requireNonNull(handlers, MessageHandlerResourceBundle.get("noMessageHandlersProvided"));
        addMessageHandlers(handlers);
    }
    /**
     * @param handlers a collection of non-null MessageHandlers
     */
    public CompositeMessageHandler(final @Nonnull MessageHandler... handlers) {
        this(LevelControlMode.COMPOSITE_ONLY, handlers);
    }
    /**
     * Creates a composite in the provided level control mode, preloaded with handlers.
     *
     * @param levelControlMode mode controlling how level state is managed
     * @param handlers a collection of non-null MessageHandlers
     */
    public CompositeMessageHandler(final @Nonnull LevelControlMode levelControlMode,
                                   final @Nonnull MessageHandler... handlers) {
        this.levelControlMode = Objects.requireNonNull(
                levelControlMode,
                MessageHandlerResourceBundle.get("nullCompositeLevelControlModeProvided"));
        Objects.requireNonNull(handlers, MessageHandlerResourceBundle.get("noMessageHandlersProvided"));
        addMessageHandlers(Arrays.asList(handlers));
    }

    private void addMessageHandlers(@Nonnull Collection<MessageHandler> handlers) {
        lock.writeLock().lock();
        try {
            for (MessageHandler handler : handlers) {
                MessageHandler nonNullHandler = Objects.requireNonNull(
                        handler,
                        MessageHandlerResourceBundle.get("nullMessageHandlerProvided"));
                if (levelControlMode == LevelControlMode.PROPAGATE_TO_DELEGATES) {
                    alignDelegateLevels(nonNullHandler, getEnabledLevels(), getDisabledLevels());
                }
                messageHandlers.add(nonNullHandler);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    /**
     * Adds a single {@link MessageHandler} to this composite at runtime.
     * <p>
     * The operation acquires the write lock, so it is safe to call from any thread even while
     * messages are being dispatched concurrently.
     *
     * @param messageHandler a non-null {@link MessageHandler} to add
     * @throws NullPointerException if {@code messageHandler} is {@code null}
     */
    public void addMessageHandler(final @Nonnull MessageHandler messageHandler) {
        Objects.requireNonNull(messageHandler, MessageHandlerResourceBundle.get("nullMessageHandlerProvided"));
        lock.writeLock().lock();
        try {
            if (levelControlMode == LevelControlMode.PROPAGATE_TO_DELEGATES) {
                alignDelegateLevels(messageHandler, getEnabledLevels(), getDisabledLevels());
            }
            messageHandlers.add(messageHandler);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Evaluates if a level is enabled for dispatch.
     * <p>
     * In {@link LevelControlMode#DELEGATE_ONLY} this method always returns {@code true}
     * (after null validation) so that delegates perform the filtering.
     */
    @Override
    public boolean isEnabled(final @Nonnull SeverityNumber level) {
        if (levelControlMode == LevelControlMode.DELEGATE_ONLY) {
            Objects.requireNonNull(level, MessageHandlerResourceBundle.get("nullLevelProvided"));
            return true;
        }
        return super.isEnabled(level);
    }

    /**
     * Enables the provided levels on this composite.
     * <p>
     * In {@link LevelControlMode#PROPAGATE_TO_DELEGATES}, changes are also forwarded to
     * level-aware delegates.
     *
     * @throws UnsupportedOperationException when mode is {@link LevelControlMode#DELEGATE_ONLY}
     */
    @Override
    public void enable(final SeverityNumber... levels) {
        assertLevelMutatorAllowed("enable");
        super.enable(levels);
        if (levelControlMode == LevelControlMode.PROPAGATE_TO_DELEGATES) {
            forEachLevelAwareDelegate(delegate -> delegate.enable(levels));
        }
    }

    /**
     * Disables the provided levels on this composite.
     * <p>
     * In {@link LevelControlMode#PROPAGATE_TO_DELEGATES}, changes are also forwarded to
     * level-aware delegates.
     *
     * @throws UnsupportedOperationException when mode is {@link LevelControlMode#DELEGATE_ONLY}
     */
    @Override
    public void disable(final SeverityNumber... levels) {
        assertLevelMutatorAllowed("disable");
        super.disable(levels);
        if (levelControlMode == LevelControlMode.PROPAGATE_TO_DELEGATES) {
            forEachLevelAwareDelegate(delegate -> delegate.disable(levels));
        }
    }

    /**
     * Enables or disables a single level on this composite.
     * <p>
     * In {@link LevelControlMode#PROPAGATE_TO_DELEGATES}, changes are also forwarded to
     * level-aware delegates.
     *
     * @throws UnsupportedOperationException when mode is {@link LevelControlMode#DELEGATE_ONLY}
     */
    @Override
    public void setEnabled(final @Nonnull SeverityNumber level, final boolean enabled) {
        assertLevelMutatorAllowed("setEnabled");
        super.setEnabled(level, enabled);
        if (levelControlMode == LevelControlMode.PROPAGATE_TO_DELEGATES) {
            forEachLevelAwareDelegate(delegate -> delegate.setEnabled(level, enabled));
        }
    }

    /**
     * Enables or disables all {@code INFO*} levels on this composite.
     * <p>
     * Composite-level state is updated as one atomic grouped operation.
     * <p>
     * In {@link LevelControlMode#PROPAGATE_TO_DELEGATES}, changes are also forwarded to
     * level-aware delegates.
     *
     * @throws UnsupportedOperationException when mode is {@link LevelControlMode#DELEGATE_ONLY}
     */
    @Override
    public void setInfoEnabled(final boolean infoEnabled) {
        assertLevelMutatorAllowed("setInfoEnabled");
        super.setInfoEnabled(infoEnabled);
        if (levelControlMode == LevelControlMode.PROPAGATE_TO_DELEGATES) {
            forEachLevelAwareDelegate(delegate -> delegate.setInfoEnabled(infoEnabled));
        }
    }

    /**
     * Enables or disables all {@code WARN*} levels on this composite.
     * <p>
     * Composite-level state is updated as one atomic grouped operation.
     * <p>
     * In {@link LevelControlMode#PROPAGATE_TO_DELEGATES}, changes are also forwarded to
     * level-aware delegates.
     *
     * @throws UnsupportedOperationException when mode is {@link LevelControlMode#DELEGATE_ONLY}
     */
    @Override
    public void setWarnEnabled(final boolean warnEnabled) {
        assertLevelMutatorAllowed("setWarnEnabled");
        super.setWarnEnabled(warnEnabled);
        if (levelControlMode == LevelControlMode.PROPAGATE_TO_DELEGATES) {
            forEachLevelAwareDelegate(delegate -> delegate.setWarnEnabled(warnEnabled));
        }
    }

    /**
     * Enables or disables all {@code ERROR*} levels on this composite.
     * <p>
     * Composite-level state is updated as one atomic grouped operation.
     * <p>
     * In {@link LevelControlMode#PROPAGATE_TO_DELEGATES}, changes are also forwarded to
     * level-aware delegates.
     *
     * @throws UnsupportedOperationException when mode is {@link LevelControlMode#DELEGATE_ONLY}
     */
    @Override
    public void setErrorEnabled(final boolean errorEnabled) {
        assertLevelMutatorAllowed("setErrorEnabled");
        super.setErrorEnabled(errorEnabled);
        if (levelControlMode == LevelControlMode.PROPAGATE_TO_DELEGATES) {
            forEachLevelAwareDelegate(delegate -> delegate.setErrorEnabled(errorEnabled));
        }
    }

    /**
     * Enables or disables all {@code FATAL*} levels on this composite.
     * <p>
     * Composite-level state is updated as one atomic grouped operation.
     * <p>
     * In {@link LevelControlMode#PROPAGATE_TO_DELEGATES}, changes are also forwarded to
     * level-aware delegates.
     *
     * @throws UnsupportedOperationException when mode is {@link LevelControlMode#DELEGATE_ONLY}
     */
    @Override
    public void setFatalEnabled(final boolean fatalEnabled) {
        assertLevelMutatorAllowed("setFatalEnabled");
        super.setFatalEnabled(fatalEnabled);
        if (levelControlMode == LevelControlMode.PROPAGATE_TO_DELEGATES) {
            forEachLevelAwareDelegate(delegate -> delegate.setFatalEnabled(fatalEnabled));
        }
    }

    /**
     * Enables or disables all {@code DEBUG*} levels on this composite.
     * <p>
     * Composite-level state is updated as one atomic grouped operation.
     * <p>
     * In {@link LevelControlMode#PROPAGATE_TO_DELEGATES}, changes are also forwarded to
     * level-aware delegates.
     *
     * @throws UnsupportedOperationException when mode is {@link LevelControlMode#DELEGATE_ONLY}
     */
    @Override
    public void setDebugEnabled(final boolean debugEnabled) {
        assertLevelMutatorAllowed("setDebugEnabled");
        super.setDebugEnabled(debugEnabled);
        if (levelControlMode == LevelControlMode.PROPAGATE_TO_DELEGATES) {
            forEachLevelAwareDelegate(delegate -> delegate.setDebugEnabled(debugEnabled));
        }
    }

    /**
     * Enables or disables all {@code TRACE*} levels on this composite.
     * <p>
     * Composite-level state is updated as one atomic grouped operation.
     * <p>
     * In {@link LevelControlMode#PROPAGATE_TO_DELEGATES}, changes are also forwarded to
     * level-aware delegates.
     *
     * @throws UnsupportedOperationException when mode is {@link LevelControlMode#DELEGATE_ONLY}
     */
    @Override
    public void setTraceEnabled(final boolean traceEnabled) {
        assertLevelMutatorAllowed("setTraceEnabled");
        super.setTraceEnabled(traceEnabled);
        if (levelControlMode == LevelControlMode.PROPAGATE_TO_DELEGATES) {
            forEachLevelAwareDelegate(delegate -> delegate.setTraceEnabled(traceEnabled));
        }
    }
    /**
     * Removes a previously registered {@link MessageHandler} from this composite.
     * <p>
     * If the handler is not currently registered, the call is silently ignored.
     * The operation acquires the write lock, so it is safe to call from any thread even while
     * messages are being dispatched concurrently.
     *
     * @param messageHandler a non-null {@link MessageHandler} to remove
     * @throws NullPointerException if {@code messageHandler} is {@code null}
     */
    public void removeMessageHandler(final @Nonnull MessageHandler messageHandler) {
        Objects.requireNonNull(messageHandler, MessageHandlerResourceBundle.get("nullMessageHandlerProvided"));
        lock.writeLock().lock();
        try {
            messageHandlers.remove(messageHandler);
        } finally {
            lock.writeLock().unlock();
        }
    }
    /**
     * Returns the number of {@link MessageHandler}s currently registered in this composite.
     * <p>
     * The count is read under the read lock and is consistent with a point-in-time snapshot;
     * concurrent {@link #addMessageHandler} or {@link #removeMessageHandler} calls may change
     * it immediately after this method returns.
     *
     * @return the current number of registered handlers; {@code 0} if none are registered
     */
    public int getHandlerCount() {
        lock.readLock().lock();
        try {
            return messageHandlers.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    /**
     * Returns a point-in-time snapshot of the registered handlers as an unmodifiable collection.
     * <p>
     * The returned collection reflects the state at the time of the call; subsequent
     * {@link #addMessageHandler} or {@link #removeMessageHandler} calls do not affect it.
     *
     * @return an unmodifiable, ordered collection of the currently registered {@link MessageHandler}s
     */
    public Collection<MessageHandler> getMessageHandlers() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(messageHandlers));
        } finally {
            lock.readLock().unlock();
        }
    }
    /**
     * @return the configured level-control mode of this composite.
     */
    public LevelControlMode getLevelControlMode() {
        return levelControlMode;
    }

    /**
     * Sets the consumer that receives dispatch-error notifications.
     * <p>
     * The consumer is invoked with a single string containing the formatted error message and the
     * full stack trace of the {@link Throwable} thrown by the child handler, separated by a
     * line separator. Defaults to {@code System.err::println}.
     *
     * @param errorConsumer the non-null error notification consumer
     * @throws NullPointerException if {@code errorConsumer} is {@code null}
     */
    public void setErrorConsumer(@Nonnull final Consumer<String> errorConsumer) {
        Objects.requireNonNull(errorConsumer, MessageHandlerResourceBundle.get("nullErrorConsumerProvided"));
        this.errorConsumer = errorConsumer;
    }

    /**
     * Closes all currently registered delegates.
     * <p>
     * The delegate list is snapshotted under the read lock, then each delegate is closed
     * independently. Failures from one delegate are reported through {@code errorConsumer}
     * and do not prevent closing subsequent delegates.
     */
    @Override
    public void close() {
        forEachHandler(handler -> {
            if (handler != this) {
                handler.close();
            }
        });
    }

    @Override
    public void handleMessage(@Nonnull SeverityNumber level, @Nonnull String message) {
        forEachHandler(mh -> {
            switch (level) {
                case WARN:
                case WARN2:
                case WARN3:
                case WARN4:
                    mh.warn(message);
                    break;
                case ERROR:
                case ERROR2:
                case ERROR3:
                case ERROR4:
                    mh.error(message);
                    break;
                case FATAL:
                case FATAL2:
                case FATAL3:
                case FATAL4:
                    mh.fatal(message);
                    break;
                case DEBUG:
                case DEBUG2:
                case DEBUG3:
                case DEBUG4:
                    mh.debug(message);
                    break;
                case TRACE:
                case TRACE2:
                case TRACE3:
                case TRACE4:
                    mh.trace(message);
                    break;
                default:
                    mh.info(message);
                    break;
            }
        });
    }

    @Override
    protected void handleExceptionInternal(@Nonnull String message, @Nonnull Throwable throwable) {
        forEachHandler(mh -> {
            String detail = ThrowableMessageFormatter.detail(throwable);
            if (message.isEmpty() || message.equals(throwable.getMessage()) || message.equals(detail)) {
                mh.exception(throwable);
            } else {
                mh.exception(message, throwable);
            }
        });
    }

    private void forEachHandler(Consumer<MessageHandler> consumer) {
        List<MessageHandler> snapshot;
        lock.readLock().lock();
        try {
            snapshot = new ArrayList<>(messageHandlers);
        } finally {
            lock.readLock().unlock();
        }
        for (MessageHandler handler : snapshot) {
            try {
                consumer.accept(handler);
            } catch (Throwable t) {
                String msg = String.format(
                        MessageHandlerResourceBundle.get("exceptionDuringDispatch"), handler);
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw, true));
                errorConsumer.accept(msg + System.lineSeparator() + sw);
            }
        }
    }

    private void assertLevelMutatorAllowed(final String operation) {
        if (levelControlMode == LevelControlMode.DELEGATE_ONLY) {
            throw new UnsupportedOperationException(MessageHandlerResourceBundle.format(
                    "compositeLevelMutationUnsupportedForMode",
                    operation,
                    levelControlMode));
        }
    }

    private void forEachLevelAwareDelegate(final Consumer<AbstractMessageHandler> consumer) {
        forEachHandler(handler -> {
            if (handler instanceof AbstractMessageHandler) {
                consumer.accept((AbstractMessageHandler) handler);
            }
        });
    }

    private static void alignDelegateLevels(final MessageHandler delegate,
                                            final SeverityNumber[] enabledLevels,
                                            final SeverityNumber[] disabledLevels) {
        if (delegate instanceof AbstractMessageHandler) {
            AbstractMessageHandler levelAwareHandler = (AbstractMessageHandler) delegate;
            levelAwareHandler.enable(enabledLevels);
            levelAwareHandler.disable(disabledLevels);
        }
    }

}
