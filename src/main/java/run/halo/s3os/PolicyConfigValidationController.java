package run.halo.s3os;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.plugin.ApiVersion;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

@ApiVersion("s3os.halo.run/v1alpha1")
@RestController
@RequiredArgsConstructor
public class PolicyConfigValidationController {
    private final S3OsAttachmentHandler handler;

    @PostMapping("/configmap/policy/validation")
    public Mono<Void> validatePolicyConfig(@RequestBody S3OsProperties properties) {
        var finalLocation = FilePathUtils.getFilePathByPlaceholder(properties.getLocation());
        return Mono.using(() -> handler.buildS3Client(properties),
            (s3Client) -> Mono.fromCallable(
                () -> s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(properties.getBucket())
                    .prefix(StringUtils.isNotEmpty(finalLocation)
                        ? finalLocation + "/" : null)
                    .delimiter("/")
                    .maxKeys(1)
                    .build())).subscribeOn(Schedulers.boundedElastic()),
            S3Client::close)
            .doOnNext((resp) -> {
                if (resp.sdkHttpResponse() == null || !resp.sdkHttpResponse().isSuccessful()){
                    throw new ServerWebInputException("The object storage service returned an error status code. " +
                        "Please check the storage policy configuration and make sure your account and " +
                        "service are working properly.");
                }
            })
            .onErrorMap(S3ExceptionHandler::map)
            .then();
    }
}
