package run.halo.s3os;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.plugin.ApiVersion;

@ApiVersion("s3os.halo.run/v1alpha1")
@RestController
@RequiredArgsConstructor
public class S3UnlinkController {
    private final S3UnlinkService s3UnlinkService;

    @DeleteMapping("/attachments/{name}")
    public Mono<Attachment> unlink(@PathVariable("name") String name) {
        return s3UnlinkService.unlink(name);
    }
}