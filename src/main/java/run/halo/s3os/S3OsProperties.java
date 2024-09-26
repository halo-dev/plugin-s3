package run.halo.s3os;

import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;
import run.halo.app.extension.ConfigMap;
import run.halo.app.infra.utils.JsonUtils;

@Data
public class S3OsProperties {

    private String bucket;

    private Protocol endpointProtocol = Protocol.https;

    private Boolean enablePathStyleAccess = false;

    private String endpoint;

    private String accessKey;

    private String accessSecret;

    /**
     * 开头结尾已去除"/"
     */
    private String location;

    private RandomFilenameMode randomFilenameMode;

    private String customTemplate;

    private DuplicateFilenameHandling duplicateFilenameHandling;

    private Integer randomStringLength = 8;

    private Protocol protocol = Protocol.https;

    /**
     * 不包含协议头
     */
    private String domain;


    private String region = "Auto";

    private List<urlSuffixItem> urlSuffixes;

    private String thumbnailParamPattern;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class urlSuffixItem {
        private String fileSuffix;
        private String urlSuffix;
    }

    public enum DuplicateFilenameHandling {
        randomAlphanumeric, randomAlphabetic, exception
    }

    public enum RandomFilenameMode {
        none, custom, uuid, timestampMs, dateWithString, datetimeWithString, withString, string, random_number
    }

    public String getObjectName(String filename) {
        var objectName = filename;
        var finalName = FilePathUtils.getFilePathByPlaceholder(getLocation());
        if (StringUtils.hasText(finalName)) {
            objectName = finalName + "/" + objectName;
        }
        return objectName;
    }

    public enum Protocol {
        http, https
    }

    public void setDomain(String domain) {
        this.domain = UrlUtils.removeHttpPrefix(domain);
    }

    public void setLocation(String location) {
        final var fileSeparator = "/";
        if (StringUtils.hasText(location)) {
            if (location.startsWith(fileSeparator)) {
                location = location.substring(1);
            }
            if (location.endsWith(fileSeparator)) {
                location = location.substring(0, location.length() - 1);
            }
        } else {
            location = "";
        }
        this.location = location;
    }

    public void setRandomStringLength(String randomStringLength) {  // if you use Integer, it will throw Error.
        try {
            int length = Integer.parseInt(randomStringLength);
            if (length >= 4 && length <= 16) {
                this.randomStringLength = length;
            }
        } catch (NumberFormatException ignored) {
        }
    }

    public void setRegion(String region) {
        if (!StringUtils.hasText(region)) {
            this.region = "Auto";
        } else {
            this.region = region;
        }
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = UrlUtils.removeHttpPrefix(endpoint);
    }

    public String toObjectURL(String objectKey) {
        String objectURL;
        if (!StringUtils.hasText(this.getDomain())) {
            String host;
            if (this.getEnablePathStyleAccess()) {
                host = this.getEndpoint() + "/" + this.getBucket();
            } else {
                host = this.getBucket() + "." + this.getEndpoint();
            }
            objectURL = this.getProtocol() + "://" + host + "/" + objectKey;
        } else {
            objectURL = this.getProtocol() + "://" + this.getDomain() + "/" + objectKey;
        }
        return UriUtils.encodePath(objectURL, StandardCharsets.UTF_8);
    }

    public static S3OsProperties convertFrom(ConfigMap configMap) {
        var settingJson = configMap.getData().getOrDefault("default", "{}");
        return JsonUtils.jsonToObject(settingJson, S3OsProperties.class);
    }
}
