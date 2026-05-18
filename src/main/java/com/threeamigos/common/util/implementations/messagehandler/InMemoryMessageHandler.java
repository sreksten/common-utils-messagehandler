package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecord;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFactory;
import com.threeamigos.common.util.interfaces.messagehandler.otel.LogRecordFormatter;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
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
    private final LogRecordFactory logRecordFactory;
    private final LogRecordFormatter logRecordFormatter;

    private final ArrayDeque<Holder> allMessages = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private String lastMessage;

    /**
     * Creates an {@code InMemoryMessageHandler} with a default maximum of 10&thinsp;000 entries per list.
     */
    public InMemoryMessageHandler() {
        this(10_000, null, null);
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
        this(maxEntries, null, null);
    }

    /**
     * Creates an {@code InMemoryMessageHandler} that formats incoming messages from OTEL-like records
     * before storing them.
     *
     * @param logRecordFactory factory used to create records; nullable
     * @param logRecordFormatter formatter used to render records; nullable
     */
    public InMemoryMessageHandler(final @Nullable LogRecordFactory logRecordFactory,
                                  final @Nullable LogRecordFormatter logRecordFormatter) {
        this(10_000, logRecordFactory, logRecordFormatter);
    }

    /**
     * Creates an {@code InMemoryMessageHandler} with bounded retention and optional OTEL-like formatting.
     *
     * @param maxEntries the maximum number of entries to retain in each per-level list; must be positive
     * @param logRecordFactory factory used to create records; nullable
     * @param logRecordFormatter formatter used to render records; nullable
     * @throws IllegalArgumentException if {@code maxEntries} is {@code 0} or negative
     */
    public InMemoryMessageHandler(final int maxEntries,
                                  final @Nullable LogRecordFactory logRecordFactory,
                                  final @Nullable LogRecordFormatter logRecordFormatter) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
        this.logRecordFactory = logRecordFactory;
        this.logRecordFormatter = logRecordFormatter;
    }

    @Override
    public void handleMessage(@Nonnull SeverityNumber level, @Nonnull String message) {
        lock.lock();
        try {
            String rendered = formatMessage(level, message);
            addWithLimit(allMessages, new Holder(level, rendered, null));
            lastMessage = rendered;
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void handleExceptionInternal(@Nonnull String message, @Nonnull Throwable throwable) {
        lock.lock();
        try {
            String renderedMessage = formatExceptionMessage(message, throwable);
            String rendered = renderThrowableMessage(renderedMessage, throwable);
            addWithLimit(allMessages, new Holder(SeverityNumber.UNSPECIFIED, rendered, throwable));
            lastMessage = rendered;
        } finally {
            lock.unlock();
        }
    }

    private String formatMessage(final SeverityNumber level, final String message) {
        if (logRecordFactory == null || logRecordFormatter == null) {
            return message;
        }
        LogRecord logRecord = logRecordFactory.create(level, message);
        return logRecordFormatter.format(logRecord);
    }

    private String formatExceptionMessage(final String message, final Throwable throwable) {
        if (logRecordFactory == null || logRecordFormatter == null) {
            return message;
        }
        LogRecord logRecord = logRecordFactory.create(message, throwable);
        return logRecordFormatter.format(logRecord);
    }

    private static String renderThrowableMessage(final String message, final Throwable throwable) {
        String detail = ThrowableMessageFormatter.detail(throwable);
        if (message == null ||
                message.isEmpty() ||
                message.equals(throwable.getMessage()) ||
                message.equals(detail)) {
            return detail;
        }
        return message + ": " + detail;
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
            return filterMessages(SeverityNumber::isInfo);
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
            return filterMessages(SeverityNumber::isWarn);
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
            return filterMessages(SeverityNumber::isError);
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
            return filterMessages(SeverityNumber::isFatal);
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
            return filterMessages(SeverityNumber::isDebug);
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
            return filterMessages(SeverityNumber::isTrace);
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
            return Collections.unmodifiableList(new ArrayList<>(allMessages.stream().filter(h -> h.getSeverity() == SeverityNumber.UNSPECIFIED).map(Holder::getMessage).collect(Collectors.toList())));
        } finally {
            lock.unlock();
        }
    }

    private List<String> filterMessages(Function<SeverityNumber, Boolean> severityFilter) {
        return Collections.unmodifiableList(allMessages.stream().filter(h -> severityFilter.apply(h.getSeverity())).map(Holder::getMessage).collect(Collectors.toList()));
    }


    /**
     * @return an unmodifiable list of all exceptions handled by this instance.
     */
    public List<Throwable> getAllThrowables() {
        lock.lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(allMessages.stream().map(Holder::getThrowable).filter(Objects::nonNull).collect(Collectors.toList())));
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
            return new Snapshot(allMessages, lastMessage);
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
        private final String lastMessage;

        private Snapshot(final Collection<Holder> allMessages, final String lastMessage) {
            this.allMessages = new ArrayList<>(allMessages);
            this.lastMessage = lastMessage;
        }

        /**
         * @return unmodifiable list of all messages across every level, in arrival order
         */
        public List<String> getAllMessages() {
            return Collections.unmodifiableList(allMessages.stream().map(Holder::getMessage).collect(Collectors.toList()));
        }

        private List<String> filterMessages(Function<SeverityNumber, Boolean> severityFilter) {
            return Collections.unmodifiableList(allMessages.stream().filter(h -> severityFilter.apply(h.getSeverity())).map(Holder::getMessage).collect(Collectors.toList()));
        }

        /**
         * @return unmodifiable list of all info-level messages, in arrival order
         */
        public List<String> getAllInfoMessages() {
            return filterMessages(SeverityNumber::isInfo);
        }

        /**
         * @return unmodifiable list of all warning-level messages, in arrival order
         */
        public List<String> getAllWarnMessages() {
            return filterMessages(SeverityNumber::isWarn);
        }

        /**
         * @return unmodifiable list of all error-level messages, in arrival order
         */
        public List<String> getAllErrorMessages() {
            return filterMessages(SeverityNumber::isError);
        }

        /**
         * @return unmodifiable list of all fatal-level messages, in arrival order
         */
        public List<String> getAllFatalMessages() {
            return filterMessages(SeverityNumber::isFatal);
        }

        /**
         * @return unmodifiable list of all debug-level messages, in arrival order
         */
        public List<String> getAllDebugMessages() {
            return filterMessages(SeverityNumber::isDebug);
        }

        /**
         * @return unmodifiable list of all trace-level messages, in arrival order
         */
        public List<String> getAllTraceMessages() {
            return filterMessages(SeverityNumber::isTrace);
        }

        /**
         * @return unmodifiable list of all exception detail strings, in arrival order
         */
        public List<String> getAllExceptionMessages() {
            return Collections.unmodifiableList(allMessages.stream().filter(Holder::hasException).map(Holder::getMessage).collect(Collectors.toList()));
        }

        /**
         * @return unmodifiable list of all handled exceptions, in arrival order
         */
        public List<Throwable> getAllThrowables() {
            return Collections.unmodifiableList(allMessages.stream().map(Holder::getThrowable).filter(Objects::nonNull).collect(Collectors.toList()));
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
        private final Throwable throwable;

        Holder(SeverityNumber severity, String message, Throwable throwable) {
            this.severity = severity;
            this.message = message;
            this.throwable = throwable;
        }
        public SeverityNumber getSeverity() {
            return severity;
        }

        public String getMessage() {
            return message;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public boolean hasException() {
            return throwable != null;
        }
    }
}
