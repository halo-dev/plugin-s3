package run.halo.s3os;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.web.server.ServerErrorException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.util.UriUtils;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
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
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Extension
public class S3OsAttachmentHandler implements AttachmentHandler {

    private static final String OBJECT_KEY = "s3os.plugin.halo.run/object-key";
    private static final int MULTIPART_MIN_PART_SIZE = 5 * 1024 * 1024;
    private final Map<String, Object> uploadingFile = new ConcurrentHashMap<>();

    @Override
    public Mono<Attachment> upload(UploadContext uploadContext) {
        return Mono.just(uploadContext).filter(context -> this.shouldHandle(context.policy()))
                .flatMap(context -> {
                    final var properties = getProperties(context.configMap());
                    return upload(context, properties)
                            .map(objectDetail -> this.buildAttachment(properties, objectDetail));
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

    Attachment buildAttachment(S3OsProperties properties, ObjectDetail objectDetail) {
        String externalLink;
        if (StringUtils.isBlank(properties.getDomain())) {
            var host = properties.getBucket() + "." + properties.getEndpoint();
            externalLink = properties.getProtocol() + "://" + host + "/" + objectDetail.uploadState.objectKey;
        } else {
            externalLink = properties.getProtocol() + "://" + properties.getDomain() + "/"
                    + objectDetail.uploadState.objectKey;
        }

        var metadata = new Metadata();
        metadata.setName(UUID.randomUUID().toString());
        metadata.setAnnotations(
                Map.of(OBJECT_KEY, objectDetail.uploadState.objectKey, Constant.EXTERNAL_LINK_ANNO_KEY,
                        UriUtils.encodePath(externalLink, StandardCharsets.UTF_8)));

        var objectMetadata = objectDetail.objectMetadata();
        var spec = new AttachmentSpec();
        spec.setSize(objectMetadata.contentLength());
        spec.setDisplayName(objectDetail.uploadState.fileName);
        spec.setMediaType(objectMetadata.contentType());

        var attachment = new Attachment();
        attachment.setMetadata(metadata);
        attachment.setSpec(spec);
        log.info("Upload object {} to bucket {} successfully", objectDetail.uploadState.objectKey,
                properties.getBucket());
        return attachment;
    }

    S3AsyncClient buildS3AsyncClient(S3OsProperties properties) {
        return S3AsyncClient.builder()
                .region(Region.of(properties.getRegion()))
                .endpointOverride(URI.create(properties.getEndpointProtocol() + "://" + properties.getEndpoint()))
                .credentialsProvider(() -> AwsBasicCredentials.create(properties.getAccessKey(),
                        properties.getAccessSecret()))
                .serviceConfiguration(S3Configuration.builder()
                        .chunkedEncodingEnabled(false)
                        .pathStyleAccessEnabled(properties.getEnablePathStyleAccess())
                        .build())
                .build();
    }

    Mono<ObjectDetail> upload(UploadContext uploadContext, S3OsProperties properties) {
        return Mono.zip(Mono.just(new UploadState(properties, uploadContext.file().filename())),
                        Mono.just(buildS3AsyncClient(properties)))
                .flatMap(tuple -> {
                    var uploadState = tuple.getT1();
                    var s3client = tuple.getT2();
                    return checkFileExistsAndRename(uploadState, s3client)
                            // init multipart upload
                            .flatMap(state -> Mono.fromFuture(s3client.createMultipartUpload(
                                    CreateMultipartUploadRequest.builder()
                                            .bucket(properties.getBucket())
                                            .contentType(state.contentType)
                                            .key(state.objectKey)
                                            .build())))
                            .flatMapMany((response) -> {
                                checkResult(response, "createMultipartUpload");
                                uploadState.uploadId = response.uploadId();
                                return uploadContext.file().content();
                            })
                            // buffer to part
                            .windowUntil((buffer) -> {
                                uploadState.buffered += buffer.readableByteCount();
                                if (uploadState.buffered >= MULTIPART_MIN_PART_SIZE) {
                                    uploadState.buffered = 0;
                                    return true;
                                } else {
                                    return false;
                                }
                            })
                            // upload part
                            .concatMap((window) -> window.collectList().flatMap((bufferList) -> {
                                var buffer = S3OsAttachmentHandler.concatBuffers(bufferList);
                                return uploadPart(uploadState, buffer, s3client);
                            }))
                            .reduce(uploadState, (state, completedPart) -> {
                                state.completedParts.put(completedPart.partNumber(), completedPart);
                                return state;
                            })
                            // complete multipart upload
                            .flatMap((state) -> Mono
                                    .fromFuture(s3client.completeMultipartUpload(
                                            CompleteMultipartUploadRequest
                                                    .builder()
                                                    .bucket(properties.getBucket())
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
                                                .key(uploadState.objectKey)
                                                .build()
                                ));
                            })
                            // build object detail
                            .map((response) -> {
                                checkResult(response, "getMetadata");
                                return new ObjectDetail(uploadState, response);
                            })
                            // close client
                            .doFinally((signalType) -> {
                                if (uploadState.needRemoveMapKey) {
                                    uploadingFile.remove(uploadState.getUploadingMapKey());
                                }
                                s3client.close();
                            });
                });
    }

    private Mono<UploadState> checkFileExistsAndRename(UploadState uploadState, S3AsyncClient s3client) {
        return Mono.defer(() -> {
                    // deduplication of uploading files
                    if (uploadingFile.put(uploadState.getUploadingMapKey(), uploadState.getUploadingMapKey()) != null) {
                        return Mono.error(new FileAlreadyExistsException("文件 " + uploadState.objectKey
                                + " 已存在，建议更名后重试。[local]"));
                    }
                    uploadState.needRemoveMapKey = true;
                    // check whether file exists
                    return Mono
                            .fromFuture(s3client.headObject(HeadObjectRequest.builder()
                                    .bucket(uploadState.properties.getBucket())
                                    .key(uploadState.objectKey)
                                    .build()))
                            .onErrorResume(NoSuchKeyException.class, e -> {
                                var builder = HeadObjectResponse.builder();
                                builder.sdkHttpResponse(SdkHttpResponse.builder().statusCode(404).build());
                                return Mono.just(builder.build());
                            })
                            .flatMap(response -> {
                                if (response != null && response.sdkHttpResponse() != null
                                        && response.sdkHttpResponse().isSuccessful()) {
                                    return Mono.error(new FileAlreadyExistsException("文件 " + uploadState.objectKey
                                            + " 已存在，建议更名后重试。[remote]"));
                                } else {
                                    return Mono.just(uploadState);
                                }
                            });
                })
                .retryWhen(Retry.max(3)
                        .filter(FileAlreadyExistsException.class::isInstance)
                        .doAfterRetry((retrySignal) -> {
                            if (uploadState.needRemoveMapKey) {
                                uploadingFile.remove(uploadState.getUploadingMapKey());
                                uploadState.needRemoveMapKey = false;
                            }
                            uploadState.randomFileName();
                        })
                )
                .onErrorMap(Exceptions::isRetryExhausted,
                        throwable -> new ServerWebInputException(throwable.getCause().getMessage()));
    }


    private Mono<CompletedPart> uploadPart(UploadState uploadState, ByteBuffer buffer, S3AsyncClient s3client) {
        final int partNumber = ++uploadState.partCounter;
        return Mono
                .fromFuture(s3client.uploadPart(UploadPartRequest.builder()
                                .bucket(uploadState.properties.getBucket())
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
        buffers.forEach((buffer) -> partData.put(buffer.toByteBuffer()));

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

    record ObjectDetail(UploadState uploadState, HeadObjectResponse objectMetadata) {
    }

    static class UploadState {
        final S3OsProperties properties;
        final String originalFileName;
        String uploadId;
        int partCounter;
        Map<Integer, CompletedPart> completedParts = new HashMap<>();
        int buffered = 0;
        String contentType;
        String fileName;
        String objectKey;
        boolean needRemoveMapKey = false;

        public UploadState(S3OsProperties properties, String fileName) {
            this.properties = properties;
            this.originalFileName = fileName;
            this.fileName = fileName;
            this.objectKey = properties.getObjectName(fileName);
            this.contentType = MediaTypeFactory.getMediaType(fileName)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
        }

        public String getUploadingMapKey() {
            return properties.getBucket() + "/" + objectKey;
        }

        public void randomFileName() {
            this.fileName = FileNameUtils.randomFileName(originalFileName, 4);
            this.objectKey = properties.getObjectName(fileName);
        }
    }

}
