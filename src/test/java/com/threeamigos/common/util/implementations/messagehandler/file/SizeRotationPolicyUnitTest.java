package com.threeamigos.common.util.implementations.messagehandler.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SizeRotationPolicy unit tests")
@Tag("unit")
@Tag("messageHandler")
class SizeRotationPolicyUnitTest {

    @Test
    @DisplayName("Should throw if maxBytes is zero")
    void shouldThrowIfMaxBytesIsZero() {
        assertThrows(IllegalArgumentException.class, () -> new SizeRotationPolicy(0));
    }

    @Test
    @DisplayName("Should throw if maxBytes is negative")
    void shouldThrowIfMaxBytesIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new SizeRotationPolicy(-1));
    }

    @Test
    @DisplayName("shouldRotate should return false when bytes written is below threshold")
    void shouldNotRotateWhenBelowThreshold() {
        SizeRotationPolicy policy = new SizeRotationPolicy(1000);
        assertFalse(policy.shouldRotate(Paths.get("app.log"), 999));
    }

    @Test
    @DisplayName("shouldRotate should return true when bytes written equals threshold")
    void shouldRotateWhenAtThreshold() {
        SizeRotationPolicy policy = new SizeRotationPolicy(1000);
        assertTrue(policy.shouldRotate(Paths.get("app.log"), 1000));
    }

    @Test
    @DisplayName("shouldRotate should return true when bytes written exceeds threshold")
    void shouldRotateWhenAboveThreshold() {
        SizeRotationPolicy policy = new SizeRotationPolicy(1000);
        assertTrue(policy.shouldRotate(Paths.get("app.log"), 1500));
    }

    @Test
    @DisplayName("rotatedFilePath should return path in the same directory with timestamp suffix")
    void rotatedFilePathShouldBeInSameDirectoryWithTimestampSuffix() {
        SizeRotationPolicy policy = new SizeRotationPolicy(1000);
        Path original = Paths.get("/var/log/app.log");
        Path rotated = policy.rotatedFilePath(original);

        assertEquals(original.getParent(), rotated.getParent(),
                "Rotated file should be in the same directory");
        assertTrue(rotated.getFileName().toString().startsWith("app.log."),
                "Rotated filename should start with original name followed by a dot");
        // Timestamp suffix format: yyyyMMdd-HHmmss-SSS (e.g. 20250415-093012-456)
        String suffix = rotated.getFileName().toString().substring("app.log.".length());
        assertTrue(suffix.matches("\\d{8}-\\d{6}-\\d{3}"),
                "Timestamp suffix should match yyyyMMdd-HHmmss-SSS format, was: " + suffix);
    }

    @Test
    @DisplayName("rotatedFilePath should work for file with no parent directory")
    void rotatedFilePathShouldWorkWithNoParent() {
        SizeRotationPolicy policy = new SizeRotationPolicy(500);
        Path original = Paths.get("messages.log");
        Path rotated = policy.rotatedFilePath(original);

        assertNull(rotated.getParent(), "No parent expected for relative filename with no directory");
        assertTrue(rotated.getFileName().toString().startsWith("messages.log."));
    }

    @Test
    @DisplayName("onRotated should be a no-op (stateless policy)")
    void onRotatedShouldBeNoOp() {
        SizeRotationPolicy policy = new SizeRotationPolicy(1000);
        assertDoesNotThrow(policy::onRotated);
        // Still rotates at the same threshold after onRotated()
        assertTrue(policy.shouldRotate(Paths.get("app.log"), 1000));
    }
}
