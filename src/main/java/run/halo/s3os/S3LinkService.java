package run.halo.s3os;


import java.util.Set;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.attachment.Policy;

public interface S3LinkService {
    Flux<Policy> listS3Policies();

    Mono<S3ListResult> listObjects(String policyName, String continuationToken,
        Integer pageSize);

    Mono<LinkResult> addAttachmentRecords(String policyName, Set<String> objectKeys);

    Mono<S3ListResult> listObjectsUnlinked(String policyName, String continuationToken,
        String continuationObject, Integer pageSize);
}
