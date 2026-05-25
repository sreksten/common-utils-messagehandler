package com.threeamigos.common.util.implementations.messagehandler.filters;

import com.threeamigos.common.util.implementations.messagehandler.InnerErrorMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.MessageHandlerResourceBundle;
import com.threeamigos.common.util.implementations.messagehandler.utils.ParametersValidator;
import com.threeamigos.common.util.interfaces.messagehandler.otel.*;
import jakarta.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * A filter for LogRecords based on the function or class name. It uses Java's Regex to match the LogRecord's
 * class name and checks if its SeverityNumber is compatible with the required SeverityNumber for a
 * specific class or package. Longer Regex patterns are checked first.
 * <p>
 * A minimum required SeverityNumber is determined for each class or package name based on a configuration map. E.g.:<br/>
 * <code>
 * com.threeamigos.*=DEBUG<br/>
 * com.threeamigos.packagenottolog=OFF<br/>
 * .*=INFO<br/>
 * </code>
 * The '.*' is optional, but it serves as a placeholder for a global minimum SeverityNumber.<br/>
 * OFF can be used to filter off a whole package or any class not explicitly specified, if used in combination with
 * the '.*' pattern.<br/>
 * To check if a LogRecord should be filtered out:
 * The <code>severityNumber</code> field is extracted from the LogRecord.
 * If the record has an <code>UNSPECIFIED</code> SeverityNumber, it is not filtered at all.
 * Then the class name is extracted and matched against the configuration map.
 * To determine the class name, the LogRecord's <code>code.function.name</code> is extracted and stripped of the
 * method's name. If absent, the <code>code.namespace</code> is considered (even if this field is deprecated).
 * If there is a match, the record SeverityNumber is checked against the minimum SeverityNumber specified for its class.
 * If the SeverityNumber is greater or equal than the minimum SeverityNumber specified for its class,
 * the record is returned without being filtered.<br/>
 * If there is NOT a match, the SeverityNumber is compared against the minimum global SeverityNumber if the latter
 * is defined. Otherwise, the record is returned without being filtered.
 *
 * @author Stefano Reksten
 */
public class FilterByClassName implements Filter {

    private static final String CLASS_PREFIX = "class.";
    private static final String SEVERITY_PREFIX = "severity.";
    private static final String OFF = "OFF";

    private static final Comparator<RegexHolder> regexComparator = new RegexComparator();

    private final TreeMap<RegexHolder, SeverityNumber> classSeverityMap = new TreeMap<>(regexComparator);

    private final TreeMap<RegexHolder, Object> prunedClasses = new TreeMap<>(regexComparator);

    /**
     * Loads class-severity mappings from a properties file path.
     *
     * @param filename properties filename/path
     * @throws NullPointerException if {@code filename} is {@code null}
     * @throws IllegalArgumentException if {@code filename} is blank
     * @throws IOException if the file cannot be opened or read
     */
    public void loadPropertiesFromFile(final String filename) throws IOException {
        String trimmedFilename = ParametersValidator.validateNotBlank(filename, "filename");
        loadProperties(new File(trimmedFilename));
    }

    /**
     * Loads class-severity mappings from a classpath resource.
     *
     * @param resourceName classpath resource name/path
     * @throws NullPointerException if {@code resourceName} is {@code null}
     * @throws IllegalArgumentException if {@code resourceName} is blank or resource is not found
     * @throws IOException if the resource cannot be read
     */
    public void loadPropertiesFromResource(final String resourceName) throws IOException {
        String trimmedResourceName = ParametersValidator.validateNotBlank(resourceName, "resourceName");
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(trimmedResourceName);
        if (inputStream == null) {
            throw new IllegalArgumentException(
                    MessageHandlerResourceBundle.format("resourceNotFound", trimmedResourceName));
        }
        try (InputStream stream = inputStream) {
            loadProperties(stream);
        }
    }

    /**
     * Loads class-severity mappings from a properties file.
     *
     * @param file properties file
     * @throws NullPointerException if {@code file} is {@code null}
     * @throws IOException if the file cannot be opened or read
     */
    public void loadProperties(final File file) throws IOException {
        ParametersValidator.validateNotNull(file, "file");
        try (InputStream inputStream = new FileInputStream(file)) {
            loadProperties(inputStream);
        }
    }

    /**
     * Loads class-severity mappings from a properties {@link InputStream}.
     * <p>
     * The stream is consumed immediately and not closed by this method.
     *
     * @param inputStream input stream containing Java properties
     * @throws NullPointerException if {@code inputStream} is {@code null}
     * @throws IOException if the properties cannot be loaded
     */
    public void loadProperties(final InputStream inputStream) throws IOException {
        ParametersValidator.validateNotNull(inputStream, "inputStream");
        Properties properties = new Properties();
        properties.load(inputStream);
        loadProperties(properties);
    }

    /**
     * Adds multiple class severity mappings from a collection of properties.
     * The property key must be a package or class name.
     * The property value must be a valid severity level name or its numeric value.
     * @param properties collection of class severity mappings
     */
    public void loadProperties(Properties properties) {
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String key = entry.getKey().toString();
            if (key.startsWith(CLASS_PREFIX)) {
                key = key.substring(CLASS_PREFIX.length());
            }
            String value = entry.getValue().toString();
            if (OFF.equalsIgnoreCase(value)) {
                prune(key);
            } else {
                if (value.startsWith(SEVERITY_PREFIX)) {
                    value = value.substring(SEVERITY_PREFIX.length());
                }
                SeverityNumber severityNumber = parseSeverityNumberName(value);
                if (severityNumber == null) {
                    severityNumber = parseSeverityNumberValue(value);
                }
                if (severityNumber == null) {
                    continue;
                }
                add(key, severityNumber);
            }
        }
    }

    /**
     * Completely disables logging for a class.
     * @param className name of a package or of a class
     */
    public void prune(String className) {
        if (className == null || className.isEmpty()) {
            return;
        }
        RegexHolder regexHolder = createRegexHolderOrNull(className, "pruning class filter");
        if (regexHolder == null) {
            return;
        }
        prunedClasses.put(regexHolder, null);
        classSeverityMap.remove(regexHolder);
    }

    /**
     * Adds a class severity mapping.
     * @param className name of a package or of a class
     * @param severityNumber minimum severity level for logs from this class
     */
    public void add(String className, SeverityNumber severityNumber) {
        if (className == null || className.isEmpty()) {
            return;
        }
        RegexHolder regexHolder = createRegexHolderOrNull(className, "adding class filter");
        if (regexHolder == null) {
            return;
        }
        if (severityNumber == null) {
            classSeverityMap.remove(regexHolder);
        } else {
            classSeverityMap.put(regexHolder, severityNumber);
        }
    }

    /**
     * Adds multiple class severity mappings from a collection of entries.
     * The entry key must be a package or class name.
     * @param entries collection of class severity mappings
     */
    public void add(Collection<Map.Entry<String, SeverityNumber>> entries) {
        for (Map.Entry<String, SeverityNumber> entry : entries) {
            add(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Gets a list of pruned classes
     */
    public Collection<String> getPrunedClasses() {
        return Collections.unmodifiableSet(prunedClasses.keySet().stream().map(RegexHolder::getOriginalRegex).collect(Collectors.toSet()));
    }

    /**
     * Gets the class severity map, which maps class names to their minimum severity levels.
     * @return unmodifiable map of class severity levels
     */
    public Map<String, SeverityNumber> getClassSeverityMap() {
        return Collections.unmodifiableMap(classSeverityMap.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().getOriginalRegex(), Map.Entry::getValue)));
    }

    /**
     * Filters a log record based on the configured class severity map.
     * @param logRecord log record to filter
     * @return filtered log record, or null if the record should be discarded
     */
    @Override
    public @Nullable LogRecord filter(@Nullable LogRecord logRecord) {
        // The record may already have been filtered by another filter
        if (logRecord == null) {
            return null;
        }

        SeverityNumber logRecordSeverityNumber = logRecord.getSeverityNumber();

        // We did not find a severity number in the LogRecord, so we are not filtering it.
        if (logRecordSeverityNumber == SeverityNumber.UNSPECIFIED) {
            return logRecord;
        }

        String className = searchByFunctionName(logRecord);
        if (className == null) {
            className = searchByClassName(logRecord);
        }
        if (className == null) {
            className = "";
        }

        if (isPruned(className)) {
            return null;
        }

        SeverityNumber requiredSeverityNumber = getSeverity(className);
        if (isLogRecordSeverityCompatibleWith(logRecordSeverityNumber, requiredSeverityNumber)) {
            return logRecord;
        } else {
            return null;
        }
    }

    private SeverityNumber parseSeverityNumberName(String string) {
        for (SeverityNumber severityNumber : SeverityNumber.values()) {
            if (string.equals(severityNumber.name())) {
                return severityNumber;
            }
        }
        return null;
    }

    private SeverityNumber parseSeverityNumberValue(String string) {
        try {
            long value = Long.parseLong(string);
            if (value >= SeverityNumber.UNSPECIFIED.getValue() && value <= SeverityNumber.FATAL4.getValue()) {
                return SeverityNumber.fromValue((int) value);
            }
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private RegexHolder createRegexHolderOrNull(final String regex, final String operation) {
        try {
            return new RegexHolder(regex);
        } catch (PatternSyntaxException patternSyntaxException) {
            reportInvalidRegex(regex, operation, patternSyntaxException);
            return null;
        }
    }

    private static void reportInvalidRegex(final String regex,
                                           final String operation,
                                           final PatternSyntaxException patternSyntaxException) {
        String message = "Ignoring invalid regex pattern '" + regex + "' while " + operation
                + ": " + patternSyntaxException.getMessage();
        InnerErrorMessageHandler.consume(message);
    }

    private String searchByFunctionName(LogRecord logRecord) {
        Optional<String> functionNameOpt = logRecord.getAttributes().stream()
                .filter(ka -> ka.getKey().equals("code.function.name"))
                .map(KeyValue::getValue)
                .map(AnyValue::asString)
                .findFirst();

        if (functionNameOpt.isPresent()) {
            String functionName = functionNameOpt.get();
            int lastDot = functionName.lastIndexOf('.');
            if (lastDot > 0) {
                return functionName.substring(0, lastDot);
            } else {
                return null;
            }
        }
        return null;
    }

    private String searchByClassName(LogRecord logRecord) {
        return logRecord.getAttributes().stream()
                .filter(ka -> ka.getKey().equals("code.namespace"))
                .map(KeyValue::getValue)
                .map(AnyValue::asString)
                .findFirst()
                .orElse(null);
    }

    protected boolean isPruned(String className) {
        for (RegexHolder regexHolder : prunedClasses.keySet()) {
            if (regexHolder.matches(className)) {
                return true;
            }
        }
        return false;
    }

    protected SeverityNumber getSeverity(String className) {
        for (Map.Entry<RegexHolder, SeverityNumber> entry : classSeverityMap.entrySet()) {
            RegexHolder mapKey = entry.getKey();
            if (mapKey.matches(className)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean isLogRecordSeverityCompatibleWith(SeverityNumber logRecordSeverityNumber, SeverityNumber requiredSeverityNumber) {
        if (requiredSeverityNumber == null) {
            return true;
        }
        return logRecordSeverityNumber.compareTo(requiredSeverityNumber) >= 0;
    }

    private static class RegexHolder {
        String originalRegex;
        Pattern pattern;

        public RegexHolder(String regex) {
            this.originalRegex = regex;
            this.pattern = Pattern.compile(regex);
        }

        public String getOriginalRegex() {
            return originalRegex;
        }

        boolean matches(String className) {
            return pattern.matcher(className).matches();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof RegexHolder))
                return false;
            RegexHolder other = (RegexHolder) obj;
            return other.originalRegex.equals(originalRegex);
        }

        @Override
        public int hashCode() {
            return originalRegex.hashCode();
        }
    }

    private static final class RegexComparator implements Comparator<RegexHolder> {
        @Override
        public int compare(final RegexHolder left, final RegexHolder right) {
            int lengthComparison = Integer.compare(right.originalRegex.length(), left.originalRegex.length());
            if (lengthComparison != 0) {
                return lengthComparison;
            }
            return Comparator.<String>naturalOrder().compare(left.originalRegex, right.originalRegex);
        }
    }
}
