package run.halo.s3os;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FileNameUtilsTest {
    @Test
    void testReplaceFilenameWithDuplicateHandling() {
        // Case 1: halo.run -> halo-xyz.run
        var result = FileNameUtils.replaceFilenameWithDuplicateHandling(
            "halo.run", S3OsProperties.RandomFilenameMode.none, null,
            null, S3OsProperties.DuplicateFilenameHandling.randomAlphanumeric);
        assertTrue(result.matches("halo-[a-z0-9]{4}.run"));

        // Case 2: .run -> xyz.run
        result = FileNameUtils.replaceFilenameWithDuplicateHandling(
            ".run", S3OsProperties.RandomFilenameMode.none, null,
            null, S3OsProperties.DuplicateFilenameHandling.randomAlphanumeric);
        assertTrue(result.matches("[a-z0-9]{4}.run"));

        // Case 3: halo -> halo-xyz
        result = FileNameUtils.replaceFilenameWithDuplicateHandling(
            "halo", S3OsProperties.RandomFilenameMode.none, null,
            null, S3OsProperties.DuplicateFilenameHandling.randomAlphanumeric);
        assertTrue(result.matches("halo-[a-z0-9]{4}"));
    }
}