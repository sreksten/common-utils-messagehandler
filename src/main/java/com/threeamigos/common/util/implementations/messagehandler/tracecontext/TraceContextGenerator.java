package com.threeamigos.common.util.implementations.messagehandler.tracecontext;

import java.security.SecureRandom;

/**
 * Generates W3C Trace Context identifiers.
 * <p>
 * Specification:
 * <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>.
 *
 * @author Stefano Reksten
 */
public final class TraceContextGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private TraceContextGenerator() {
    }

    /**
     * Generates a valid W3C {@code trace-id}:
     * 32 lowercase hexadecimal characters, not all zeros.
     *
     * @return a valid trace-id
     */
    public static String generateTraceId() {
        return generateRandomHex(16);
    }

    /**
     * Generates a valid W3C {@code parent-id}:
     * 16 lowercase hexadecimal characters, not all zeros.
     *
     * @return a valid parent-id
     */
    public static String generateParentId() {
        return generateRandomHex(8);
    }

    private static String generateRandomHex(final int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        bytes[0] = (byte) (SECURE_RANDOM.nextInt(255) + 1);
        return toLowerHex(bytes);
    }

    private static String toLowerHex(final byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        int i = 0;
        for (byte b : bytes) {
            int value = b & 0xFF;
            chars[i++] = HEX[value >>> 4];
            chars[i++] = HEX[value & 0x0F];
        }
        return new String(chars);
    }
}
