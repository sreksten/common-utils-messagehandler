package com.threeamigos.common.util.implementations.messagehandler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DailyRotationPolicy unit tests")
@Tag("unit")
@Tag("messageHandler")
class DailyRotationPolicyUnitTest {

    @Test
    @DisplayName("shouldRotate should return false when open date is today")
    void shouldNotRotateOnSameDay() {
        DailyRotationPolicy policy = new DailyRotationPolicy();
        assertFalse(policy.shouldRotate(Paths.get("app.log"), 0));
    }

    @Test
    @DisplayName("shouldRotate should return true when open date is in the past")
    void shouldRotateWhenOpenDateIsInThePast() {
        DailyRotationPolicy policy = new DailyRotationPolicy(LocalDate.now().minusDays(1));
        assertTrue(policy.shouldRotate(Paths.get("app.log"), 0));
    }

    @Test
    @DisplayName("onRotated should reset open date so shouldRotate returns false")
    void onRotatedShouldResetOpenDate() {
        DailyRotationPolicy policy = new DailyRotationPolicy(LocalDate.now().minusDays(1));
        assertTrue(policy.shouldRotate(Paths.get("app.log"), 0));
        policy.onRotated();
        assertFalse(policy.shouldRotate(Paths.get("app.log"), 0));
    }

    @Test
    @DisplayName("rotatedFilePath should insert date before last extension")
    void rotatedFilePathShouldInsertDateBeforeExtension() {
        LocalDate openDate = LocalDate.of(2025, 4, 15);
        DailyRotationPolicy policy = new DailyRotationPolicy(openDate);
        Path original = Paths.get("/var/log/app.log");

        Path rotated = policy.rotatedFilePath(original);

        assertEquals("/var/log/app.2025-04-15.log", rotated.toString().replace('\\', '/'));
    }

    @Test
    @DisplayName("rotatedFilePath should append date when file has no extension")
    void rotatedFilePathShouldAppendDateWhenNoExtension() {
        LocalDate openDate = LocalDate.of(2025, 4, 15);
        DailyRotationPolicy policy = new DailyRotationPolicy(openDate);
        Path original = Paths.get("/var/log/messages");

        Path rotated = policy.rotatedFilePath(original);

        assertEquals("/var/log/messages.2025-04-15", rotated.toString().replace('\\', '/'));
    }

    @Test
    @DisplayName("rotatedFilePath should handle file with dotfile name (leading dot only)")
    void rotatedFilePathShouldHandleDotfileName() {
        LocalDate openDate = LocalDate.of(2025, 4, 15);
        DailyRotationPolicy policy = new DailyRotationPolicy(openDate);
        // ".log" — dotIndex is 0, so no extension is detected (dotIndex > 0 check)
        Path original = Paths.get("/var/log/.log");

        Path rotated = policy.rotatedFilePath(original);

        // Leading-dot name has no extension by our rule → append date
        assertTrue(rotated.getFileName().toString().contains("2025-04-15"));
    }

    @Test
    @DisplayName("rotatedFilePath should work for file with no parent directory")
    void rotatedFilePathShouldWorkWithNoParent() {
        LocalDate openDate = LocalDate.of(2025, 4, 15);
        DailyRotationPolicy policy = new DailyRotationPolicy(openDate);
        Path original = Paths.get("server.log");

        Path rotated = policy.rotatedFilePath(original);

        assertNull(rotated.getParent());
        assertEquals("server.2025-04-15.log", rotated.getFileName().toString());
    }

    @Test
    @DisplayName("rotatedFilePath should use the open date (not today) for the archive name")
    void rotatedFilePathShouldUseOpenDateNotToday() {
        LocalDate pastDate = LocalDate.now().minusDays(3);
        DailyRotationPolicy policy = new DailyRotationPolicy(pastDate);
        Path original = Paths.get("app.log");

        Path rotated = policy.rotatedFilePath(original);

        assertTrue(rotated.getFileName().toString().contains(pastDate.toString()),
                "Archive name should contain the open date, not today");
    }
}
