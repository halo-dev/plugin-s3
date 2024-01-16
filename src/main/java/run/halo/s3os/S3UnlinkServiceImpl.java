package run.halo.s3os;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.extension.ReactiveExtensionClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3UnlinkServiceImpl implements S3UnlinkService {
    private final ReactiveExtensionClient client;
    private final S3OsAttachmentHandler handler;

    @Override
    public Mono<Attachment> unlink(String name) {
        return client.get(Attachment.class, name)
            .flatMap((attachment) -> client.get(Policy.class, attachment.getSpec().getPolicyName())
                .doOnNext((policy) -> {
                    if (!handler.shouldHandle(policy)) {
                        throw new ServerWebInputException(
                            "This attachment policy is not managed by plugin-s3.");
                    }
                }).thenReturn(attachment))
            .flatMap(attachment -> {
                attachment.getMetadata().getAnnotations().put(
                    S3OsAttachmentHandler.SKIP_REMOTE_DELETION_ANNO, Boolean.TRUE.toString());
                return client.delete(attachment);
            });
    }

}
