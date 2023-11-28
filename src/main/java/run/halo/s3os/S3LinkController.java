package run.halo.s3os;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ApiVersion;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@ApiVersion("s3os.halo.run/v1alpha1")
@RestController
@RequiredArgsConstructor
public class S3LinkController {
    private final S3LinkService s3LinkService;
    private final ReactiveExtensionClient client;

    /**
     * Map of linking file, used as a lock, key is policyName/objectKey, value is policyName/objectKey.
     */
    private final Map<String, Object> linkingFile = new ConcurrentHashMap<>();

    @GetMapping("/policies/s3")
    public Flux<Policy> listS3Policies() {
        return s3LinkService.listS3Policies();
    }

    @GetMapping("/objects/{policyName}")
    public Mono<S3ListResult> listObjects(@PathVariable(value = "policyName") String policyName,
        @RequestParam(name = "continuationToken", required = false) String continuationToken,
        @RequestParam(name = "continuationObject", required = false) String continuationObject,
        @RequestParam(name = "pageSize") Integer pageSize,
        @RequestParam(name = "unlinked", required = false, defaultValue = "false")
        Boolean unlinked) {
        if (unlinked) {
            return s3LinkService.listObjectsUnlinked(policyName, continuationToken,
                continuationObject, pageSize);
        } else {
            return s3LinkService.listObjects(policyName, continuationToken, pageSize);
        }
    }

    @PostMapping("/attachments/link")
    public Mono<LinkResult> addAttachmentRecord(@RequestBody LinkRequest linkRequest) {
        return Flux.fromIterable(linkRequest.getObjectKeys())
            .filter(objectKey -> linkingFile.put(linkRequest.getPolicyName() + "/" + objectKey,
                linkRequest.getPolicyName() + "/" + objectKey) == null)
            .collectList()
            .flatMap(operableObjectKeys -> client.list(Attachment.class,
                    attachment -> Objects.equals(attachment.getSpec().getPolicyName(),
                        linkRequest.getPolicyName())
                        && StringUtils.isNotEmpty(attachment.getMetadata().getAnnotations()
                        .get(S3OsAttachmentHandler.OBJECT_KEY))
                        && linkRequest.getObjectKeys().contains(attachment.getMetadata()
                        .getAnnotations().get(S3OsAttachmentHandler.OBJECT_KEY)),
                    null)
                .collectList()
                .flatMap(existingAttachments -> Flux.fromIterable(linkRequest.getObjectKeys())
                    .flatMap((objectKey) -> {
                        if (operableObjectKeys.contains(objectKey) && existingAttachments.stream()
                            .noneMatch(attachment -> Objects.equals(
                                attachment.getMetadata().getAnnotations().get(
                                    S3OsAttachmentHandler.OBJECT_KEY), objectKey))) {
                            return s3LinkService
                                .addAttachmentRecord(linkRequest.getPolicyName(), objectKey)
                                .onErrorResume((throwable) -> Mono.just(
                                    new LinkResult.LinkResultItem(objectKey, false,
                                        throwable.getMessage())));
                        } else {
                            return Mono.just(new LinkResult.LinkResultItem(objectKey, false,
                                "附件库中已存在该对象"));
                        }
                    })
                    .doOnNext(linkResultItem -> linkingFile.remove(
                        linkRequest.getPolicyName() + "/" + linkResultItem.getObjectKey()))
                    .collectList()
                    .map(LinkResult::new)));
    }
}
