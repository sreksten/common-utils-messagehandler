package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.annotation.Nonnull;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * An implementation of the {@link MessageHandler} interface that forwards
 * messages and exceptions to one or more other MessageHandlers. Note that if you disable a certain message level from
 * here, no messages will be forwarded to any MessageHandler registered. The best option is to disable levels in the
 * handlers. In this way you can, for example, forward only error messages to a file, and all other messages to the
 * console.
 *
 * @author Stefano Reksten
 */
public class CompositeMessageHandler extends AbstractMessageHandler {

    private final List<MessageHandler> messageHandlers = new ArrayList<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public CompositeMessageHandler() {
        this(new ArrayList<>());
    }

    /**
     * @param handlers a collection of non-null MessageHandlers
     */
    public CompositeMessageHandler(final @Nonnull Collection<MessageHandler> handlers) {
        Objects.requireNonNull(handlers, MessageHandlerResourceBundle.BUNDLE.getString("noMessageHandlersProvided"));
        addMessageHandlers(handlers);
    }

    /**
     * @param handlers a collection of non-null MessageHandlers
     */
    public CompositeMessageHandler(final @Nonnull MessageHandler... handlers) {
        Objects.requireNonNull(handlers, MessageHandlerResourceBundle.BUNDLE.getString("noMessageHandlersProvided"));
        addMessageHandlers(Arrays.asList(handlers));
    }

    private void addMessageHandlers(@Nonnull Collection<MessageHandler> handlers) {
        lock.writeLock().lock();
        try {
            for (MessageHandler handler : handlers) {
                messageHandlers.add(Objects.requireNonNull(handler, MessageHandlerResourceBundle.BUNDLE.getString("nullMessageHandlerProvided")));
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
        Objects.requireNonNull(messageHandler, MessageHandlerResourceBundle.BUNDLE.getString("nullMessageHandlerProvided"));
        lock.writeLock().lock();
        try {
            messageHandlers.add(messageHandler);
        } finally {
            lock.writeLock().unlock();
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
        Objects.requireNonNull(messageHandler, MessageHandlerResourceBundle.BUNDLE.getString("nullMessageHandlerProvided"));
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

    @Override
    protected void handleInfoMessageImpl(final String message) {
        forEachHandler(mh -> mh.handleInfoMessage(message));
    }

    @Override
    protected void handleWarnMessageImpl(final String message) {
        forEachHandler(mh -> mh.handleWarnMessage(message));
    }

    @Override
    protected void handleErrorMessageImpl(final String message) {
        forEachHandler(mh -> mh.handleErrorMessage(message));
    }

    @Override
    protected void handleDebugMessageImpl(final String message) {
        forEachHandler(mh -> mh.handleDebugMessage(message));
    }

    @Override
    protected void handleTraceMessageImpl(final String message) {
        forEachHandler(mh -> mh.handleTraceMessage(message));
    }

    @Override
    protected void handleExceptionImpl(final Exception exception) {
        forEachHandler(mh -> mh.handleException(exception));
    }

    @Override
    protected void handleExceptionImpl(final String message, final Exception exception) {
        forEachHandler(mh -> mh.handleException(message, exception));
    }

    private void forEachHandler(java.util.function.Consumer<MessageHandler> consumer) {
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
                String msg = String.format(MessageHandlerResourceBundle.BUNDLE.getString("exceptionDuringDispatch"), handler);
                System.err.println(msg);
                t.printStackTrace(System.err);
            }
        }
    }
}
