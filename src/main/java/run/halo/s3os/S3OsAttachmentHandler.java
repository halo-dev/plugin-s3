package run.halo.s3os;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.server.ServerErrorException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.retry.Retry;
import run.halo.app.core.attachment.ThumbnailSize;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Attachment.AttachmentSpec;
import run.halo.app.core.extension.attachment.Attachment.AttachmentStatus;
import run.halo.app.core.extension.attachment.Constant;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.attachment.endpoint.AttachmentHandler;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.MetadataUtil;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.awscore.presigner.SdkPresigner;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.utils.SdkAutoCloseable;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Extension
public class S3OsAttachmentHandler implements AttachmentHandler {

    public static final String OBJECT_KEY = "s3os.plugin.halo.run/object-key";

    public static final String URL_SUFFIX_ANNO_KEY = "s3os.plugin.halo.run/url-suffix";

    public static final String SKIP_REMOTE_DELETION_ANNO = "s3os.plugin.halo.run/skip-remote-deletion";

    private static final MediaType IMAGE_MEDIA_TYPE = MediaType.parseMediaType("image/*");

    public static final int MULTIPART_MIN_PART_SIZE = 5 * 1024 * 1024;

    /**
     * Map to store uploading file, used as a lock, key is bucket/objectKey, value is bucket/objectKey.
     */
    private final Map<String, Object> uploadingFile = new ConcurrentHashMap<>();

    @Override
    public Mono<Attachment> upload(UploadContext uploadContext) {
        return Mono.just(uploadContext).filter(context -> this.shouldHandle(context.policy()))
            .flatMap(context -> {
                final var properties = S3OsProperties.convertFrom(context.configMap());
                return upload(context, properties)
                    .subscribeOn(Schedulers.boundedElastic())
                    .map(objectDetail -> this.buildAttachment(properties, objectDetail))
                    .onErrorMap(S3ExceptionHandler::map);
            });
    }

    @Override
    public Mono<Attachment> delete(DeleteContext deleteContext) {
        return Mono.just(deleteContext)
            .filter(context -> this.shouldHandle(context.policy()))
            .flatMap(context -> {
                var objectKey = getObjectKey(context.attachment());
                if (objectKey == null) {
                    log.warn(
                        "Cannot obtain object key from attachment {}, skip deleting object from S3.",
                        context.attachment().getMetadata().getName());
                    return Mono.just(context.attachment());
                } else if (MetadataUtil.nullSafeAnnotations(context.attachment())
                    .containsKey(SKIP_REMOTE_DELETION_ANNO)) {
                    log.info("Skip deleting object {} from S3.", objectKey);
                    return Mono.just(context.attachment());
                }
                var properties = S3OsProperties.convertFrom(deleteContext.configMap());
                return Mono.using(
                        () -> buildS3Client(properties),
                        client -> Mono.fromCallable(
                            () -> client.deleteObject(DeleteObjectRequest.builder()
                                .bucket(properties.getBucket())
                                .key(objectKey)
                                .build())),
                        S3Client::close
                    )
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnNext(response -> {
                        checkResult(response, "delete object");
                        log.info("Delete object {} from bucket {} successfully",
                            objectKey, properties.getBucket());
                    })
                    // ignore when the object does not exist
                    .onErrorComplete(NoSuchKeyException.class::isInstance)
                    .onErrorMap(S3ExceptionHandler::map)
                    .thenReturn(context.attachment());
            });
    }

    @Override
    public Mono<URI> getSharedURL(Attachment attachment, Policy policy, ConfigMap configMap,
                                  Duration ttl) {
        if (!this.shouldHandle(policy)) {
            return Mono.empty();
        }
        var objectKey = getObjectKey(attachment);
        if (objectKey == null) {
            return Mono.error(new IllegalArgumentException(
                "Cannot obtain object key from attachment " + attachment.getMetadata().getName()));
        }
        var properties = S3OsProperties.convertFrom(configMap);

        return Mono.using(() -> buildS3Presigner(properties),
                s3Presigner -> {
                    var getObjectRequest = GetObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(objectKey)
                        .build();
                    var presignedRequest = GetObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .getObjectRequest(getObjectRequest)
                        .build();
                    var presignedGetObjectRequest = s3Presigner.presignGetObject(presignedRequest);
                    var presignedURL = presignedGetObjectRequest.url();
                    try {
                        return Mono.just(presignedURL.toURI());
                    } catch (URISyntaxException e) {
                        return Mono.error(
                            new RuntimeException("Failed to convert URL " + presignedURL + " to URI."));
                    }
                },
                SdkPresigner::close)
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap(S3ExceptionHandler::map);
    }

    @Override
    public Mono<URI> getPermalink(Attachment attachment, Policy policy, ConfigMap configMap) {
        if (!this.shouldHandle(policy)) {
            return Mono.empty();
        }
        return Mono.justOrEmpty(doGetPermalink(attachment, S3OsProperties.convertFrom(configMap)));
    }

    @Override
    public Mono<Map<ThumbnailSize, URI>> getThumbnailLinks(Attachment attachment, Policy policy, ConfigMap configMap) {
        if (!this.shouldHandle(policy)) {
            return Mono.empty();
        }
        var properties = S3OsProperties.convertFrom(configMap);
        return Mono.just(doGetThumbnailLinks(attachment, properties));
    }

    private Optional<URI> doGetPermalink(Attachment attachment, S3OsProperties properties) {
        var objectKey = getObjectKey(attachment);
        if (objectKey == null) {
            // fallback to default handler for backward compatibility
            return Optional.empty();
        }
        var objectURL = properties.toObjectURL(objectKey);
        var urlSuffix = getUrlSuffixAnnotation(attachment);
        if (StringUtils.isNotBlank(urlSuffix)) {
            objectURL += urlSuffix;
        }
        return Optional.of(URI.create(objectURL));
    }

    @NonNull
    private Map<ThumbnailSize, URI> doGetThumbnailLinks(Attachment attachment, S3OsProperties properties) {
        // TODO Support configuring media types that support thumbnails
        var support = Optional.ofNullable(attachment.getSpec().getMediaType())
            .map(MediaType::parseMediaType)
            .map(IMAGE_MEDIA_TYPE::isCompatibleWith)
            .orElse(false);
        if (!support) {
            if (log.isDebugEnabled()) {
                log.debug("Attachment {} media type {} is not compatible with image/*, skip generating thumbnail links",
                    attachment.getMetadata().getName(), attachment.getSpec().getMediaType());
            }
            return Map.of();
        }

        var thumbnailParamPattern = properties.getThumbnailParamPattern();
        if (StringUtils.isBlank(thumbnailParamPattern) || !thumbnailParamPattern.contains("{width}")) {
            return Map.of();
        }
        return Optional.ofNullable(attachment.getStatus())
            .map(AttachmentStatus::getPermalink)
            .filter(StringUtils::isNotBlank)
            .map(URI::create)
            .or(() -> doGetPermalink(attachment, properties))
            .map(permalink -> Arrays.stream(ThumbnailSize.values())
                .collect(Collectors.toMap(Function.identity(), size -> {
                    var thumbnailParam = thumbnailParamPattern.replace("{width}", String.valueOf(size.getWidth()));
                    var isQueryPattern = thumbnailParam.startsWith("?");
                    if (isQueryPattern) {
                        return UriComponentsBuilder.fromUri(permalink)
                            .query(thumbnailParam.substring(1))
                            .build(true)
                            .toUri();
                    }
                    return UriComponentsBuilder.fromUri(permalink)
                        .path(thumbnailParam)
                        .build(true)
                        .toUri();
                }))
            )
            .orElse(Map.of());
    }

    @Nullable
    private String getObjectKey(Attachment attachment) {
        var annotations = attachment.getMetadata().getAnnotations();
        if (annotations == null) {
            return null;
        }
        return annotations.get(OBJECT_KEY);
    }

    @Nullable
    private String getUrlSuffixAnnotation(Attachment attachment) {
        var annotations = attachment.getMetadata().getAnnotations();
        if (annotations == null) {
            return null;
        }
        return annotations.get(URL_SUFFIX_ANNO_KEY);
    }

    Attachment buildAttachment(S3OsProperties properties, ObjectDetail objectDetail) {
        String externalLink = properties.toObjectURL(objectDetail.uploadState.objectKey);
        var urlSuffix = UrlUtils.findUrlSuffix(properties.getUrlSuffixes(),
            objectDetail.uploadState.fileName);

        var metadata = new Metadata();
        metadata.setName(UUID.randomUUID().toString());

        var annotations = new HashMap<>(Map.of(OBJECT_KEY, objectDetail.uploadState.objectKey));
        if (StringUtils.isNotBlank(urlSuffix)) {
            externalLink += urlSuffix;
            annotations.put(URL_SUFFIX_ANNO_KEY, urlSuffix);
        }
        annotations.put(Constant.EXTERNAL_LINK_ANNO_KEY, externalLink);
        metadata.setAnnotations(annotations);

        var objectMetadata = objectDetail.objectMetadata();
        var spec = new AttachmentSpec();
        spec.setSize(objectMetadata.contentLength());
        spec.setDisplayName(objectDetail.uploadState.fileName);
        spec.setMediaType(objectMetadata.contentType());

        var attachment = new Attachment();
        attachment.setMetadata(metadata);
        attachment.setSpec(spec);
        attachment.setStatus(new AttachmentStatus());
        doGetPermalink(attachment, properties).ifPresent(permalink ->
            attachment.getStatus().setPermalink(permalink.toString())
        );
        var thumbnails = doGetThumbnailLinks(attachment, properties);
        var mappedThumbnails = thumbnails.keySet()
            .stream()
            .collect(Collectors.toMap(ThumbnailSize::name, size -> thumbnails.get(size).toString()));
        if (!mappedThumbnails.isEmpty()) {
            attachment.getStatus().setThumbnails(mappedThumbnails);
        }
        log.info("Built attachment {} successfully", objectDetail.uploadState.objectKey);
        return attachment;
    }

    S3Client buildS3Client(S3OsProperties properties) {
        return S3Client.builder()
            .region(Region.of(properties.getRegion()))
            .endpointOverride(
                URI.create(properties.getEndpointProtocol() + "://" + properties.getEndpoint()))
            .credentialsProvider(() -> AwsBasicCredentials.create(properties.getAccessKey(),
                properties.getAccessSecret()))
            .serviceConfiguration(S3Configuration.builder()
                .chunkedEncodingEnabled(false)
                .pathStyleAccessEnabled(properties.getEnablePathStyleAccess())
                .build())
            .build();
    }

    private S3Presigner buildS3Presigner(S3OsProperties properties) {
        return S3Presigner.builder()
            .region(Region.of(properties.getRegion()))
            .endpointOverride(
                URI.create(properties.getEndpointProtocol() + "://" + properties.getEndpoint()))
            .credentialsProvider(() -> AwsBasicCredentials.create(properties.getAccessKey(),
                properties.getAccessSecret()))
            .serviceConfiguration(S3Configuration.builder()
                .chunkedEncodingEnabled(false)
                .pathStyleAccessEnabled(properties.getEnablePathStyleAccess())
                .build())
            .build();
    }

    Flux<DataBuffer> reshape(Publisher<DataBuffer> content, int bufferSize) {
        var dataBufferFactory = DefaultDataBufferFactory.sharedInstance;
        return Flux.<ByteBuffer>create(sink -> {
                var byteBuffer = ByteBuffer.allocate(bufferSize);
                Flux.from(content)
                    .doOnNext(dataBuffer -> {
                        var count = dataBuffer.readableByteCount();
                        for (var i = 0; i < count; i++) {
                            byteBuffer.put(dataBuffer.read());
                            // Emit the buffer when buffer
                            if (!byteBuffer.hasRemaining()) {
                                sink.next(deepCopy(byteBuffer));
                                byteBuffer.clear();
                            }
                        }
                    })
                    .doOnComplete(() -> {
                        // Emit the last part of buffer.
                        if (byteBuffer.position() > 0) {
                            sink.next(deepCopy(byteBuffer));
                        }
                    })
                    .subscribe(DataBufferUtils::release, sink::error, sink::complete,
                        Context.of(sink.contextView()));
            })
            .map(dataBufferFactory::wrap)
            .cast(DataBuffer.class)
            .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
    }

    ByteBuffer deepCopy(ByteBuffer src) {
        src.flip();
        var dest = ByteBuffer.allocate(src.limit());
        dest.put(src);
        src.rewind();
        dest.flip();
        return dest;
    }

    Mono<ObjectDetail> upload(UploadContext uploadContext, S3OsProperties properties) {
        return Mono.using(() -> buildS3Client(properties),
            client -> {
                var uploadState = new UploadState(properties, uploadContext.file().filename(), true);

                var content = uploadContext.file().content();

                return checkFileExistsAndRename(uploadState, client)
                    // init multipart upload
                    .flatMap(state -> Mono.fromCallable(() -> client.createMultipartUpload(
                        CreateMultipartUploadRequest.builder()
                            .bucket(properties.getBucket())
                            .contentType(state.contentType)
                            .key(state.objectKey)
                            .build())))
                    .doOnNext((response) -> {
                        checkResult(response, "createMultipartUpload");
                        uploadState.uploadId = response.uploadId();
                    })
                    .thenMany(reshape(content, MULTIPART_MIN_PART_SIZE))
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
                        return uploadPart(uploadState, buffer, client);
                    }))
                    .reduce(uploadState, (state, completedPart) -> {
                        state.completedParts.put(completedPart.partNumber(), completedPart);
                        return state;
                    })
                    // complete multipart upload
                    .flatMap((state) -> Mono.just(client.completeMultipartUpload(
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
                        return Mono.just(client.headObject(
                            HeadObjectRequest.builder()
                                .bucket(properties.getBucket())
                                .key(uploadState.objectKey)
                                .build()
                        ));
                    })
                    // build object detail
                    .map((response) -> {
                        checkResult(response, "getMetadata");
                        log.info("Uploaded object {} to bucket {} successfully",
                            uploadState.objectKey, properties.getBucket());
                        return new ObjectDetail(uploadState, response);
                    })
                    // close client
                    .doFinally((signalType) -> {
                        if (uploadState.needRemoveMapKey) {
                            uploadingFile.remove(uploadState.getUploadingMapKey());
                        }
                    });
            },
            SdkAutoCloseable::close);
    }

    Mono<UploadState> checkFileExistsAndRename(UploadState uploadState,
                                               S3Client s3client) {
        return Mono.defer(() -> {
                // deduplication of uploading files
                if (uploadingFile.put(uploadState.getUploadingMapKey(),
                    uploadState.getUploadingMapKey()) != null) {
                    return Mono.error(new FileAlreadyExistsException("文件 " + uploadState.objectKey
                        +
                        " 已存在，建议更名后重试。[local]"));
                }
                uploadState.needRemoveMapKey = true;
                // check whether file exists
                return Mono.fromSupplier(() -> s3client.headObject(HeadObjectRequest.builder()
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
                            return Mono.error(
                                new FileAlreadyExistsException("文件 " + uploadState.objectKey
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
                    uploadState.randomDuplicateFileName();
                })
            )
            .onErrorMap(Exceptions::isRetryExhausted,
                throwable -> new ServerWebInputException(throwable.getCause().getMessage()));
    }


    Mono<CompletedPart> uploadPart(UploadState uploadState, ByteBuffer buffer,
                                   S3Client s3client) {
        final int partNumber = ++uploadState.partCounter;
        return Mono.just(s3client.uploadPart(UploadPartRequest.builder()
                    .bucket(uploadState.properties.getBucket())
                    .key(uploadState.objectKey)
                    .partNumber(partNumber)
                    .uploadId(uploadState.uploadId)
                    .contentLength((long) buffer.capacity())
                    .build(),
                RequestBody.fromByteBuffer(buffer)))
            .map((uploadPartResult) -> {
                checkResult(uploadPartResult, "uploadPart");
                return CompletedPart.builder()
                    .eTag(uploadPartResult.eTag())
                    .partNumber(partNumber)
                    .build();
            });
    }

    static void checkResult(SdkResponse result, String operation) {
        log.info("operation: {}, result: {}", operation, result);
        if (result.sdkHttpResponse() == null || !result.sdkHttpResponse().isSuccessful()) {
            log.error("Failed to upload object, response: {}", result.sdkHttpResponse());
            throw new ServerErrorException("对象存储响应错误，无法将对象上传到S3对象存储", null);
        }
    }

    static ByteBuffer concatBuffers(List<DataBuffer> buffers) {
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

        public UploadState(S3OsProperties properties, String fileName, boolean needRandomJudge) {
            this.properties = properties;
            this.originalFileName = fileName;

            if (needRandomJudge) {
                fileName =
                    FileNameUtils.replaceFilename(fileName, properties.getRandomFilenameMode(),
                        properties.getRandomStringLength(), properties.getCustomTemplate());
            }

            this.fileName = fileName;
            this.objectKey = properties.getObjectName(fileName);
            this.contentType = MediaTypeFactory.getMediaType(fileName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
        }

        public String getUploadingMapKey() {
            return properties.getBucket() + "/" + objectKey;
        }

        public void randomDuplicateFileName() {
            this.fileName = FileNameUtils.replaceFilenameWithDuplicateHandling(originalFileName,
                properties.getRandomFilenameMode(),
                properties.getRandomStringLength(), properties.getCustomTemplate(),
                properties.getDuplicateFilenameHandling());
            this.objectKey = properties.getObjectName(fileName);
        }
    }

}
