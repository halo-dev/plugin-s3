package run.halo.s3os;

import lombok.Data;
import org.springframework.util.StringUtils;

@Data
class S3OsProperties {

    private String bucket;

    private Protocol endpointProtocol = Protocol.https;

    private String endpoint;

    private String accessKey;

    private String accessSecret;

    /**
     * 开头结尾已去除"/"
     */
    private String location;

    private Protocol protocol = Protocol.https;

    /**
     * 不包含协议头
     */
    private String domain;


    private String region = "Auto";

    public String getObjectName(String filename) {
        var objectName = filename;
        if (StringUtils.hasText(getLocation())) {
            objectName = getLocation() + "/" + objectName;
        }
        return objectName;
    }

    enum Protocol {
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

    public void setRegion(String region) {
        if (!StringUtils.hasText(region)) {
            this.region = "Auto";
        }else {
            this.region = region;
        }
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = UrlUtils.removeHttpPrefix(endpoint);
    }
}
