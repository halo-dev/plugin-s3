package run.halo.s3os;

import lombok.experimental.UtilityClass;

@UtilityClass
public class FilePathUtils {

    public static String getFilePathByPlaceholder(String filePath) {
        return PlaceholderReplacer.replacePlaceholders(filePath, null);
    }
}
