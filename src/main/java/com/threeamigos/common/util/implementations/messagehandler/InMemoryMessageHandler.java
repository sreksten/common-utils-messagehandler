package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * An implementation of the {@link MessageHandler} interface that stores
 * messages and exceptions to an internal list for later processing (e.g.,
 * to conduct unit testing).
 *
 * @author Stefano Reksten
 */
public class InMemoryMessageHandler extends AbstractMessageHandler {

    private final int maxEntries;

    private final ArrayDeque<Holder> allMessages = new ArrayDeque<>();
    private final ArrayDeque<Throwable> allThrowables = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private String lastMessage;

    /**
     * Creates an {@code InMemoryMessageHandler} with a default maximum of 10&thinsp;000 entries per list.
     */
    public InMemoryMessageHandler() {
        this(10_000);
    }

    /**
     * Creates an {@code InMemoryMessageHandler} that retains at most {@code maxEntries} messages
     * per level list.
     * <p>
     * When a list is full, the oldest entry is evicted before the new one is added (FIFO eviction).
     *
     * @param maxEntries the maximum number of entries to retain in each per-level list; must be positive
     * @throws IllegalArgumentException if {@code maxEntries} is {@code 0} or negative
     */
    public InMemoryMessageHandler(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
    }

    @Override
    public void handleMessage(@Nonnull SeverityNumber level, @Nonnull String message) {
        lock.lock();
        try {
            addWithLimit(allMessages, new Holder(level, message));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void handleThrowable(@Nonnull String message, @Nonnull Throwable throwable) {
        lock.lock();
        try {
            addWithLimit(allMessages, new Holder(SeverityNumber.ERROR, message));
            addWithLimit(allThrowables, throwable);
        } finally {
            lock.unlock();
        }
    }

    private <E> void addWithLimit(ArrayDeque<E> messages, E value) {
        if (messages.size() >= maxEntries) {
            messages.removeFirst();
        }
        messages.add(value);
    }

    /**
     * @return an unmodifiable list of all messages handled by this instance.
     */
    public List<String> getAllMessages() {
        lock.lock();
        try {
            return Collections.unmodifiableList(allMessages.stream().map(Holder::getMessage).collect(Collectors.toList()));
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return the configured maximum number of entries retained per list.
     */
    public int getMaxEntries() {
        return maxEntries;
    }

    /**
     * @return an unmodifiable list of all info messages handled by this instance.
     */
    public List<String> getAllInfoMessages() {
        lock.lock();
        try {
            return filterMessages(Arrays.asList(SeverityNumber.INFO, SeverityNumber.INFO2, SeverityNumber.INFO3, SeverityNumber.INFO4));
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return an unmodifiable list of all warning messages handled by this instance.
     */
    public List<String> getAllWarnMessages() {
        lock.lock();
        try {
            return filterMessages(Arrays.asList(SeverityNumber.WARN, SeverityNumber.WARN2, SeverityNumber.WARN3, SeverityNumber.WARN4));
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return an unmodifiable list of all error messages handled by this instance.
     */
    public List<String> getAllErrorMessages() {
        lock.lock();
        try {
            return filterMessages(Arrays.asList(SeverityNumber.ERROR, SeverityNumber.ERROR2, SeverityNumber.ERROR3, SeverityNumber.ERROR4));
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return an unmodifiable list of all fatal messages handled by this instance.
     */
    public List<String> getAllFatalMessages() {
        lock.lock();
        try {
            return filterMessages(Arrays.asList(SeverityNumber.FATAL, SeverityNumber.FATAL2, SeverityNumber.FATAL3, SeverityNumber.FATAL4));
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return an unmodifiable list of all debug messages handled by this instance.
     */
    public List<String> getAllDebugMessages() {
        lock.lock();
        try {
            return filterMessages(Arrays.asList(SeverityNumber.DEBUG, SeverityNumber.DEBUG2, SeverityNumber.DEBUG3, SeverityNumber.DEBUG4));
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return an unmodifiable list of all trace messages handled by this instance.
     */
    public List<String> getAllTraceMessages() {
        lock.lock();
        try {
            return filterMessages(Arrays.asList(SeverityNumber.TRACE, SeverityNumber.TRACE2, SeverityNumber.TRACE3, SeverityNumber.TRACE4));
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return an unmodifiable list of all exception messages handled by this instance.
     */
    public List<String> getAllExceptionMessages() {
        lock.lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(allThrowables.stream().map(Throwable::getMessage).collect(Collectors.toList())));
        } finally {
            lock.unlock();
        }
    }

    private List<String> filterMessages(List<SeverityNumber> severities) {
        return Collections.unmodifiableList(allMessages.stream().filter(h -> severities.contains(h.getSeverity())).map(Holder::getMessage).collect(Collectors.toList()));
    }


    /**
     * @return an unmodifiable list of all exceptions handled by this instance.
     */
    public List<Throwable> getAllThrowables() {
        lock.lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(allThrowables));
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return the last message handled by this instance, or {@code null} if no message has been handled yet
     */
    public @Nullable String getLastMessage() {
        lock.lock();
        try {
            return lastMessage;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes all retained messages and exceptions from every per-level list, the combined
     * {@code allMessages} list, and resets {@link #getLastMessage()} to {@code null}.
     * <p>
     * The operation is performed atomically under the handler's lock, so no messages
     * can arrive between individual list clears.
     */
    public void clear() {
        lock.lock();
        try {
            allMessages.clear();
            allThrowables.clear();
            lastMessage = null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a consistent snapshot of all retained collections and the last handled message.
     * Unlike reading each accessor separately, this method guarantees that all returned data
     * comes from the same locked state.
     *
     * @return immutable snapshot of the current in-memory state
     */
    public Snapshot snapshot() {
        lock.lock();
        try {
            return new Snapshot(allMessages, allThrowables, lastMessage);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Immutable, point-in-time snapshot of all messages retained by an {@link InMemoryMessageHandler}.
     * <p>
     * A snapshot is obtained via {@link InMemoryMessageHandler#snapshot()} under the handler's lock,
     * guaranteeing that all lists and {@link #getLastMessage()} reflect the same instant. Unlike
     * calling individual accessors in sequence, no messages can arrive between list reads.
     * <p>
     * All list accessors return unmodifiable views; attempting to mutate them throws
     * {@link UnsupportedOperationException}.
     */
    public static final class Snapshot {
        private final List<Holder> allMessages;
        private final List<Throwable> allThrowables;
        private final String lastMessage;

        private Snapshot(final Collection<Holder> allMessages,
                         final Collection<Throwable> allThrowables, final String lastMessage) {
            this.allMessages = new ArrayList<>(allMessages);
            this.allThrowables = new ArrayList<>(allThrowables);
            this.lastMessage = lastMessage;
        }

        /**
         * @return unmodifiable list of all messages across every level, in arrival order
         */
        public List<String> getAllMessages() {
            return Collections.unmodifiableList(allMessages.stream().map(Holder::getMessage).collect(Collectors.toList()));
        }

        private List<String> filterMessages(List<SeverityNumber> severities) {
            return Collections.unmodifiableList(allMessages.stream().filter(h -> severities.contains(h.getSeverity())).map(Holder::getMessage).collect(Collectors.toList()));
        }

        /**
         * @return unmodifiable list of all info-level messages, in arrival order
         */
        public List<String> getAllInfoMessages() {
            return filterMessages(Arrays.asList(SeverityNumber.INFO, SeverityNumber.INFO2, SeverityNumber.INFO3, SeverityNumber.INFO4));
        }

        /**
         * @return unmodifiable list of all warning-level messages, in arrival order
         */
        public List<String> getAllWarnMessages() {
            return filterMessages(Arrays.asList(SeverityNumber.WARN, SeverityNumber.WARN2, SeverityNumber.WARN3, SeverityNumber.WARN4));
        }

        /**
         * @return unmodifiable list of all error-level messages, in arrival order
         */
        public List<String> getAllErrorMessages() {
            return filterMessages(Arrays.asList(SeverityNumber.ERROR, SeverityNumber.ERROR2, SeverityNumber.ERROR3, SeverityNumber.ERROR4));
        }

        /**
         * @return unmodifiable list of all fatal-level messages, in arrival order
         */
        public List<String> getAllFatalMessages() {
            return filterMessages(Arrays.asList(SeverityNumber.FATAL, SeverityNumber.FATAL2, SeverityNumber.FATAL3, SeverityNumber.FATAL4));
        }

        /**
         * @return unmodifiable list of all debug-level messages, in arrival order
         */
        public List<String> getAllDebugMessages() {
            return filterMessages(Arrays.asList(SeverityNumber.DEBUG, SeverityNumber.DEBUG2, SeverityNumber.DEBUG3, SeverityNumber.DEBUG4));
        }

        /**
         * @return unmodifiable list of all trace-level messages, in arrival order
         */
        public List<String> getAllTraceMessages() {
            return filterMessages(Arrays.asList(SeverityNumber.TRACE, SeverityNumber.TRACE2, SeverityNumber.TRACE3, SeverityNumber.TRACE4));
        }

        /**
         * @return unmodifiable list of all exception detail strings, in arrival order
         */
        public List<String> getAllExceptionMessages() {
            return allThrowables.stream().map(Throwable::getMessage).collect(Collectors.toList());
        }

        /**
         * @return unmodifiable list of all handled exceptions, in arrival order
         */
        public List<Throwable> getAllThrowables() {
            return allThrowables;
        }

        /**
         * @return the text of the last message handled across all levels, or {@code null}
         *         if no message has been handled yet
         */
        public @Nullable String getLastMessage() {
            return lastMessage;
        }
    }

    private static class Holder {
        private final SeverityNumber severity;
        private final String message;

        Holder(SeverityNumber severity, String message) {
            this.severity = severity;
            this.message = message;
        }
        public SeverityNumber getSeverity() {
            return severity;
        }

        public String getMessage() {
            return message;
        }
    }
}
