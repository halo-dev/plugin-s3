package run.halo.s3os;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebInputException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@UtilityClass
public class S3ExceptionHandler {

    /**
     * Map user configuration caused S3 exception to ServerWebInputException
     * @param throwable Exception
     * @return ServerWebInputException or original exception
     */
    public static Throwable map(Throwable throwable) {
        if (throwable instanceof S3Exception s3e) {
            log.error("S3Exception occurred", s3e);
            return new ServerWebInputException(String.format(
                "The object storage service returned an error status code %d. Please check the storage "
                    + "policy configuration and make sure your account and service are working properly.",
                s3e.statusCode()));
        }
        if (throwable instanceof SdkException sdke && sdke.getMessage() != null
            && sdke.getMessage().contains("UnknownHostException")) {
            log.error("UnknownHostException occurred", sdke);
            return new ServerWebInputException(
                "Received an UnknownHostException, please check if the endpoint is entered correctly, "
                    + "especially for any spaces before or after the endpoint.");
        }
        if (throwable instanceof SdkException sdke && sdke.getMessage() != null
            && sdke.getMessage().contains("Connect timed out")) {
            log.error("ConnectTimeoutException occurred", sdke);
            return new ServerWebInputException(
                "Received a ConnectTimeoutException, please check if the endpoint is entered correctly, "
                    + "and make sure your object storage service is working properly.");
        }
        return throwable;
    }
}