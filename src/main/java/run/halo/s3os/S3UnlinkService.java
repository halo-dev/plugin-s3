package run.halo.s3os;

import reactor.core.publisher.Mono;
import run.halo.app.core.extension.attachment.Attachment;

public interface S3UnlinkService {
    Mono<Attachment> unlink(String name);
}