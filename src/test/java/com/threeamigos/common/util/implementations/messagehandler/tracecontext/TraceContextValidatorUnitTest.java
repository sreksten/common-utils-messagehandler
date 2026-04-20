package com.threeamigos.common.util.implementations.messagehandler.tracecontext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TraceContextValidator unit tests")
@Tag("unit")
@Tag("messageHandler")
class TraceContextValidatorUnitTest {

    private static final String VALID_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String VALID_PARENT_ID = "00f067aa0ba902b7";
    private static final String VALID_TRACEPARENT = "00-" + VALID_TRACE_ID + "-" + VALID_PARENT_ID + "-01";

    @Test
    @DisplayName("isValidTraceparent() should accept valid version 00")
    void isValidTraceparentShouldAcceptValidVersion00() {
        assertTrue(TraceContextValidator.isValidTraceparent(VALID_TRACEPARENT));
    }

    @Test
    @DisplayName("isValidTraceparent() should accept short version 00 IDs by left-padding")
    void isValidTraceparentShouldAcceptShortVersion00IdsByLeftPadding() throws Exception {
        String shortTraceparent = "00-1-2-01";
        String normalizedTraceId = "00000000000000000000000000000001";
        String normalizedParentId = "0000000000000002";
        String normalizedTraceparent = "00-" + normalizedTraceId + "-" + normalizedParentId + "-01";

        assertTrue(TraceContextValidator.isValidTraceparent(shortTraceparent));
        assertTrue(TraceContextValidator.isValidTraceparent(shortTraceparent, "1"));

        TraceContextValidator parsed = new TraceContextValidator(shortTraceparent);
        assertEquals(normalizedTraceId, parsed.getTraceId());
        assertEquals(normalizedTraceparent, parsed.getTraceparentValue());

        String shortParentTraceparent = "00-" + VALID_TRACE_ID + "-2-01";
        assertTrue(TraceContextValidator.isValidTraceparent(shortParentTraceparent));
        assertFalse(TraceContextValidator.isValidTraceparent("00-" + VALID_TRACE_ID + "-2-GG"));
    }

    @Test
    @DisplayName("isValidTraceparent() should reject malformed version 00 variants")
    void isValidTraceparentShouldRejectMalformedVersion00Variants() {
        assertFalse(TraceContextValidator.isValidTraceparent(null));
        assertFalse(TraceContextValidator.isValidTraceparent("00-1234"));
        assertFalse(TraceContextValidator.isValidTraceparent("00+" + VALID_TRACE_ID + "-" + VALID_PARENT_ID + "-01"));
        assertFalse(TraceContextValidator.isValidTraceparent("00-" + VALID_TRACE_ID + "+" + VALID_PARENT_ID + "-01"));
        assertFalse(TraceContextValidator.isValidTraceparent("00-" + VALID_TRACE_ID + "-" + VALID_PARENT_ID + "+01"));
        assertFalse(TraceContextValidator.isValidTraceparent("ff-" + VALID_TRACE_ID + "-" + VALID_PARENT_ID + "-01"));
        assertFalse(TraceContextValidator.isValidTraceparent("00-" + VALID_TRACE_ID + "-" + VALID_PARENT_ID + "-01-extra"));
        assertFalse(TraceContextValidator.isValidTraceparent("00-" + VALID_TRACE_ID.toUpperCase() + "-" + VALID_PARENT_ID + "-01"));
        assertFalse(TraceContextValidator.isValidTraceparent("00-00000000000000000000000000000000-" + VALID_PARENT_ID + "-01"));
        assertFalse(TraceContextValidator.isValidTraceparent("00-" + VALID_TRACE_ID + "-0000000000000000-01"));
        assertFalse(TraceContextValidator.isValidTraceparent("00-0-1-01"));
        assertFalse(TraceContextValidator.isValidTraceparent("00-1-0-01"));
        assertFalse(TraceContextValidator.isValidTraceparent("00-" + VALID_TRACE_ID + "-" + VALID_PARENT_ID + "-GG"));
    }

    @Test
    @DisplayName("isValidTraceparent() should parse higher versions per fallback rules")
    void isValidTraceparentShouldParseHigherVersionsPerFallbackRules() {
        String validHigherVersionNoExtra = "0A-" + VALID_TRACE_ID + "-" + VALID_PARENT_ID + "-AF";
        assertTrue(TraceContextValidator.isValidTraceparent(validHigherVersionNoExtra));

        String validHigherVersion = "0A-" + VALID_TRACE_ID.toUpperCase() + "-" + VALID_PARENT_ID.toUpperCase() + "-AF-extra";
        assertTrue(TraceContextValidator.isValidTraceparent(validHigherVersion));

        String noDashAfterFlags = "0A-" + VALID_TRACE_ID + "-" + VALID_PARENT_ID + "-01x";
        assertFalse(TraceContextValidator.isValidTraceparent(noDashAfterFlags));

        String allZeroTraceId = "0A-00000000000000000000000000000000-" + VALID_PARENT_ID + "-01-extra";
        assertFalse(TraceContextValidator.isValidTraceparent(allZeroTraceId));

        String allZeroParentId = "0A-" + VALID_TRACE_ID + "-0000000000000000-01-extra";
        assertFalse(TraceContextValidator.isValidTraceparent(allZeroParentId));

        String invalidTraceIdHex = "0A-4bf92f3577b34da6a3ce929d0e0e473g-" + VALID_PARENT_ID + "-01-extra";
        assertFalse(TraceContextValidator.isValidTraceparent(invalidTraceIdHex));

        String invalidParentIdHex = "0A-" + VALID_TRACE_ID + "-00f067aa0ba902bg-01-extra";
        assertFalse(TraceContextValidator.isValidTraceparent(invalidParentIdHex));

        String invalidFlagsHex = "0A-" + VALID_TRACE_ID + "-" + VALID_PARENT_ID + "-g1-extra";
        assertFalse(TraceContextValidator.isValidTraceparent(invalidFlagsHex));

        String invalidVersion = "GG-" + VALID_TRACE_ID + "-" + VALID_PARENT_ID + "-01-extra";
        assertFalse(TraceContextValidator.isValidTraceparent(invalidVersion));
    }

    @Test
    @DisplayName("isValidTraceparent(traceparent, traceId) should validate consistency")
    void isValidTraceparentWithTraceIdShouldValidateConsistency() {
        assertTrue(TraceContextValidator.isValidTraceparent(VALID_TRACEPARENT, VALID_TRACE_ID));
        assertTrue(TraceContextValidator.isValidTraceparent("00-1-" + VALID_PARENT_ID + "-01", "1"));
        assertFalse(TraceContextValidator.isValidTraceparent(VALID_TRACEPARENT, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        assertFalse(TraceContextValidator.isValidTraceparent(VALID_TRACEPARENT, VALID_TRACE_ID.toUpperCase()));
        assertFalse(TraceContextValidator.isValidTraceparent("invalid", VALID_TRACE_ID));
    }

    @Test
    @DisplayName("isValidTraceId() and isValidParentId() should enforce lower-hex and non-zero")
    void isValidTraceIdAndParentIdShouldEnforceLowerHexAndNonZero() {
        assertTrue(TraceContextValidator.isValidTraceId(VALID_TRACE_ID));
        assertTrue(TraceContextValidator.isValidTraceId("abc"));
        assertFalse(TraceContextValidator.isValidTraceId(VALID_TRACE_ID.toUpperCase()));
        assertFalse(TraceContextValidator.isValidTraceId("00000000000000000000000000000000"));
        assertFalse(TraceContextValidator.isValidTraceId("0"));
        assertFalse(TraceContextValidator.isValidTraceId("abz"));

        assertTrue(TraceContextValidator.isValidParentId(VALID_PARENT_ID));
        assertTrue(TraceContextValidator.isValidParentId("abc"));
        assertFalse(TraceContextValidator.isValidParentId(VALID_PARENT_ID.toUpperCase()));
        assertFalse(TraceContextValidator.isValidParentId("0000000000000000"));
        assertFalse(TraceContextValidator.isValidParentId("0"));
        assertFalse(TraceContextValidator.isValidParentId("abz"));
    }

    @Test
    @DisplayName("constructors should create compliant values and parse valid inputs")
    void constructorsShouldCreateCompliantValuesAndParseValidInputs() throws Exception {
        TraceContextValidator generated = new TraceContextValidator();
        assertTrue(TraceContextValidator.isValidTraceId(generated.getTraceId()));
        assertTrue(TraceContextValidator.isValidTraceparent(generated.getTraceparentValue()));

        TraceContextValidator parsed = new TraceContextValidator(VALID_TRACEPARENT);
        assertEquals(VALID_TRACE_ID, parsed.getTraceId());
        assertEquals(VALID_TRACEPARENT, parsed.getTraceparentValue());

        TraceContextValidator shortParsed = new TraceContextValidator("00-1-2-01");
        assertEquals("00000000000000000000000000000001", shortParsed.getTraceId());
        assertEquals("00-00000000000000000000000000000001-0000000000000002-01", shortParsed.getTraceparentValue());

        TraceContextValidator higherVersion = new TraceContextValidator(
                "0a-" + VALID_TRACE_ID.toUpperCase() + "-" + VALID_PARENT_ID.toUpperCase() + "-a2-extra",
                "vendor1=value1, vendor2=value2");
        assertEquals(VALID_TRACE_ID, higherVersion.getTraceId());
        assertEquals("00-" + VALID_TRACE_ID + "-" + VALID_PARENT_ID + "-00", higherVersion.getTraceparentValue());
        assertEquals("vendor1=value1,vendor2=value2", higherVersion.getTracestateValue());
    }

    @Test
    @DisplayName("constructors should reject invalid traceparent and tracestate")
    void constructorsShouldRejectInvalidTraceparentAndTracestate() {
        assertThrows(InvalidTraceContextException.class, () -> new TraceContextValidator("bad"));
        assertThrows(InvalidTraceContextException.class, () -> new TraceContextValidator(VALID_TRACEPARENT, "k=v,k=v"));
    }

    @Test
    @DisplayName("combineTracestateHeaders() should combine values in field order")
    void combineTracestateHeadersShouldCombineValuesInFieldOrder() {
        assertEquals("", TraceContextValidator.combineTracestateHeaders((String[]) null));
        assertEquals("", TraceContextValidator.combineTracestateHeaders());
        assertEquals("a=1,b=2", TraceContextValidator.combineTracestateHeaders("a=1", "b=2"));
        assertEquals("a=1,b=2", TraceContextValidator.combineTracestateHeaders("a=1", null, "b=2"));
    }

    @Test
    @DisplayName("constructors should accept multiple tracestate headers")
    void constructorsShouldAcceptMultipleTracestateHeaders() throws Exception {
        TraceContextValidator validator = new TraceContextValidator(VALID_TRACEPARENT, "a=1", "b=2");
        assertEquals("a=1,b=2", validator.getTracestateValue());
    }

    @Test
    @DisplayName("fromIncomingHeaders() should restart on missing or invalid traceparent")
    void fromIncomingHeadersShouldRestartOnMissingOrInvalidTraceparent() {
        TraceContextValidator missing = TraceContextValidator.fromIncomingHeaders(null, "a=1");
        assertTrue(TraceContextValidator.isValidTraceparent(missing.getTraceparentValue()));
        assertEquals("", missing.getTracestateValue());

        TraceContextValidator empty = TraceContextValidator.fromIncomingHeaders("", "a=1");
        assertTrue(TraceContextValidator.isValidTraceparent(empty.getTraceparentValue()));
        assertEquals("", empty.getTracestateValue());

        TraceContextValidator invalid = TraceContextValidator.fromIncomingHeaders("bad", "a=1");
        assertTrue(TraceContextValidator.isValidTraceparent(invalid.getTraceparentValue()));
        assertEquals("", invalid.getTracestateValue());
    }

    @Test
    @DisplayName("fromIncomingHeaders() should parse valid traceparent and discard invalid tracestate")
    void fromIncomingHeadersShouldParseValidTraceparentAndDiscardInvalidTracestate() {
        TraceContextValidator valid = TraceContextValidator.fromIncomingHeaders(VALID_TRACEPARENT, "a=1");
        assertEquals(VALID_TRACEPARENT, valid.getTraceparentValue());
        assertEquals("a=1", valid.getTracestateValue());

        TraceContextValidator invalidTracestate = TraceContextValidator.fromIncomingHeaders(VALID_TRACEPARENT, "a=1,a=2");
        assertEquals(VALID_TRACEPARENT, invalidTracestate.getTraceparentValue());
        assertEquals("", invalidTracestate.getTracestateValue());

        TraceContextValidator multiHeader = TraceContextValidator.fromIncomingHeaders(VALID_TRACEPARENT, "a=1", "b=2");
        assertEquals("a=1,b=2", multiHeader.getTracestateValue());
    }

    @Test
    @DisplayName("toCompliantTraceId() and extractShortTraceId() should support interop conversion")
    void toCompliantTraceIdAndExtractShortTraceIdShouldSupportInteropConversion() throws Exception {
        assertEquals(VALID_TRACE_ID, TraceContextValidator.toCompliantTraceId(VALID_TRACE_ID));
        assertEquals("000000000000000000000000000000ab", TraceContextValidator.toCompliantTraceId("ab"));
        assertThrows(InvalidTraceContextException.class, () -> TraceContextValidator.toCompliantTraceId("AB"));
        assertThrows(InvalidTraceContextException.class, () -> TraceContextValidator.toCompliantTraceId("0"));

        assertEquals("0e0e4736", TraceContextValidator.extractShortTraceId(VALID_TRACE_ID, 8));
        assertEquals("ab", TraceContextValidator.extractShortTraceId("ab", 2));
        assertThrows(InvalidTraceContextException.class, () -> TraceContextValidator.extractShortTraceId("AB", 2));
        assertThrows(InvalidTraceContextException.class, () -> TraceContextValidator.extractShortTraceId(VALID_TRACE_ID, 0));
        assertThrows(InvalidTraceContextException.class, () -> TraceContextValidator.extractShortTraceId(VALID_TRACE_ID, 33));
    }

    @Test
    @DisplayName("tracestate truncation should remove entries longer than 128 characters first")
    void tracestateTruncationShouldRemoveEntriesLongerThan128128CharactersFirst() throws Exception {
        String longEntryTracestate = "a=" + repeat("x", 130)
                + ",b=" + repeat("y", 100)
                + ",c=" + repeat("z", 100)
                + ",d=" + repeat("w", 100)
                + ",e=" + repeat("k", 100)
                + ",f=" + repeat("m", 100);

        TraceContextValidator validator = new TraceContextValidator(VALID_TRACEPARENT, longEntryTracestate);
        String normalized = validator.getTracestateValue();
        assertFalse(normalized.contains("a="));
        assertTrue(normalized.startsWith("b="));
        assertTrue(normalized.endsWith("e=" + repeat("k", 100)));
        assertFalse(normalized.contains("f="));
    }

    @Test
    @DisplayName("tracestate truncation should remove right-most entries until total length fits")
    void tracestateTruncationShouldRemoveRightMostEntriesUntilTotalLengthFits() throws Exception {
        String oversized = "a=" + repeat("x", 100)
                + ",b=" + repeat("y", 100)
                + ",c=" + repeat("z", 100)
                + ",d=" + repeat("w", 100)
                + ",e=" + repeat("k", 100)
                + ",f=" + repeat("m", 100);

        TraceContextValidator validator = new TraceContextValidator(VALID_TRACEPARENT, oversized);
        assertTrue(validator.getTracestateValue().contains("a="));
        assertTrue(validator.getTracestateValue().contains("d="));
        assertFalse(validator.getTracestateValue().contains("e="));
        assertFalse(validator.getTracestateValue().contains("f="));

        validator.upsertVendorEntry("vendor", repeat("v", 130));
        assertFalse(validator.getTracestateValue().contains("vendor="));
    }

    @Test
    @DisplayName("isValidTracestate() should accept valid values and reject invalid ones")
    void isValidTracestateShouldAcceptValidValuesAndRejectInvalidOnes() {
        assertTrue(TraceContextValidator.isValidTracestate(null));
        assertTrue(TraceContextValidator.isValidTracestate(""));
        assertTrue(TraceContextValidator.isValidTracestate("   "));
        assertTrue(TraceContextValidator.isValidTracestate("a=v"));
        assertTrue(TraceContextValidator.isValidTracestate("a=v,,\t ,b=value"));
        assertTrue(TraceContextValidator.isValidTracestate("1tenant@sys=value"));
        assertTrue(TraceContextValidator.isValidTracestate("abc= leading-space-ok"));

        assertFalse(TraceContextValidator.isValidTracestate("a"));
        assertFalse(TraceContextValidator.isValidTracestate("A=v"));
        assertFalse(TraceContextValidator.isValidTracestate("a@b@c=v"));
        assertFalse(TraceContextValidator.isValidTracestate("1tenant@1sys=v"));
        assertTrue(TraceContextValidator.isValidTracestate("a=v "));
        assertTrue(TraceContextValidator.isValidTracestate("a=v,"));
        assertFalse(TraceContextValidator.isValidTracestate("a=v,b=v,b=v"));
        assertFalse(TraceContextValidator.isValidTracestate("a=va,lue"));
        assertFalse(TraceContextValidator.isValidTracestate("a=va=lue"));
        assertFalse(TraceContextValidator.isValidTracestate("a=va\tlue"));
        assertFalse(TraceContextValidator.isValidTracestate(repeat("a", 257) + "=v"));
        assertFalse(TraceContextValidator.isValidTracestate("a=" + repeat("v", 257)));
        assertFalse(TraceContextValidator.isValidTracestate(buildTracestateWithMembers(33)));
    }

    @Test
    @DisplayName("upsertVendorEntry() should add first, update existing and enforce member limit")
    void upsertVendorEntryShouldAddFirstUpdateExistingAndEnforceMemberLimit() throws Exception {
        TraceContextValidator validator = new TraceContextValidator(VALID_TRACEPARENT, "a=1,b=2,c=3");
        validator.upsertVendorEntry("vendor", "v");
        assertEquals("vendor=v,a=1,b=2,c=3", validator.getTracestateValue());

        validator.upsertVendorEntry("b", "22");
        assertEquals("b=22,vendor=v,a=1,c=3", validator.getTracestateValue());

        TraceContextValidator oversized = new TraceContextValidator(VALID_TRACEPARENT, buildTracestateWithMembers(32));
        oversized.upsertVendorEntry("new", "entry");
        assertTrue(TraceContextValidator.isValidTracestate(oversized.getTracestateValue()));
        assertEquals(32, oversized.getTracestateValue().split(",", -1).length);
        assertTrue(oversized.getTracestateValue().startsWith("new=entry,"));
    }

    @Test
    @DisplayName("upsertVendorEntry() should reject invalid vendor key and value")
    void upsertVendorEntryShouldRejectInvalidVendorKeyAndValue() throws Exception {
        TraceContextValidator validator = new TraceContextValidator(VALID_TRACEPARENT);
        assertThrows(InvalidTraceContextException.class, () -> validator.upsertVendorEntry("Invalid", "v"));
        assertThrows(InvalidTraceContextException.class, () -> validator.upsertVendorEntry("valid", "bad "));
    }

    @Test
    @DisplayName("header output methods should expose trace values and complete HTTP header lines")
    void headerOutputMethodsShouldExposeTraceValuesAndCompleteHttpHeaderLines() throws Exception {
        TraceContextValidator withoutTracestate = new TraceContextValidator(VALID_TRACEPARENT);
        assertEquals(VALID_TRACEPARENT, withoutTracestate.getTraceValue());
        assertEquals("traceparent: " + VALID_TRACEPARENT, withoutTracestate.getHttpHeaderValue());
        assertEquals(withoutTracestate.getHttpHeaderValue(), withoutTracestate.getCompleteHeaderValue());

        TraceContextValidator withTracestate = new TraceContextValidator(VALID_TRACEPARENT, "a=v");
        assertEquals(
                "traceparent: " + VALID_TRACEPARENT + "\r\ntracestate: a=v",
                withTracestate.getHttpHeaderValue());
    }

    @Test
    @DisplayName("private helpers should enforce low-level grammar edges")
    void privateHelpersShouldEnforceLowLevelGrammarEdges() throws Exception {
        assertTrue(invokeBoolean("isNotValidTracestateKey", new Class<?>[]{String.class}, (Object) null));
        assertTrue(invokeBoolean("isNotValidTracestateKey", new Class<?>[]{String.class}, ""));
        assertTrue(invokeBoolean("isNotValidTracestateKey", new Class<?>[]{String.class}, repeat("a", 257)));
        assertTrue(invokeBoolean("isNotValidTracestateKey", new Class<?>[]{String.class}, "_tenant@sys"));
        assertTrue(invokeBoolean("isNotValidTracestateKey", new Class<?>[]{String.class}, "tenant@sy!s"));
        assertTrue(invokeBoolean("isNotValidTracestateKey", new Class<?>[]{String.class}, "a!"));
        assertFalse(invokeBoolean("isNotValidTracestateKey", new Class<?>[]{String.class}, "a"));

        assertFalse(invokeBoolean("isValidSimpleKey", new Class<?>[]{String.class}, ""));
        assertFalse(invokeBoolean("isValidSimpleKey", new Class<?>[]{String.class}, "a!"));

        assertFalse(invokeBoolean("isValidTenantId", new Class<?>[]{String.class}, ""));
        assertFalse(invokeBoolean("isValidTenantId", new Class<?>[]{String.class}, "_a"));
        assertFalse(invokeBoolean("isValidTenantId", new Class<?>[]{String.class}, "a!"));
        assertFalse(invokeBoolean("isValidTenantId", new Class<?>[]{String.class}, repeat("a", 242)));

        assertFalse(invokeBoolean("isValidSystemId", new Class<?>[]{String.class}, "a!"));
        assertFalse(invokeBoolean("isValidSystemId", new Class<?>[]{String.class}, ""));
        assertFalse(invokeBoolean("isValidSystemId", new Class<?>[]{String.class}, repeat("a", 15)));

        assertFalse(invokeBoolean("isNotValidKeyChar", new Class<?>[]{char.class}, 'a'));
        assertFalse(invokeBoolean("isNotValidKeyChar", new Class<?>[]{char.class}, '5'));
        assertFalse(invokeBoolean("isNotValidKeyChar", new Class<?>[]{char.class}, '_'));
        assertFalse(invokeBoolean("isNotValidKeyChar", new Class<?>[]{char.class}, '-'));
        assertFalse(invokeBoolean("isNotValidKeyChar", new Class<?>[]{char.class}, '*'));
        assertFalse(invokeBoolean("isNotValidKeyChar", new Class<?>[]{char.class}, '/'));
        assertTrue(invokeBoolean("isNotValidKeyChar", new Class<?>[]{char.class}, '!'));

        assertFalse(invokeBoolean("isNotLowerAlpha", new Class<?>[]{char.class}, 'a'));
        assertTrue(invokeBoolean("isNotLowerAlpha", new Class<?>[]{char.class}, 'A'));
        assertTrue(invokeBoolean("isNotLowerAlpha", new Class<?>[]{char.class}, '{'));
        assertFalse(invokeBoolean("isNotDigit", new Class<?>[]{char.class}, '8'));
        assertTrue(invokeBoolean("isNotDigit", new Class<?>[]{char.class}, 'x'));

        assertTrue(invokeBoolean("isNotValidTracestateValue", new Class<?>[]{String.class}, (Object) null));
        assertTrue(invokeBoolean("isNotValidTracestateValue", new Class<?>[]{String.class}, ""));
        assertTrue(invokeBoolean("isNotValidTracestateValue", new Class<?>[]{String.class}, "a,b"));
        assertTrue(invokeBoolean("isNotValidTracestateValue", new Class<?>[]{String.class}, "a=b"));
        assertTrue(invokeBoolean("isNotValidTracestateValue", new Class<?>[]{String.class}, "a\u007F"));
        assertFalse(invokeBoolean("isNotValidTracestateValue", new Class<?>[]{String.class}, "ok value"));
        assertTrue(invokeBoolean("isNotValidHex", new Class<?>[]{String.class, int.class}, null, 2));
        assertTrue(invokeBoolean("isNotValidHex", new Class<?>[]{String.class, int.class}, "0", 2));
        assertTrue(invokeBoolean("isNotValidLowerHex", new Class<?>[]{String.class}, (Object) null));
        assertTrue(invokeBoolean("isNotValidLowerHex", new Class<?>[]{String.class}, "0"));
        assertFalse(invokeBoolean("isNotLowerHexCharacter", new Class<?>[]{char.class}, '5'));
        assertFalse(invokeBoolean("isNotLowerHexCharacter", new Class<?>[]{char.class}, 'b'));
        assertTrue(invokeBoolean("isNotLowerHexCharacter", new Class<?>[]{char.class}, 'g'));
        assertTrue(invokeBoolean("isNotLowerHexCharacter", new Class<?>[]{char.class}, ':'));
        assertTrue(invokeBoolean("isNotLowerHexCharacter", new Class<?>[]{char.class}, '/'));
        assertTrue(invokeBoolean("isHexCharacter", new Class<?>[]{char.class}, '5'));
        assertTrue(invokeBoolean("isHexCharacter", new Class<?>[]{char.class}, 'b'));
        assertTrue(invokeBoolean("isHexCharacter", new Class<?>[]{char.class}, 'B'));
        assertFalse(invokeBoolean("isHexCharacter", new Class<?>[]{char.class}, 'g'));
        assertFalse(invokeBoolean("isHexCharacter", new Class<?>[]{char.class}, 'G'));
        assertFalse(invokeBoolean("isHexCharacter", new Class<?>[]{char.class}, ':'));
        assertFalse(invokeBoolean("isHexCharacter", new Class<?>[]{char.class}, '/'));
        assertNull(invokeObject("normalizeLowerHexIdToLength", new Class<?>[]{String.class, int.class}, null, 4));
        assertNull(invokeObject("normalizeLowerHexIdToLength", new Class<?>[]{String.class, int.class}, "", 4));
        assertNull(invokeObject("normalizeLowerHexIdToLength", new Class<?>[]{String.class, int.class}, "abcde", 4));
        assertNull(invokeObject("normalizeLowerHexIdToLength", new Class<?>[]{String.class, int.class}, "abz", 4));
        assertNull(invokeObject("normalizeLowerHexIdToLength", new Class<?>[]{String.class, int.class}, "0", 4));
        assertEquals("000a", invokeObject("normalizeLowerHexIdToLength", new Class<?>[]{String.class, int.class}, "a", 4));
        assertEquals("abcd", invokeObject("normalizeLowerHexIdToLength", new Class<?>[]{String.class, int.class}, "abcd", 4));
        assertEquals("abcd", invokeObject("leftPadWithZeroes", new Class<?>[]{String.class, int.class}, "abcd", 4));
        assertEquals("00ab", invokeObject("leftPadWithZeroes", new Class<?>[]{String.class, int.class}, "ab", 4));
        assertTrue(invokeBoolean("isLowerHex", new Class<?>[]{String.class}, "ab12"));
        assertFalse(invokeBoolean("isLowerHex", new Class<?>[]{String.class}, "abz"));

        LinkedHashMap<String, String> empty = new LinkedHashMap<String, String>();
        invokeVoid("removeLastEntry", new Class<?>[]{LinkedHashMap.class}, empty);
        assertTrue(empty.isEmpty());
    }

    private static String buildTracestateWithMembers(final int memberCount) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < memberCount; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('k').append(i).append("=v").append(i);
        }
        return builder.toString();
    }

    private static String repeat(final String value, final int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private static boolean invokeBoolean(final String methodName, final Class<?>[] parameterTypes, final Object... args)
            throws Exception {
        Method method = TraceContextValidator.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, args);
    }

    private static void invokeVoid(final String methodName, final Class<?>[] parameterTypes, final Object... args)
            throws Exception {
        Method method = TraceContextValidator.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        method.invoke(null, args);
    }

    private static Object invokeObject(final String methodName, final Class<?>[] parameterTypes, final Object... args)
            throws Exception {
        Method method = TraceContextValidator.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}
