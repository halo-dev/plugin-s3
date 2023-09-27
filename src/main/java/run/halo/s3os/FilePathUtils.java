package run.halo.s3os;

import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;

public class FilePathUtils {
    private FilePathUtils() {

    }

    public static String getFilePathByPlaceholder(String filename) {
        LocalDate localDate = LocalDate.now();
        return StringUtils.replaceEach(filename,
            new String[] {"${year}","${month}","${day}"},
            new String[] {
                 String.valueOf(localDate.getYear()),
                 String.valueOf(localDate.getMonthValue()),
                 String.valueOf(localDate.getDayOfMonth())
            }
        );
    }
}
