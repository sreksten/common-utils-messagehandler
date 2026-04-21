package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.file.RotationPolicy;
import jakarta.annotation.Nonnull;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A {@link RotationPolicy} that rotates the log file once the estimated number of bytes
 * written since the last open exceeds a configurable threshold.
 * <p>
 * The byte count is an approximation based on the UTF-8 encoding of the formatted log lines.
 * Stack traces written by exception handlers are not included in the count; set the threshold
 * slightly below the exact desired limit to account for this.
 * <p>
 * When rotation occurs, the current file is renamed by appending a timestamp suffix with
 * millisecond precision (e.g. {@code app.log.20250415-093012-456}). The timestamp is taken at
 * the moment {@link #rotatedFilePath(Path)} is called, which happens inside the handler's
 * write lock, so consecutive rotations will have distinct names.
 * <p>
 * This policy is stateless: {@link #onRotated()} does nothing because no per-open baseline
 * needs to be reset (that responsibility belongs to {@code FileMessageHandler}).
 *
 * @author Stefano Reksten
 * @see com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler
 */
public class SizeRotationPolicy implements RotationPolicy {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final long maxBytes;

    /**
     * Creates a {@code SizeRotationPolicy} that triggers rotation when the byte count
     * reaches {@code maxBytes}.
     *
     * @param maxBytes maximum approximate bytes to write before rotating; must be positive
     * @throws IllegalArgumentException if {@code maxBytes} is not positive
     */
    public SizeRotationPolicy(final long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive, was: " + maxBytes);
        }
        this.maxBytes = maxBytes;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns {@code true} when {@code bytesWrittenSinceOpen >= maxBytes}.
     */
    @Override
    public boolean shouldRotate(@Nonnull final Path filePath, final long bytesWrittenSinceOpen) {
        return bytesWrittenSinceOpen >= maxBytes;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a path in the same directory as {@code filePath} with a millisecond-precision
     * timestamp appended as a suffix, e.g. {@code /var/log/app.log.20250415-093012-456}.
     */
    @Override
    @Nonnull
    public Path rotatedFilePath(@Nonnull final Path filePath) {
        String timestamp = ZonedDateTime.now().format(TIMESTAMP);
        String rotatedName = filePath.getFileName().toString() + "." + timestamp;
        Path parent = filePath.getParent();
        return parent != null ? parent.resolve(rotatedName) : filePath.resolveSibling(rotatedName);
    }
}
