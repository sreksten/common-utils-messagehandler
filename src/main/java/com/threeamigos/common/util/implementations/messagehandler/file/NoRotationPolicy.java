package com.threeamigos.common.util.implementations.messagehandler.file;

import com.threeamigos.common.util.interfaces.messagehandler.file.RotationPolicy;
import jakarta.annotation.Nonnull;

import java.nio.file.Path;

/**
 * A {@link RotationPolicy} that never rotates the log file.
 * <p>
 * Use this policy when you want to disable rotation explicitly while still using constructors that
 * accept a rotation policy argument.
 *
 * @author Stefano Reksten
 * @see com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler
 */
public class NoRotationPolicy implements RotationPolicy {

    /**
     * {@inheritDoc}
     * <p>
     * Always returns {@code false}.
     */
    @Override
    public boolean shouldRotate(@Nonnull final Path filePath, final long bytesWrittenSinceOpen) {
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the same path unchanged. This method is not expected to be called in normal usage
     * because {@link #shouldRotate(Path, long)} always returns {@code false}.
     */
    @Override
    @Nonnull
    public Path rotatedFilePath(@Nonnull final Path filePath) {
        return filePath;
    }
}
