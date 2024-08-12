package run.halo.s3os;

import static run.halo.app.infra.FileCategoryMatcher.IMAGE;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.MetadataUtil;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;

/**
 * <p>This {@link Reconciler} used to check thumbnail status are generated
 * if not, update the attachment to trigger thumbnail generation by halo</p>
 */
@Component
@RequiredArgsConstructor
public class AttachmentThumbnailReconciler implements Reconciler<Reconciler.Request> {
    static final String REQUEST_GEN_THUMBNAIL = "s3os.halo.run/request-gen-thumbnail";
    private final ExtensionClient client;

    @Override
    public Result reconcile(Request request) {
        client.fetch(Attachment.class, request.name())
            .ifPresent(attachment -> {
                var annotations = MetadataUtil.nullSafeAnnotations(attachment);
                var requestTime = annotations.get(REQUEST_GEN_THUMBNAIL);
                if (isMadeWithIn1Day(requestTime)) {
                    // skip if request is made within 1 day
                    return;
                }

                if (!isImage(attachment)) {
                    // skip non-image attachments
                    return;
                }

                var status = attachment.getStatus();
                if (status == null || status.getThumbnails() == null) {
                    // update to trigger attachment thumbnail generation
                    annotations.put(REQUEST_GEN_THUMBNAIL, Instant.now().toString());
                    client.update(attachment);
                }
            });
        return Result.doNotRetry();
    }

    static boolean isMadeWithIn1Day(String requestTimeStr) {
        if (StringUtils.isBlank(requestTimeStr)) {
            return false;
        }
        var requestTime = Instant.parse(requestTimeStr);
        return Duration.between(requestTime, Instant.now()).minusDays(1).isNegative();
    }

    public static boolean isImage(Attachment attachment) {
        Assert.notNull(attachment, "Attachment must not be null");
        var mediaType = attachment.getSpec().getMediaType();
        return mediaType != null && IMAGE.match(mediaType);
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder
            .extension(new Attachment())
            .build();
    }
}
