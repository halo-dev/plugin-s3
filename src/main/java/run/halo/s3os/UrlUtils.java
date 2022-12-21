package run.halo.s3os;

import java.util.Arrays;
import java.util.List;

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
}
