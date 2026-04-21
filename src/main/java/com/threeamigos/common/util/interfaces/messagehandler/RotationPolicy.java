package com.threeamigos.common.util.interfaces.messagehandler;

import jakarta.annotation.Nonnull;

import java.nio.file.Path;

/**
 * Strategy interface that controls when and how a log file is rotated.
 * <p>
 * A {@code RotationPolicy} is consulted by
 * {@link com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler}
 * after every write operation to decide whether the current log file should be archived and a new one started.
 * <p>
 * Implementations must be thread-safe if they hold mutable state (e.g., an open date), because
 * {@code FileMessageHandler} calls these methods from its worker thread.
 * <p>
 * Ready-made implementations:
 * <ul>
 *   <li>{@link com.threeamigos.common.util.implementations.messagehandler.SizeRotationPolicy}
 *       — rotates when the estimated bytes written since the last open exceed a configurable
 *       threshold.</li>
 *   <li>{@link com.threeamigos.common.util.implementations.messagehandler.DailyRotationPolicy}
 *       — rotates once per calendar day, naming the archived file with the date it was opened.</li>
 * </ul>
 *
 * @author Stefano Reksten
 * @see com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler
 */
public interface RotationPolicy {

    /**
     * Returns {@code true} if the current log file should be rotated.
     * <p>
     * This method is called inside {@code FileMessageHandler}'s write lock after every line is
     * written. Implementations should be inexpensive (no blocking I/O) and idempotent.
     *
     * @param filePath              path to the currently open log file; never {@code null}
     * @param bytesWrittenSinceOpen approximate number of UTF-8 bytes written to the file since
     *                              it was last opened or rotated
     * @return {@code true} if rotation should be performed immediately
     */
    boolean shouldRotate(@Nonnull Path filePath, long bytesWrittenSinceOpen);

    /**
     * Returns the path to which the current log file should be moved (renamed) during rotation.
     * <p>
     * The returned path must be on the same filesystem as {@code filePath} to allow an atomic
     * rename. It must not yet exist; if it does, the rotation will fail with an
     * {@link java.io.IOException}.
     *
     * @param filePath path of the currently open log file that is about to be rotated; never {@code null}
     * @return the destination path for the rotated (archived) file; never {@code null}
     */
    @Nonnull Path rotatedFilePath(@Nonnull Path filePath);

    /**
     * Called by {@code FileMessageHandler} immediately after a rotation has completed and the
     * new log file has been opened.
     * <p>
     * Stateful policies (e.g. {@link com.threeamigos.common.util.implementations.messagehandler.DailyRotationPolicy})
     * override this method to reset internal state so that the next call to
     * {@link #shouldRotate(Path, long)} uses the new file's baseline. The default implementation
     * is a no-op.
     */
    default void onRotated() {
        // No-op by default. Stateful policies override to reset their baseline.
    }
}
