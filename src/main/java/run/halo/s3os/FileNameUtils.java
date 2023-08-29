package run.halo.s3os;

import com.google.common.io.Files;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public final class FileNameUtils {

    private FileNameUtils() {
    }

    public static String removeFileExtension(String filename, boolean removeAllExtensions) {
        if (filename == null || filename.isEmpty()) {
            return filename;
        }
        var extPattern = "(?<!^)[.]" + (removeAllExtensions ? ".*" : "[^.]*$");
        return filename.replaceAll(extPattern, "");
    }

    public static String getRandomFilename(String filename, Integer length, String mode) {
        return switch (mode) {
//            case "none" -> filename;
            case "withString" -> randomFilenameWithString(filename, length);
            case "dateWithString" -> randomDateWithString(filename, length);
            case "datetimeWithString" -> randomDatetimeWithString(filename, length);
            case "string" -> randomString(filename, length);
            case "uuid" -> randomUuid(filename);
            default -> filename;
        };
    }

    /**
     * Append random string after file name.
     * <pre>
     * Case 1: halo.run -> halo-xyz.run
     * Case 2: .run -> xyz.run
     * Case 3: halo -> halo-xyz
     * </pre>
     *
     * @param filename is name of file.
     * @param length   is for generating random string with specific length.
     * @return File name with random string.
     */
    public static String randomFilenameWithString(String filename, Integer length) {
        String random = RandomStringUtils.randomAlphabetic(length).toLowerCase();
        return randomFilename(filename, random, true);
    }

    private static String randomDateWithString(String filename, Integer length) {
        String random = LocalDate.now() + "-" + RandomStringUtils.randomAlphabetic(length).toLowerCase();
        return randomFilename(filename, random, false);
    }

    private static String randomDatetimeWithString(String filename, Integer length) {
        String random = LocalDateTime.now() + "-" + RandomStringUtils.randomAlphabetic(length).toLowerCase();
        return randomFilename(filename, random, false);
    }

    private static String randomString(String filename, Integer length) {
        String random = RandomStringUtils.randomAlphabetic(length).toLowerCase();
        return randomFilename(filename, random, false);
    }

    private static String randomUuid(String filename) {
        String random = UUID.randomUUID().toString().toUpperCase();
        return randomFilename(filename, random, false);
    }

    private static String randomFilename(String filename, String random, Boolean needOriginalName) {
        String nameWithoutExtension = Files.getNameWithoutExtension(filename);
        String extension = Files.getFileExtension(filename);
        boolean nameIsEmpty = StringUtils.isBlank(nameWithoutExtension);
        boolean extensionIsEmpty = StringUtils.isBlank(extension);
        if (needOriginalName) {
            if (nameIsEmpty) {
                return random + "." + extension;
            }
            if (extensionIsEmpty) {
                return nameWithoutExtension + "-" + random;
            }
            return nameWithoutExtension + "-" + random + "." + extension;
        }
        else {
            if (extensionIsEmpty) {
                return random;
            }
            return random + "." + extension;
        }
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
