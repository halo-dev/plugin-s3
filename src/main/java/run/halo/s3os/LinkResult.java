package run.halo.s3os;


import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class LinkResult {

    private List<LinkResultItem> items;

    @Data
    @AllArgsConstructor
    public static class LinkResultItem {
        private String objectKey;

        private Boolean success;

        private String message;
    }
}
