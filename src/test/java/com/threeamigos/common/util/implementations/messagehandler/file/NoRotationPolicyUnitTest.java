package com.threeamigos.common.util.implementations.messagehandler.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("NoRotationPolicy unit tests")
@Tag("unit")
@Tag("messageHandler")
class NoRotationPolicyUnitTest {

    @Test
    @DisplayName("shouldRotate should always return false")
    void shouldRotateShouldAlwaysReturnFalse() {
        NoRotationPolicy policy = new NoRotationPolicy();
        Path file = Paths.get("app.log");

        assertFalse(policy.shouldRotate(file, 0L));
        assertFalse(policy.shouldRotate(file, 1024L));
        assertFalse(policy.shouldRotate(file, Long.MAX_VALUE));
    }

    @Test
    @DisplayName("rotatedFilePath should return the same path unchanged")
    void rotatedFilePathShouldReturnSamePathUnchanged() {
        NoRotationPolicy policy = new NoRotationPolicy();
        Path file = Paths.get("/var/log/app.log");

        assertEquals(file, policy.rotatedFilePath(file));
    }

    @Test
    @DisplayName("onRotated should be a no-op")
    void onRotatedShouldBeNoOp() {
        NoRotationPolicy policy = new NoRotationPolicy();
        assertDoesNotThrow(policy::onRotated);
    }
}
