package com.threeamigos.common.util.implementations.messagehandler.tracecontext;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Validates and manages W3C Trace Context values ({@code traceparent} and {@code tracestate}).
 * <p>
 * How to use:
 * <ol>
 *   <li>Create from incoming HTTP headers via
 *   {@link #fromIncomingHeaders(String, String)} (or the varargs overload for multiple
 *   {@code tracestate} headers).</li>
 *   <li>Optionally add or move your vendor entry to the front with
 *   {@link #upsertVendorEntry(String, String)} before forwarding.</li>
 *   <li>Propagate using {@link #getTraceparentValue()} and {@link #getTracestateValue()},
 *   or emit complete header lines with {@link #getHttpHeaderValue()}.</li>
 * </ol>
 * <pre>{@code
 * try {
 *     TraceContextValidator context =
 *             TraceContextValidator.fromIncomingHeaders(traceparentHeader, tracestateHeader);
 *     context.upsertVendorEntry("congo", "t61rcWkgMzE");
 *
 *     String outgoingTraceparent = context.getTraceparentValue();
 *     String outgoingTracestate = context.getTracestateValue();
 * } catch (InvalidTraceContextException ex) {
 *     // Handle invalid vendor key/value. fromIncomingHeaders(...) normalizes
 *     // invalid incoming headers instead of throwing; use constructors for strict handling.
 * }
 * }</pre>
 * <p>
 * Specification:
 * <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>
 * (including
 * <a href="https://www.w3.org/TR/trace-context/#trace-id">trace-id</a>).
 *
 * @author Stefano Reksten
 */
public final class TraceContextValidator {

    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String TRACESTATE_HEADER = "tracestate";

    private static final String CURRENT_VERSION = "00";
    private static final String DEFAULT_TRACE_FLAGS = "00";

    private static final int TRACE_ID_LENGTH = 32;
    private static final int PARENT_ID_LENGTH = 16;
    private static final int TRACE_FLAGS_LENGTH = 2;
    private static final int TRACEPARENT_V00_LENGTH = 55;
    private static final int MAX_TRACESTATE_MEMBERS = 32;
    private static final int MAX_TRACESTATE_KEY_LENGTH = 256;
    private static final int MAX_TRACESTATE_VALUE_LENGTH = 256;
    private static final int MAX_TRACESTATE_COMBINED_LENGTH = 512;
    private static final int TRUNCATION_PRIORITY_MEMBER_LENGTH = 128;

    private final LinkedHashMap<String, String> tracestateEntries = new LinkedHashMap<>();
    private final String traceId;
    private final String parentId;
    private final String traceFlags;

    /**
     * Creates a new trace context with generated compliant identifiers.
     * <p>
     * Use this when there is no incoming {@code traceparent} to continue, for example, when
     * starting a new root trace in this service. The same behavior is used as a fallback by
     * {@link #fromIncomingHeaders(String, String)} when incoming headers are absent or invalid.
     */
    public TraceContextValidator() {
        this.traceId = TraceContextGenerator.generateTraceId();
        this.parentId = TraceContextGenerator.generateParentId();
        this.traceFlags = DEFAULT_TRACE_FLAGS;
    }

    /**
     * Creates a validator from a {@code traceparent} value.
     *
     * @param traceparent the traceparent value to validate and load
     * @throws InvalidTraceContextException when the provided traceparent is invalid
     */
    public TraceContextValidator(final String traceparent) throws InvalidTraceContextException {
        this(traceparent, (String) null);
    }

    /**
     * Creates a validator from {@code traceparent} and {@code tracestate} values.
     *
     * @param traceparent the traceparent value to validate and load
     * @param tracestate  the tracestate value to validate and load
     * @throws InvalidTraceContextException when traceparent or tracestate is invalid
     */
    public TraceContextValidator(final String traceparent, final String tracestate) throws InvalidTraceContextException {
        ParsedTraceparent parsed = parseTraceparent(traceparent);
        if (parsed == null) {
            throw new InvalidTraceContextException("Invalid traceparent value");
        }

        this.traceId = parsed.traceId.toLowerCase(Locale.ROOT);
        this.parentId = parsed.parentId.toLowerCase(Locale.ROOT);
        this.traceFlags = normalizeTraceFlagsForOutgoing(parsed.traceFlags);
        this.tracestateEntries.putAll(applyTracestateLimits(parseTracestate(tracestate)));
    }

    /**
     * Creates a validator from {@code traceparent} and multiple {@code tracestate} header values.
     * Headers are combined in field order according to RFC7230 section 3.2.2.
     *
     * @param traceparent       the traceparent value to validate and load
     * @param tracestateHeaders one or more tracestate header values
     * @throws InvalidTraceContextException when traceparent or tracestate is invalid
     */
    public TraceContextValidator(final String traceparent, final String... tracestateHeaders) throws InvalidTraceContextException {
        this(traceparent, combineTracestateHeaders(tracestateHeaders));
    }

    /**
     * Validates a complete {@code traceparent} value.
     *
     * @param traceparent the complete traceparent value in
     *                    {@code version-trace-id-parent-id-trace-flags} format
     * @return {@code true} if valid, otherwise {@code false}
     */
    public static boolean isValidTraceparent(final String traceparent) {
        return parseTraceparent(traceparent) != null;
    }

    /**
     * Validates a complete {@code traceparent} value and checks consistency
     * with a separately provided {@code trace-id}.
     *
     * @param traceparent the complete traceparent value
     * @param traceId     a trace-id that must match the one inside traceparent
     * @return {@code true} if both are valid and consistent, otherwise {@code false}
     */
    public static boolean isValidTraceparent(final String traceparent, final String traceId) {
        String normalizedTraceId = normalizeLowerHexIdToLength(traceId, TRACE_ID_LENGTH);
        if (normalizedTraceId == null) {
            return false;
        }

        ParsedTraceparent parsed = parseTraceparent(traceparent);
        return parsed != null && parsed.traceId.equals(normalizedTraceId);
    }

    /**
     * Validates a {@code trace-id}.
     * <p>
     * For interoperability, shorter lower-hex IDs are accepted and normalized
     * by left-padding with zeroes up to 32 characters.
     *
     * @param traceId trace-id to validate
     * @return {@code true} when valid
     */
    public static boolean isValidTraceId(final String traceId) {
        return normalizeLowerHexIdToLength(traceId, TRACE_ID_LENGTH) != null;
    }

    /**
     * Validates a {@code parent-id}.
     * <p>
     * For interoperability, shorter lower-hex IDs are accepted and normalized
     * by left-padding with zeroes up to 16 characters.
     *
     * @param parentId parent-id to validate
     * @return {@code true} when valid
     */
    public static boolean isValidParentId(final String parentId) {
        return normalizeLowerHexIdToLength(parentId, PARENT_ID_LENGTH) != null;
    }

    /**
     * Validates a {@code tracestate} value.
     *
     * @param tracestate tracestate to validate
     * @return {@code true} when valid
     */
    public static boolean isValidTracestate(final String tracestate) {
        try {
            parseTracestate(tracestate);
            return true;
        } catch (InvalidTraceContextException ex) {
            return false;
        }
    }

    /**
     * Combines multiple {@code tracestate} header field values in field order according to RFC7230 section 3.2.2.
     *
     * @param tracestateHeaders tracestate header values
     * @return combined tracestate value (empty string when no values are provided)
     */
    public static String combineTracestateHeaders(final String... tracestateHeaders) {
        if (tracestateHeaders == null || tracestateHeaders.length == 0) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (String tracestateHeader : tracestateHeaders) {
            if (tracestateHeader == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(tracestateHeader);
        }
        return builder.toString();
    }

    /**
     * Creates a trace context from incoming headers using the W3C processing model.
     * <p>
     * If {@code traceparent} is absent or invalid, a new context is created and tracestate is discarded.
     * If {@code traceparent} is valid but {@code tracestate} is invalid, tracestate is discarded.
     *
     * @param traceparent incoming traceparent value
     * @param tracestate  incoming tracestate value
     * @return a valid trace context ready for propagation
     */
    public static TraceContextValidator fromIncomingHeaders(final String traceparent, final String tracestate) {
        if (traceparent == null || traceparent.isEmpty()) {
            return new TraceContextValidator();
        }

        ParsedTraceparent parsed = parseTraceparent(traceparent);
        if (parsed == null) {
            return new TraceContextValidator();
        }

        LinkedHashMap<String, String> parsedTracestate;
        try {
            parsedTracestate = parseTracestate(tracestate);
        } catch (InvalidTraceContextException ex) {
            parsedTracestate = new LinkedHashMap<>();
        }

        return new TraceContextValidator(parsed, parsedTracestate);
    }

    /**
     * Creates a trace context from incoming headers using the W3C processing model,
     * combining multiple tracestate fields according to RFC7230 field order.
     *
     * @param traceparent       incoming traceparent value
     * @param tracestateHeaders incoming tracestate header values
     * @return a valid trace context ready for propagation
     */
    public static TraceContextValidator fromIncomingHeaders(final String traceparent, final String... tracestateHeaders) {
        return fromIncomingHeaders(traceparent, combineTracestateHeaders(tracestateHeaders));
    }

    /**
     * Normalizes a potentially short lower-hex trace identifier to a compliant 32-char trace-id by left-padding with zeroes.
     *
     * @param traceId trace identifier to normalize
     * @return compliant 32-char trace-id
     * @throws InvalidTraceContextException when trace-id is invalid
     */
    public static String toCompliantTraceId(final String traceId) throws InvalidTraceContextException {
        String normalized = normalizeLowerHexIdToLength(traceId, TRACE_ID_LENGTH);
        if (normalized == null) {
            throw new InvalidTraceContextException("Invalid trace-id");
        }
        return normalized;
    }

    /**
     * Extracts a shorter internal identifier from the right-most portion of a valid trace-id.
     *
     * @param traceId      trace identifier to extract from
     * @param targetLength desired short identifier length
     * @return right-most {@code targetLength} characters
     * @throws InvalidTraceContextException when trace-id or target length is invalid
     */
    public static String extractShortTraceId(final String traceId, final int targetLength) throws InvalidTraceContextException {
        String normalized = toCompliantTraceId(traceId);
        if (targetLength <= 0 || targetLength > TRACE_ID_LENGTH) {
            throw new InvalidTraceContextException("Invalid target length");
        }
        return normalized.substring(TRACE_ID_LENGTH - targetLength);
    }

    /**
     * Adds or updates a vendor entry in {@code tracestate}.
     * <p>
     * New or updated entries are placed at the beginning, as required by the specification.
     * If the list exceeds 32 members, right-most entries are dropped.
     *
     * @param vendorName  tracestate vendor key
     * @param vendorValue tracestate vendor value
     * @throws InvalidTraceContextException when a vendor key or value is invalid
     */
    public void upsertVendorEntry(final String vendorName, final String vendorValue) throws InvalidTraceContextException {
        if (isNotValidTracestateKey(vendorName)) {
            throw new InvalidTraceContextException("Invalid tracestate vendor key");
        }
        if (isNotValidTracestateValue(vendorValue)) {
            throw new InvalidTraceContextException("Invalid tracestate vendor value");
        }

        LinkedHashMap<String, String> reordered = new LinkedHashMap<>();
        reordered.put(vendorName, vendorValue);

        for (Map.Entry<String, String> entry : tracestateEntries.entrySet()) {
            if (!entry.getKey().equals(vendorName)) {
                reordered.put(entry.getKey(), entry.getValue());
            }
        }

        tracestateEntries.clear();
        tracestateEntries.putAll(applyTracestateLimits(reordered));
    }

    /**
     * @return the current valid trace-id
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * @return the current valid parent-id
     */
    public String getParentId() {
        return parentId;
    }

    /**
     * @return the normalized trace-flags as a lowercase hex string ({@code "00"} or {@code "01"})
     */
    public String getTraceFlags() {
        return traceFlags;
    }

    /**
     * @return the normalized trace-flags as a byte value
     */
    public byte getTraceFlagsByte() {
        return (byte) Integer.parseInt(traceFlags, 16);
    }

    /**
     * @return a valid traceparent value
     */
    public String getTraceparentValue() {
        return CURRENT_VERSION + "-" + traceId + "-" + parentId + "-" + traceFlags;
    }

    /**
     * Convenience alias for {@link #getTraceparentValue()}.
     *
     * @return a valid trace value
     */
    public String getTraceValue() {
        return getTraceparentValue();
    }

    /**
     * @return a normalized tracestate value, or empty string when absent
     */
    public String getTracestateValue() {
        return formatTracestate(tracestateEntries);
    }

    /**
     * @return complete HTTP header lines for trace context (traceparent, plus tracestate when present)
     */
    public String getHttpHeaderValue() {
        String traceparentHeader = TRACEPARENT_HEADER + ": " + getTraceparentValue();
        String tracestate = getTracestateValue();
        if (tracestate.isEmpty()) {
            return traceparentHeader;
        }
        return traceparentHeader + "\r\n" + TRACESTATE_HEADER + ": " + tracestate;
    }

    /**
     * Convenience alias for {@link #getHttpHeaderValue()}.
     *
     * @return complete HTTP header lines for trace context
     */
    public String getCompleteHeaderValue() {
        return getHttpHeaderValue();
    }

    private TraceContextValidator(final ParsedTraceparent parsed, final LinkedHashMap<String, String> parsedTracestate) {
        this.traceId = parsed.traceId.toLowerCase(Locale.ROOT);
        this.parentId = parsed.parentId.toLowerCase(Locale.ROOT);
        this.traceFlags = normalizeTraceFlagsForOutgoing(parsed.traceFlags);
        this.tracestateEntries.putAll(applyTracestateLimits(parsedTracestate));
    }

    private static ParsedTraceparent parseTraceparent(final String traceparent) {
        ParsedTraceparent canonicalParsed = parseCanonicalTraceparent(traceparent);
        if (canonicalParsed != null) {
            return canonicalParsed;
        }
        return parseShortVersion00Traceparent(traceparent);
    }

    private static ParsedTraceparent parseCanonicalTraceparent(final String traceparent) {
        if (traceparent == null || traceparent.length() < TRACEPARENT_V00_LENGTH) {
            return null;
        }

        if (traceparent.charAt(2) != '-' || traceparent.charAt(35) != '-' || traceparent.charAt(52) != '-') {
            return null;
        }

        String version = traceparent.substring(0, 2);
        if (isNotValidHex(version, 2) || "ff".equalsIgnoreCase(version)) {
            return null;
        }

        String traceId = traceparent.substring(3, 35);
        String parentId = traceparent.substring(36, 52);
        String traceFlags = traceparent.substring(53, 55);

        if ("00".equals(version)) {
            if (traceparent.length() != TRACEPARENT_V00_LENGTH) {
                return null;
            }
            if (!isValidTraceId(traceId) || !isValidParentId(parentId) || isNotValidLowerHex(traceFlags)) {
                return null;
            }
            return new ParsedTraceparent(traceId, parentId, traceFlags);
        }

        if (isNotValidHex(traceId, TRACE_ID_LENGTH) || isAllZeroes(traceId)) {
            return null;
        }
        if (isNotValidHex(parentId, PARENT_ID_LENGTH) || isAllZeroes(parentId)) {
            return null;
        }
        if (isNotValidHex(traceFlags, TRACE_FLAGS_LENGTH)) {
            return null;
        }
        if (traceparent.length() > TRACEPARENT_V00_LENGTH && traceparent.charAt(TRACEPARENT_V00_LENGTH) != '-') {
            return null;
        }

        return new ParsedTraceparent(
                traceId.toLowerCase(Locale.ROOT),
                parentId.toLowerCase(Locale.ROOT),
                traceFlags.toLowerCase(Locale.ROOT));
    }

    private static ParsedTraceparent parseShortVersion00Traceparent(final String traceparent) {
        if (traceparent == null) {
            return null;
        }

        String[] parts = traceparent.split("-", -1);
        if (parts.length != 4 || !CURRENT_VERSION.equals(parts[0])) {
            return null;
        }

        if (parts[1].length() >= TRACE_ID_LENGTH && parts[2].length() >= PARENT_ID_LENGTH) {
            return null;
        }

        String normalizedTraceId = normalizeLowerHexIdToLength(parts[1], TRACE_ID_LENGTH);
        if (normalizedTraceId == null) {
            return null;
        }

        String normalizedParentId = normalizeLowerHexIdToLength(parts[2], PARENT_ID_LENGTH);
        if (normalizedParentId == null) {
            return null;
        }

        String traceFlags = parts[3];
        if (isNotValidLowerHex(traceFlags)) {
            return null;
        }

        return new ParsedTraceparent(normalizedTraceId, normalizedParentId, traceFlags);
    }

    private static LinkedHashMap<String, String> parseTracestate(final String tracestate) throws InvalidTraceContextException {
        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        if (tracestate == null) {
            return entries;
        }

        String[] members = tracestate.split(",", -1);
        if (members.length > MAX_TRACESTATE_MEMBERS) {
            throw new InvalidTraceContextException("tracestate contains too many list-members");
        }

        for (String rawMember : members) {
            String member = trimOws(rawMember);
            if (member.isEmpty()) {
                continue;
            }

            int equalsIndex = member.indexOf('=');
            if (equalsIndex <= 0 || equalsIndex != member.lastIndexOf('=')) {
                throw new InvalidTraceContextException("Invalid tracestate list-member");
            }

            String key = member.substring(0, equalsIndex);
            String value = member.substring(equalsIndex + 1);

            if (isNotValidTracestateKey(key)) {
                throw new InvalidTraceContextException("Invalid tracestate key");
            }
            if (isNotValidTracestateValue(value)) {
                throw new InvalidTraceContextException("Invalid tracestate value");
            }
            if (entries.containsKey(key)) {
                throw new InvalidTraceContextException("Duplicate tracestate key");
            }

            entries.put(key, value);
        }

        return entries;
    }

    private static String trimOws(final String value) {
        int start = 0;
        int end = value.length();

        while (start < end && isOws(value.charAt(start))) {
            start++;
        }
        while (end > start && isOws(value.charAt(end - 1))) {
            end--;
        }

        return value.substring(start, end);
    }

    private static boolean isOws(final char c) {
        return c == ' ' || c == '\t';
    }

    private static boolean isNotValidTracestateKey(final String key) {
        if (key == null || key.isEmpty() || key.length() > MAX_TRACESTATE_KEY_LENGTH) {
            return true;
        }

        int atIndex = key.indexOf('@');
        if (atIndex < 0) {
            return !isValidSimpleKey(key);
        }
        if (atIndex != key.lastIndexOf('@')) {
            return true;
        }

        String tenantId = key.substring(0, atIndex);
        String systemId = key.substring(atIndex + 1);
        return !isValidTenantId(tenantId) || !isValidSystemId(systemId);
    }

    private static boolean isValidSimpleKey(final String key) {
        if (key.isEmpty() || isNotLowerAlpha(key.charAt(0))) {
            return false;
        }

        for (int i = 1; i < key.length(); i++) {
            if (isNotValidKeyChar(key.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static boolean isValidTenantId(final String tenantId) {
        if (tenantId.isEmpty() || tenantId.length() > 241) {
            return false;
        }
        char first = tenantId.charAt(0);
        if (isNotLowerAlpha(first) && isNotDigit(first)) {
            return false;
        }

        for (int i = 1; i < tenantId.length(); i++) {
            if (isNotValidKeyChar(tenantId.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static boolean isValidSystemId(final String systemId) {
        if (systemId.isEmpty() || systemId.length() > 14 || isNotLowerAlpha(systemId.charAt(0))) {
            return false;
        }

        for (int i = 1; i < systemId.length(); i++) {
            if (isNotValidKeyChar(systemId.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static boolean isNotValidKeyChar(final char c) {
        return isNotLowerAlpha(c) && isNotDigit(c) && c != '_' && c != '-' && c != '*' && c != '/';
    }

    private static boolean isNotLowerAlpha(final char c) {
        return c < 'a' || c > 'z';
    }

    private static boolean isNotDigit(final char c) {
        return c < '0' || c > '9';
    }

    private static boolean isNotValidTracestateValue(final String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_TRACESTATE_VALUE_LENGTH) {
            return true;
        }
        if (value.charAt(value.length() - 1) == ' ') {
            return true;
        }

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ','
                    || c == '='
                    || (c != ' ' && (c < 0x21 || c > 0x7E))) {
                return true;
            }
        }

        return false;
    }

    private static String formatTracestate(final LinkedHashMap<String, String> entries) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static void removeLastEntry(final LinkedHashMap<String, String> entries) {
        String lastKey = null;
        for (String key : entries.keySet()) {
            lastKey = key;
        }
        if (lastKey != null) {
            entries.remove(lastKey);
        }
    }

    private static String normalizeTraceFlagsForOutgoing(final String traceFlags) {
        int flags = Integer.parseInt(traceFlags, 16);
        return (flags & 0x01) == 0x01 ? "01" : "00";
    }

    private static boolean isNotValidHex(final String value, final int requiredLength) {
        if (value == null || value.length() != requiredLength) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!isHexCharacter(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNotValidLowerHex(final String value) {
        if (value == null || value.length() != TraceContextValidator.TRACE_FLAGS_LENGTH) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            if (isNotLowerHexCharacter(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNotLowerHexCharacter(final char c) {
        return (c < '0' || c > '9') && (c < 'a' || c > 'f');
    }

    private static boolean isHexCharacter(final char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
    }

    private static LinkedHashMap<String, String> applyTracestateLimits(final LinkedHashMap<String, String> source) {
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>(source);

        removeEntriesLongerThan128(normalized);

        while (normalized.size() > MAX_TRACESTATE_MEMBERS) {
            removeLastEntry(normalized);
        }

        while (calculateTracestateLength(normalized) > MAX_TRACESTATE_COMBINED_LENGTH) {
            removeLastEntry(normalized);
        }

        return normalized;
    }

    private static void removeEntriesLongerThan128(final LinkedHashMap<String, String> entries) {
        LinkedHashMap<String, String> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (calculateListMemberLength(entry.getKey(), entry.getValue()) <= TraceContextValidator.TRUNCATION_PRIORITY_MEMBER_LENGTH) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }

        entries.clear();
        entries.putAll(filtered);
    }

    private static int calculateTracestateLength(final LinkedHashMap<String, String> entries) {
        int totalLength = 0;
        boolean first = true;
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (!first) {
                totalLength++;
            }
            totalLength += calculateListMemberLength(entry.getKey(), entry.getValue());
            first = false;
        }
        return totalLength;
    }

    private static int calculateListMemberLength(final String key, final String value) {
        return key.length() + 1 + value.length();
    }

    private static String normalizeLowerHexIdToLength(final String id, final int requiredLength) {
        if (id == null || id.isEmpty() || id.length() > requiredLength || !isLowerHex(id)) {
            return null;
        }
        String normalized = leftPadWithZeroes(id, requiredLength);
        if (isAllZeroes(normalized)) {
            return null;
        }
        return normalized;
    }

    private static String leftPadWithZeroes(final String value, final int requiredLength) {
        if (value.length() >= requiredLength) {
            return value;
        }

        StringBuilder builder = new StringBuilder(requiredLength);
        for (int i = value.length(); i < requiredLength; i++) {
            builder.append('0');
        }
        builder.append(value);
        return builder.toString();
    }

    private static boolean isLowerHex(final String value) {
        for (int i = 0; i < value.length(); i++) {
            if (isNotLowerHexCharacter(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAllZeroes(final String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }

    private static final class ParsedTraceparent {
        private final String traceId;
        private final String parentId;
        private final String traceFlags;

        private ParsedTraceparent(final String traceId, final String parentId, final String traceFlags) {
            this.traceId = traceId;
            this.parentId = parentId;
            this.traceFlags = traceFlags;
        }
    }
}
