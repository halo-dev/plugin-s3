package run.halo.s3os;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PlaceholderReplacerTest {

    @Test
    void testReplacePlaceholdersTemplateNull() {
        String result1 = PlaceholderReplacer.replacePlaceholders(null, "test");
        assertEquals("test", result1);
        String result2 = PlaceholderReplacer.replacePlaceholders("", "test");
        assertEquals("test", result2);
    }

    @Test
    void testReplacePlaceholdersAllPlaceholder() {
        String template = "${origin-filename}-${uuid-with-dash}-${uuid-no-dash}-${timestamp-sec}-" +
            "${timestamp-ms}-${year}-${month}-${day}-${weekday}-${hour}-${minute}-${second}-" +
            "${millisecond}-${random-alphabetic:4}-${random-num:5}-${random-alphanumeric:6}";
        String result = PlaceholderReplacer.replacePlaceholders(template, "test");
        String regex = "test-" +
            "[A-F0-9]{8}-[A-F0-9]{4}-[A-F0-9]{4}-[A-F0-9]{4}-[A-F0-9]{12}-" +
            "[A-F0-9]{32}-" +
            "[0-9]{10}-" +
            "[0-9]{13}-[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{1}-[0-9]{2}-[0-9]{2}-[0-9]{2}-" +
            "[0-9]{3}-[a-z]{4}-[0-9]{5}-[a-z0-9]{6}";
        assertTrue(result.matches(regex));
    }

    @Test
    void testReplacePlaceholdersTimestamp() {
        String template =
            "${timestamp-sec}-${timestamp-sec}-${timestamp-ms}-${timestamp-ms}-${year}-${year}-" +
                "${month}-${month}-${day}-${day}-${weekday}-${weekday}-${hour}-${hour}-" +
                "${minute}-${minute}-${second}-${second}-${millisecond}-${millisecond}";
        String result = PlaceholderReplacer.replacePlaceholders(template, "test");
        String[] split = result.split("-");
        for (int i = 0; i < split.length; i += 2) {
            assertEquals(split[i], split[i + 1]);
        }
    }

    @Test
    void testReplacePlaceholdersRandomAlphabeticNoLength() {
        String template =
            "${random-alphabetic}_${random-alphabetic:}_${random-alphabetic:-1}_${random-alphabetic:0}";
        String result = PlaceholderReplacer.replacePlaceholders(template, "test");
        String regex = "[a-z]{8}_[a-z]{8}_[a-z]{8}_[a-z]{8}";
        assertTrue(result.matches(regex));

        template = "${random-num}_${random-num:}_${random-num:-1}_${random-num:0}";
        result = PlaceholderReplacer.replacePlaceholders(template, "test");
        regex = "[0-9]{8}_[0-9]{8}_[0-9]{8}_[0-9]{8}";
        assertTrue(result.matches(regex));

        template =
            "${random-alphanumeric}_${random-alphanumeric:}_${random-alphanumeric:-1}_${random-alphanumeric:0}";
        result = PlaceholderReplacer.replacePlaceholders(template, "test");
        regex = "[a-z0-9]{8}_[a-z0-9]{8}_[a-z0-9]{8}_[a-z0-9]{8}";
        assertTrue(result.matches(regex));
    }

    @Test
    void testReplacePlaceholdersInvalid() {
        String template = "file_${not-exist}_test";
        String result = PlaceholderReplacer.replacePlaceholders(template, "test");
        assertEquals("file_${not-exist}_test", result);

        template = "file_${random-alphabetic_test";
        result = PlaceholderReplacer.replacePlaceholders(template, "test");
        assertEquals("file_${random-alphabetic_test", result);

        template = "file_random-alphabetic}_test";
        result = PlaceholderReplacer.replacePlaceholders(template, "test");
        assertEquals("file_random-alphabetic}_test", result);
    }

}