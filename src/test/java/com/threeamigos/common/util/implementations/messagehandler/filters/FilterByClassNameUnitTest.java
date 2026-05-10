package com.threeamigos.common.util.implementations.messagehandler.filters;

import com.threeamigos.common.util.implementations.messagehandler.otel.AnyValueFactory;
import com.threeamigos.common.util.implementations.messagehandler.otel.KeyValueFactory;
import com.threeamigos.common.util.implementations.messagehandler.otel.LogRecordImpl;
import com.threeamigos.common.util.interfaces.messagehandler.otel.KeyValue;
import com.threeamigos.common.util.interfaces.messagehandler.otel.SeverityNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FilterByClassName unit tests")
@Tag("unit")
@Tag("messageHandler")
class FilterByClassNameUnitTest {

    @Test
    @DisplayName("add() and prune() should handle null/empty inputs and remove mappings when needed")
    void addAndPruneShouldHandleNullEmptyAndRemoval() {
        FilterByClassName filter = new FilterByClassName();

        filter.add(null, SeverityNumber.INFO);
        filter.add("", SeverityNumber.INFO);
        filter.prune(null);
        filter.prune("");

        assertTrue(filter.getClassSeverityMap().isEmpty());
        assertTrue(filter.getPrunedClasses().isEmpty());

        filter.add("com\\.acme\\..*", SeverityNumber.DEBUG);
        assertEquals(SeverityNumber.DEBUG, filter.getClassSeverityMap().get("com\\.acme\\..*"));

        filter.add("com\\.acme\\..*", null);
        assertTrue(filter.getClassSeverityMap().isEmpty());

        filter.add("com\\.block\\..*", SeverityNumber.INFO);
        filter.prune("com\\.block\\..*");
        assertFalse(filter.getClassSeverityMap().containsKey("com\\.block\\..*"));
        assertTrue(filter.getPrunedClasses().contains("com\\.block\\..*"));
    }

    @Test
    @DisplayName("add(Collection) should apply each entry")
    void addCollectionShouldApplyEachEntry() {
        FilterByClassName filter = new FilterByClassName();
        Collection<Map.Entry<String, SeverityNumber>> entries = new ArrayList<>();
        entries.add(new AbstractMap.SimpleEntry<>("com\\.one\\..*", SeverityNumber.INFO));
        entries.add(new AbstractMap.SimpleEntry<>(null, SeverityNumber.WARN));
        entries.add(new AbstractMap.SimpleEntry<>("com\\.two\\..*", SeverityNumber.ERROR));

        filter.add(entries);

        assertEquals(2, filter.getClassSeverityMap().size());
        assertEquals(SeverityNumber.INFO, filter.getClassSeverityMap().get("com\\.one\\..*"));
        assertEquals(SeverityNumber.ERROR, filter.getClassSeverityMap().get("com\\.two\\..*"));
    }

    @Test
    @DisplayName("loadProperties(Properties) should support prefixes, OFF, names, numeric values and ignore invalid values")
    void loadPropertiesFromFileShouldHandleAllParsingPaths() {
        FilterByClassName filter = new FilterByClassName();
        Properties properties = new Properties();
        properties.setProperty("class.com\\.prefixed\\..*", "severity.DEBUG");
        properties.setProperty("com\\.name\\..*", "ERROR");
        properties.setProperty("class.com\\.numeric\\..*", "10");
        properties.setProperty("com\\.off\\..*", "off");
        properties.setProperty("class.com\\.invalid\\..*", "NOT_A_LEVEL");
        properties.setProperty("class.com\\.outofrange\\..*", "999");
        properties.setProperty("class.com\\.negative\\..*", "-1");

        filter.loadProperties(properties);

        assertEquals(3, filter.getClassSeverityMap().size());
        assertEquals(SeverityNumber.DEBUG, filter.getClassSeverityMap().get("com\\.prefixed\\..*"));
        assertEquals(SeverityNumber.ERROR, filter.getClassSeverityMap().get("com\\.name\\..*"));
        assertEquals(SeverityNumber.INFO2, filter.getClassSeverityMap().get("com\\.numeric\\..*"));
        assertTrue(filter.getPrunedClasses().contains("com\\.off\\..*"));
        assertFalse(filter.getClassSeverityMap().containsKey("com\\.invalid\\..*"));
        assertFalse(filter.getClassSeverityMap().containsKey("com\\.outofrange\\..*"));
        assertFalse(filter.getClassSeverityMap().containsKey("com\\.negative\\..*"));
    }

    @Test
    @DisplayName("loadProperties(InputStream) should load properties and apply rules")
    void loadPropertiesInputStreamShouldLoadAndApplyRulesFromFile() throws IOException {
        FilterByClassName filter = new FilterByClassName();
        String content = "class.com\\\\.stream\\\\..*=severity.WARN\n" +
                "com\\\\.streamoff\\\\..*=OFF\n";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        filter.loadProperties(inputStream);

        assertEquals(SeverityNumber.WARN, filter.getClassSeverityMap().get("com\\.stream\\..*"));
        assertTrue(filter.getPrunedClasses().contains("com\\.streamoff\\..*"));
    }

    @Test
    @DisplayName("loadProperties(File) should load properties and fail fast on invalid input")
    void loadPropertiesFileShouldLoadAndFailFastFromFile() throws IOException {
        FilterByClassName filter = new FilterByClassName();
        File file = Files.createTempFile("filter-by-class", ".properties").toFile();
        Files.write(file.toPath(),
                ("class.com\\\\.file\\\\..*=severity.ERROR\n").getBytes(StandardCharsets.UTF_8));
        try {
            filter.loadProperties(file);
            assertEquals(SeverityNumber.ERROR, filter.getClassSeverityMap().get("com\\.file\\..*"));
        } finally {
            Files.deleteIfExists(file.toPath());
        }

        assertThrows(NullPointerException.class, () -> filter.loadProperties((File) null));
        assertThrows(IOException.class, () -> filter.loadProperties(new File("missing-file-" + System.nanoTime() + ".properties")));
    }

    @Test
    @DisplayName("loadPropertiesFromFile(String) should load properties and fail fast on invalid input")
    void loadPropertiesStringShouldLoadAndFailFastFromFile() throws IOException {
        FilterByClassName filter = new FilterByClassName();
        File file = Files.createTempFile("filter-by-class-name", ".properties").toFile();
        Files.write(file.toPath(),
                ("class.com\\\\.filename\\\\..*=INFO\n").getBytes(StandardCharsets.UTF_8));
        try {
            filter.loadPropertiesFromFile(file.getAbsolutePath());
            assertEquals(SeverityNumber.INFO, filter.getClassSeverityMap().get("com\\.filename\\..*"));
        } finally {
            Files.deleteIfExists(file.toPath());
        }

        assertThrows(NullPointerException.class, () -> filter.loadPropertiesFromFile(null));
        assertThrows(IllegalArgumentException.class, () -> filter.loadPropertiesFromFile(" "));
        assertThrows(IOException.class, () -> filter.loadPropertiesFromFile("missing-file-" + System.nanoTime() + ".properties"));
    }

    @Test
    @DisplayName("loadPropertiesFromResource(String) should load properties and fail fast on invalid input")
    void loadPropertiesFromResourceShouldLoadAndFailFast() throws IOException {
        FilterByClassName filter = new FilterByClassName();

        filter.loadPropertiesFromResource("otel/filters/filter-by-classname-test.properties");
        assertEquals(SeverityNumber.WARN, filter.getClassSeverityMap().get("com\\.resource\\..*"));
        assertTrue(filter.getPrunedClasses().contains("com\\.resourceoff\\..*"));

        assertThrows(NullPointerException.class, () -> filter.loadPropertiesFromResource(null));
        assertThrows(IllegalArgumentException.class, () -> filter.loadPropertiesFromResource(" "));
        assertThrows(IllegalArgumentException.class, () -> filter.loadPropertiesFromResource("otel/filters/missing.properties"));
    }

    @Test
    @DisplayName("filter() should return null for null log record")
    void filterShouldReturnNullForNullRecord() {
        FilterByClassName filter = new FilterByClassName();
        assertNull(filter.filter(null));
    }

    @Test
    @DisplayName("filter() should not filter when severity is UNSPECIFIED")
    void filterShouldSkipWhenSeverityIsUnspecified() {
        FilterByClassName filter = new FilterByClassName();
        filter.add(".*", SeverityNumber.FATAL);

        LogRecordImpl record = new LogRecordImpl();
        record.setSeverityNumber(SeverityNumber.UNSPECIFIED);

        assertSame(record, filter.filter(record));
    }

    @Test
    @DisplayName("filter() should extract class from code.function.name and accept compatible severity")
    void filterShouldUseFunctionNameWhenPresent() {
        FilterByClassName filter = new FilterByClassName();
        filter.add("com\\.example\\.service\\..*", SeverityNumber.INFO);

        LogRecordImpl record = logRecord(
                SeverityNumber.WARN,
                keyValue("code.function.name", "com.example.service.UserService.execute"));

        assertSame(record, filter.filter(record));
    }

    @Test
    @DisplayName("filter() should fallback to code.namespace and reject incompatible severity")
    void filterShouldUseNamespaceWhenFunctionMissing() {
        FilterByClassName filter = new FilterByClassName();
        filter.add("com\\.fallback\\..*", SeverityNumber.ERROR);

        LogRecordImpl record = logRecord(
                SeverityNumber.WARN,
                keyValue("code.namespace", "com.fallback.Component"));

        assertNull(filter.filter(record));
    }

    @Test
    @DisplayName("filter() should ignore malformed function name and fallback to namespace")
    void filterShouldFallbackWhenFunctionNameHasNoMethodSeparator() {
        FilterByClassName filter = new FilterByClassName();
        filter.add("com\\.fallback\\..*", SeverityNumber.INFO);

        LogRecordImpl record = logRecord(
                SeverityNumber.WARN,
                keyValue("code.function.name", "NoDotFunctionName"),
                keyValue("code.namespace", "com.fallback.Component"));

        assertSame(record, filter.filter(record));
    }

    @Test
    @DisplayName("filter() should use empty class name when function and namespace are absent")
    void filterShouldHandleMissingClassAttributes() {
        FilterByClassName filter = new FilterByClassName();
        LogRecordImpl record = logRecord(SeverityNumber.INFO);

        assertSame(record, filter.filter(record));
    }

    @Test
    @DisplayName("filter() should drop records from pruned classes")
    void filterShouldDropPrunedClasses() {
        FilterByClassName filter = new FilterByClassName();
        filter.prune("com\\.blocked\\..*");
        filter.add("com\\.blocked\\..*", SeverityNumber.TRACE);

        LogRecordImpl record = logRecord(
                SeverityNumber.FATAL,
                keyValue("code.namespace", "com.blocked.Secret"));

        assertNull(filter.filter(record));
    }

    @Test
    @DisplayName("filter() should allow records when no severity rule matches")
    void filterShouldAllowWhenNoSeverityRuleMatches() {
        FilterByClassName filter = new FilterByClassName();
        LogRecordImpl record = logRecord(
                SeverityNumber.INFO,
                keyValue("code.namespace", "com.nomatch.Component"));

        assertSame(record, filter.filter(record));
    }

    @Test
    @DisplayName("filter() should iterate non-matching prune/severity patterns and still allow record")
    void filterShouldHandleNonMatchingPatterns() {
        FilterByClassName filter = new FilterByClassName();
        filter.prune("com\\.pruned\\..*");
        filter.add("com\\.ruled\\..*", SeverityNumber.ERROR);

        LogRecordImpl record = logRecord(
                SeverityNumber.INFO,
                keyValue("code.namespace", "com.other.Component"));

        assertSame(record, filter.filter(record));
    }

    @Test
    @DisplayName("filter() should prioritize longer regex over global fallback")
    void filterShouldPrioritizeLongerRegex() {
        FilterByClassName filter = new FilterByClassName();
        filter.add(".*", SeverityNumber.FATAL);
        filter.add("com\\.example\\..*", SeverityNumber.DEBUG);

        LogRecordImpl record = logRecord(
                SeverityNumber.INFO,
                keyValue("code.namespace", "com.example.Feature"));

        assertSame(record, filter.filter(record));
    }

    @Test
    @DisplayName("RegexHolder and RegexComparator helper behavior should be stable")
    void regexHolderAndComparatorShouldBehaveAsExpected() throws Exception {
        Object holderA = newRegexHolder("ab.*");
        Object holderA2 = newRegexHolder("ab.*");
        Object holderB = newRegexHolder("abc.*");
        Object holderC = newRegexHolder("ac.*");

        assertEquals(holderA, holderA2);
        assertNotEquals("not-a-holder", holderA);
        assertEquals(holderA.hashCode(), holderA2.hashCode());
        assertEquals("ab.*", invokeNoArg(holderA));
        assertEquals(Boolean.TRUE, invoke(holderA, new Class<?>[]{String.class}, "ab.test"));
        assertEquals(Boolean.FALSE, invoke(holderA, new Class<?>[]{String.class}, "zz.test"));

        @SuppressWarnings("unchecked")
        Comparator<Object> comparator = (Comparator<Object>) newRegexComparator();

        int byLength = comparator.compare(holderB, holderA);
        int byNaturalOrder = comparator.compare(holderA, holderC);

        assertTrue(byLength < 0, "longer regex should come first");
        assertTrue(byNaturalOrder < 0, "for equal length, natural String order should apply");
    }

    private static LogRecordImpl logRecord(final SeverityNumber severityNumber, final KeyValue... attributes) {
        LogRecordImpl record = new LogRecordImpl();
        record.setSeverityNumber(severityNumber);
        List<KeyValue> attrs = new ArrayList<>();
        Collections.addAll(attrs, attributes);
        record.setAttributes(attrs);
        return record;
    }

    private static KeyValue keyValue(final String key, final String value) {
        return KeyValueFactory.of(key, AnyValueFactory.ofString(value));
    }

    private static Object newRegexHolder(final String regex) throws Exception {
        Class<?> holderClass = Class.forName(
                "com.threeamigos.common.util.implementations.messagehandler.filters.FilterByClassName$RegexHolder");
        Constructor<?> constructor = holderClass.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(regex);
    }

    private static Object newRegexComparator() throws Exception {
        Class<?> comparatorClass = Class.forName(
                "com.threeamigos.common.util.implementations.messagehandler.filters.FilterByClassName$RegexComparator");
        Constructor<?> constructor = comparatorClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Object invokeNoArg(final Object target) throws Exception {
        Method method = target.getClass().getDeclaredMethod("getOriginalRegex");
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object invoke(final Object target, final Class<?>[] signature,
                                 final Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod("matches", signature);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}
