package run.halo.s3os;

import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class UrlUtils {
    private static final List<String> HTTP_PREFIXES = Arrays.asList("http://", "https://");

    public static String removeHttpPrefix(String url) {
        if (url != null) {
            for (var httpPrefix : HTTP_PREFIXES) {
                if (url.toLowerCase().startsWith(httpPrefix)) {
                    url = url.substring(httpPrefix.length());
                    break;
                }
            }
        }
        return url;
    }

    public static String findUrlSuffix(List<S3OsProperties.urlSuffixItem> urlSuffixList,
                                       String fileName) {
        if (StringUtils.isBlank(fileName) || urlSuffixList == null) {
            return null;
        }
        fileName = fileName.toLowerCase();
        for (S3OsProperties.urlSuffixItem item : urlSuffixList) {
            String[] fileSuffixes = item.getFileSuffix().split(",");
            for (String suffix : fileSuffixes) {
                if (fileName.endsWith("." + suffix.trim().toLowerCase())) {
                    return item.getUrlSuffix();
                }
            }
        }
        return null;
    }
}
