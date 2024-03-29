package run.halo.s3os;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.plugin.ApiVersion;

@ApiVersion("s3os.halo.run/v1alpha1")
@RestController
@RequiredArgsConstructor
public class S3LinkController {
    private final S3LinkService s3LinkService;

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
        return s3LinkService.addAttachmentRecords(linkRequest.getPolicyName(),
            linkRequest.getObjectKeys());
    }
}
