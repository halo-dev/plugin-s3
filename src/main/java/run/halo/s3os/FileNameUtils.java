package run.halo.s3os;

import static run.halo.s3os.S3OsProperties.DuplicateFilenameHandling;
import static run.halo.s3os.S3OsProperties.RandomFilenameMode;

import com.google.common.io.Files;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.server.ServerWebInputException;

public final class FileNameUtils {

    private FileNameUtils() {
    }

    /**
     * Replace placeholders in filename. No duplicate handling.
     *
     * @param filename filename
     * @param mode random filename mode
     * @param randomStringLength random string length,when mode is withString or string
     * @param customTemplate custom template,when mode is custom
     * @return replaced filename
     */
    public static String replaceFilename(String filename, RandomFilenameMode mode,
                                         Integer randomStringLength, String customTemplate) {
        var extension = Files.getFileExtension(filename);
        var filenameWithoutExtension = Files.getNameWithoutExtension(filename);
        var replaced = replaceFilenameByMode(filenameWithoutExtension, mode, randomStringLength,
            customTemplate);
        return replaced + (StringUtils.isBlank(extension) ? "" : "." + extension);
    }

    /**
     * Replace placeholders in filename with duplicate handling.
     * <pre>
     * Case 1: halo.run -> halo-xyz.run
     * Case 2: .run -> xyz.run
     * Case 3: halo -> halo-xyz
     * </pre>
     *
     * @param filename filename
     * @param mode random filename mode
     * @param randomStringLength random string length,when mode is withString or string
     * @param customTemplate custom template,when mode is custom
     * @param handling duplicate filename handling
     * @return replaced filename
     */
    public static String replaceFilenameWithDuplicateHandling(String filename,
                                                              RandomFilenameMode mode,
                                                              Integer randomStringLength,
                                                              String customTemplate,
                                                              DuplicateFilenameHandling handling) {
        var extension = Files.getFileExtension(filename);
        var filenameWithoutExtension = Files.getNameWithoutExtension(filename);
        var replaced =
            replaceFilenameByMode(filenameWithoutExtension, mode, randomStringLength,
                customTemplate);
        var suffix = getDuplicateFilenameSuffix(handling);
        return replaced + (StringUtils.isBlank(replaced) ? "" : "-") + suffix
            + (StringUtils.isBlank(extension) ? "" : "." + extension);
    }

    private static String getDuplicateFilenameSuffix(
        S3OsProperties.DuplicateFilenameHandling duplicateFilenameHandling) {
        if (duplicateFilenameHandling == null) {
            return RandomStringUtils.randomAlphabetic(4).toLowerCase();
        }
        return switch (duplicateFilenameHandling) {
            case randomAlphabetic -> RandomStringUtils.randomAlphabetic(4).toLowerCase();
            case exception -> throw new ServerWebInputException("Duplicate filename");
            // include "randomAlphanumeric" mode
            default -> RandomStringUtils.randomAlphanumeric(4).toLowerCase();
        };
    }

    private static String replaceFilenameByMode(String filenameWithoutExtension,
                                                S3OsProperties.RandomFilenameMode mode,
                                                Integer randomStringLength,
                                                String customTemplate) {
        if (mode == null) {
            return filenameWithoutExtension;
        }
        // default length is 8
        Integer length = randomStringLength == null ? 8 : randomStringLength;

        return switch (mode) {
            case custom -> {
                if (StringUtils.isBlank(customTemplate)) {
                    yield filenameWithoutExtension;
                }
                yield PlaceholderReplacer.replacePlaceholders(customTemplate,
                    filenameWithoutExtension);
            }
            case uuid -> PlaceholderReplacer.replacePlaceholders("${uuid-with-dash}",
                filenameWithoutExtension);
            case timestampMs -> PlaceholderReplacer.replacePlaceholders("${timestamp-ms}",
                filenameWithoutExtension);
            case dateWithString -> {
                String dateWithStringTemplate =
                    String.format("${year}-${month}-${day}-${random-alphabetic:%d}", length);
                yield PlaceholderReplacer.replacePlaceholders(dateWithStringTemplate,
                    filenameWithoutExtension);
            }
            case datetimeWithString -> {
                String datetimeWithStringTemplate = String.format(
                    "${year}-${month}-${day}T${hour}:${minute}:${second}-${random-alphabetic:%d}",
                    length);
                yield PlaceholderReplacer.replacePlaceholders(datetimeWithStringTemplate,
                    filenameWithoutExtension);
            }
            case withString -> {
                String withStringTemplate =
                    String.format("${origin-filename}-${random-alphabetic:%d}", length);
                yield PlaceholderReplacer.replacePlaceholders(withStringTemplate,
                    filenameWithoutExtension);
            }
            case string -> {
                String stringTemplate = String.format("${random-alphabetic:%d}", length);
                yield PlaceholderReplacer.replacePlaceholders(stringTemplate,
                    filenameWithoutExtension);
            }
            default ->
                // include "none" mode
                filenameWithoutExtension;
        };

    }

    /**
     * Extracts the file name from an Amazon S3 object key.
     *
     * @param objectKey The Amazon S3 object key from which to extract the file name.
     * @return The extracted file name.
     */
    public static String extractFileNameFromS3Key(String objectKey) {
        int lastSlashIndex = objectKey.lastIndexOf("/");
        if (lastSlashIndex >= 0 && lastSlashIndex < objectKey.length() - 1) {
            return objectKey.substring(lastSlashIndex + 1);
        }
        return objectKey;
    }
}
