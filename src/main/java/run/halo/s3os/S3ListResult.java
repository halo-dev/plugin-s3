package run.halo.s3os;

import lombok.AllArgsConstructor;
import lombok.Data;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
public class S3ListResult {
    private List<ObjectVo> objects;
    private String currentToken;
    private String currentContinuationObject;
    private String nextContinuationObject;
    private String nextToken;
    private Boolean hasMore;


    @Data
    @AllArgsConstructor
    public static class ObjectVo {
        private String key;

        private Instant lastModified;

        private Boolean isLinked;

        private String displayName;

        public static ObjectVo fromS3Object(S3Object s3Object) {
            final var key = s3Object.key();
            final var displayName = key.substring(key.lastIndexOf("/") + 1);
            return new ObjectVo(key, s3Object.lastModified(), false, displayName);
        }
    }
}
