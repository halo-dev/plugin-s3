package run.halo.s3os;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.web.server.ServerErrorException;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Attachment.AttachmentSpec;
import run.halo.app.core.extension.attachment.Constant;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.attachment.endpoint.AttachmentHandler;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.infra.utils.JsonUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Extension
public class S3OsAttachmentHandler implements AttachmentHandler {

    private static final String OBJECT_KEY = "s3os.plugin.halo.run/object-key";
    private static final int MULTIPART_MIN_PART_SIZE = 5 * 1024 * 1024;

    @Override
    public Mono<Attachment> upload(UploadContext uploadContext) {
        return Mono.just(uploadContext).filter(context -> this.shouldHandle(context.policy()))
                .flatMap(context -> {
                    final var properties = getProperties(context.configMap());
                    return upload(context, properties).map(
                            objectDetail -> this.buildAttachment(context, properties, objectDetail));
                });
    }

    @Override
    public Mono<Attachment> delete(DeleteContext deleteContext) {
        return Mono.just(deleteContext).filter(context -> this.shouldHandle(context.policy()))
                .flatMap(context -> {
                    var annotations = context.attachment().getMetadata().getAnnotations();
                    if (annotations == null || !annotations.containsKey(OBJECT_KEY)) {
                        return Mono.just(context);
                    }
                    var objectName = annotations.get(OBJECT_KEY);
                    var properties = getProperties(deleteContext.configMap());
                    var client = buildS3AsyncClient(properties);
                    return Mono.fromFuture(client.deleteObject(DeleteObjectRequest.builder()
                                    .bucket(properties.getBucket())
                                    .key(objectName)
                                    .build()))
                            .doFinally(signalType -> client.close())
                            .map(response -> {
                                checkResult(response, "delete object");
                                log.info("Delete object {} from bucket {} successfully",
                                        objectName, properties.getBucket());
                                return context;
                            });

                })
                .map(DeleteContext::attachment);
    }

    S3OsProperties getProperties(ConfigMap configMap) {
        var settingJson = configMap.getData().getOrDefault("default", "{}");
        return JsonUtils.jsonToObject(settingJson, S3OsProperties.class);
    }

    Attachment buildAttachment(UploadContext uploadContext, S3OsProperties properties,
                               ObjectDetail objectDetail) {
        String externalLink;
        if (StringUtils.isBlank(properties.getDomain())) {
            var host = properties.getBucket() + "." + properties.getEndpoint();
            externalLink = properties.getProtocol() + "://" + host + "/" + objectDetail.objectKey();
        } else {
            externalLink = properties.getProtocol() + "://" + properties.getDomain() + "/" + objectDetail.objectKey();
        }

        var metadata = new Metadata();
        metadata.setName(UUID.randomUUID().toString());
        metadata.setAnnotations(
                Map.of(OBJECT_KEY, objectDetail.objectKey(), Constant.EXTERNAL_LINK_ANNO_KEY,
                        UriUtils.encodePath(externalLink, StandardCharsets.UTF_8)));

        var objectMetadata = objectDetail.objectMetadata();
        var spec = new AttachmentSpec();
        spec.setSize(objectMetadata.contentLength());
        spec.setDisplayName(uploadContext.file().filename());
        spec.setMediaType(objectMetadata.contentType());

        var attachment = new Attachment();
        attachment.setMetadata(metadata);
        attachment.setSpec(spec);
        log.info("Upload object {} to bucket {} successfully", objectDetail.objectKey(), properties.getBucket());
        return attachment;
    }

    S3AsyncClient buildS3AsyncClient(S3OsProperties properties) {
        return S3AsyncClient.builder()
                .region(Region.of(properties.getRegion()))
                .endpointOverride(URI.create(properties.getEndpointProtocol() + "://" + properties.getEndpoint()))
                .credentialsProvider(() -> AwsBasicCredentials.create(properties.getAccessKey(), properties.getAccessSecret()))
                .build();
    }

    Mono<ObjectDetail> upload(UploadContext uploadContext, S3OsProperties properties) {
        var s3client = buildS3AsyncClient(properties);

        var originFilename = uploadContext.file().filename();
        var objectKey = properties.getObjectName(originFilename);
        var contentType = MediaTypeFactory.getMediaType(originFilename)
                .orElse(MediaType.APPLICATION_OCTET_STREAM).toString();

        var uploadState = new UploadState(properties.getBucket(), objectKey);

        return Mono
                // init multipart upload
                .fromFuture(s3client.createMultipartUpload(
                        CreateMultipartUploadRequest.builder()
                                .bucket(properties.getBucket())
                                .contentType(contentType)
                                .key(objectKey)
                                .build()))
                .flatMapMany((response) -> {
                    checkResult(response, "createMultipartUpload");
                    uploadState.setUploadId(response.uploadId());
                    return uploadContext.file().content();
                })
                // buffer to part
                .bufferUntil((buffer) -> {
                    uploadState.buffered += buffer.readableByteCount();
                    if (uploadState.buffered >= MULTIPART_MIN_PART_SIZE) {
                        uploadState.buffered = 0;
                        return true;
                    } else {
                        return false;
                    }
                })
                .map(S3OsAttachmentHandler::concatBuffers)
                // upload part
                .flatMap((buffer) -> uploadPart(uploadState, buffer, s3client))
                .reduce(uploadState, (state, completedPart) -> {
                    state.completedParts.put(completedPart.partNumber(), completedPart);
                    return state;
                })
                // complete multipart upload
                .flatMap((state) -> Mono
                        .fromFuture(s3client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                                .bucket(state.bucket)
                                .uploadId(state.uploadId)
                                .multipartUpload(CompletedMultipartUpload.builder()
                                        .parts(state.completedParts.values())
                                        .build())
                                .key(state.objectKey)
                                .build())
                        ))
                // get object metadata
                .flatMap((response) -> {
                    checkResult(response, "completeUpload");
                    return Mono.fromFuture(s3client.headObject(
                            HeadObjectRequest.builder()
                                    .bucket(properties.getBucket())
                                    .key(objectKey)
                                    .build()
                    ));
                })
                // build object detail
                .map((response) -> {
                    checkResult(response, "getMetadata");
                    return new ObjectDetail(properties.getBucket(), objectKey, response);
                })
                // close client
                .doFinally((signalType) -> s3client.close());
    }


    private Mono<CompletedPart> uploadPart(UploadState uploadState, ByteBuffer buffer, S3AsyncClient s3client) {
        final int partNumber = ++uploadState.partCounter;
        return Mono
                .fromFuture(s3client.uploadPart(UploadPartRequest.builder()
                                .bucket(uploadState.bucket)
                                .key(uploadState.objectKey)
                                .partNumber(partNumber)
                                .uploadId(uploadState.uploadId)
                                .contentLength((long) buffer.capacity())
                                .build(),
                        AsyncRequestBody.fromPublisher(Mono.just(buffer))))
                .map((uploadPartResult) -> {
                    checkResult(uploadPartResult, "uploadPart");
                    return CompletedPart.builder()
                            .eTag(uploadPartResult.eTag())
                            .partNumber(partNumber)
                            .build();
                });
    }

    private static void checkResult(SdkResponse result, String operation) {
        log.info("operation: {}, result: {}", operation, result);
        if (result.sdkHttpResponse() == null || !result.sdkHttpResponse().isSuccessful()) {
            log.error("Failed to upload object, response: {}", result.sdkHttpResponse());
            throw new ServerErrorException("对象存储响应错误，无法将对象上传到S3对象存储", null);
        }
    }

    private static ByteBuffer concatBuffers(List<DataBuffer> buffers) {
        int partSize = 0;
        for (DataBuffer b : buffers) {
            partSize += b.readableByteCount();
        }

        ByteBuffer partData = ByteBuffer.allocate(partSize);
        buffers.forEach((buffer) -> {
            partData.put(buffer.toByteBuffer());
        });

        // Reset read pointer to first byte
        partData.rewind();

        return partData;
    }


    boolean shouldHandle(Policy policy) {
        if (policy == null || policy.getSpec() == null ||
                policy.getSpec().getTemplateName() == null) {
            return false;
        }
        String templateName = policy.getSpec().getTemplateName();
        return "s3os".equals(templateName);
    }

    record ObjectDetail(String bucketName, String objectKey, HeadObjectResponse objectMetadata) {
    }

    static class UploadState {
        String bucket;
        String objectKey;
        String uploadId;
        int partCounter;
        Map<Integer, CompletedPart> completedParts = new HashMap<>();
        int buffered = 0;

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getObjectKey() {
            return objectKey;
        }

        public void setObjectKey(String objectKey) {
            this.objectKey = objectKey;
        }

        public String getUploadId() {
            return uploadId;
        }

        public void setUploadId(String uploadId) {
            this.uploadId = uploadId;
        }

        public int getPartCounter() {
            return partCounter;
        }

        public void setPartCounter(int partCounter) {
            this.partCounter = partCounter;
        }

        public Map<Integer, CompletedPart> getCompletedParts() {
            return completedParts;
        }

        public void setCompletedParts(Map<Integer, CompletedPart> completedParts) {
            this.completedParts = completedParts;
        }

        public int getBuffered() {
            return buffered;
        }

        public void setBuffered(int buffered) {
            this.buffered = buffered;
        }

        UploadState(String bucket, String objectKey) {
            this.bucket = bucket;
            this.objectKey = objectKey;
        }
    }

}
