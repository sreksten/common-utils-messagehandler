package com.threeamigos.common.util.implementations.messagehandler;

import com.threeamigos.common.util.interfaces.messagehandler.RotationPolicy;
import jakarta.annotation.Nonnull;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * A {@link RotationPolicy} that rotates the log file once per calendar day.
 * <p>
 * Rotation is triggered on the first write that occurs on a different calendar day from the day
 * the file was opened (or last rotated). The archived file is named by inserting the open date
 * before the final file extension:
 * <ul>
 *   <li>{@code app.log} → {@code app.2025-04-15.log}</li>
 *   <li>{@code server.log.gz} → {@code server.log.2025-04-15.gz}</li>
 *   <li>{@code messages} (no extension) → {@code messages.2025-04-15}</li>
 * </ul>
 * <p>
 * The {@link #onRotated()} callback advances the internal baseline date to today so that
 * subsequent writes on the new day do not trigger an immediate second rotation.
 * <p>
 * This class is thread-safe: the {@code openDate} field is {@code volatile} and is only mutated
 * inside {@code FileMessageHandler}'s write lock via {@link #onRotated()}.
 *
 * @author Stefano Reksten
 * @see com.threeamigos.common.util.implementations.messagehandler.FileMessageHandler
 */
public class DailyRotationPolicy implements RotationPolicy {

    private volatile LocalDate openDate;

    /**
     * Creates a {@code DailyRotationPolicy} initialised to today's date.
     */
    public DailyRotationPolicy() {
        this(LocalDate.now());
    }

    /**
     * Package-private constructor that accepts an explicit open date.
     * <p>
     * Intended for use in unit tests to simulate a past open date without manipulating the
     * system clock.
     *
     * @param openDate the date the log file was conceptually opened; must not be {@code null}
     */
    DailyRotationPolicy(@Nonnull final LocalDate openDate) {
        this.openDate = openDate;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns {@code true} when today's date differs from the date stored when this file was
     * opened (or last rotated via {@link #onRotated()}).
     */
    @Override
    public boolean shouldRotate(@Nonnull final Path filePath, final long bytesWrittenSinceOpen) {
        return !LocalDate.now().isEqual(openDate);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a path in the same directory as {@code filePath} with the open date inserted
     * before the final file extension (or appended if there is no extension).
     */
    @Override
    @Nonnull
    public Path rotatedFilePath(@Nonnull final Path filePath) {
        String dateStr = openDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String name = filePath.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        String rotatedName;
        if (dotIndex > 0) {
            rotatedName = name.substring(0, dotIndex) + "." + dateStr + name.substring(dotIndex);
        } else {
            rotatedName = name + "." + dateStr;
        }
        Path parent = filePath.getParent();
        return parent != null ? parent.resolve(rotatedName) : filePath.resolveSibling(rotatedName);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Advances the internal open-date baseline to today so that subsequent writes on the new
     * calendar day do not trigger an immediate second rotation.
     */
    @Override
    public void onRotated() {
        openDate = LocalDate.now();
    }
}
