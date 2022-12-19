package run.halo.s3os;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.web.util.UriUtils;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Attachment.AttachmentSpec;
import run.halo.app.core.extension.attachment.Constant;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.attachment.endpoint.AttachmentHandler;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.infra.utils.JsonUtils;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Extension
public class S3OsAttachmentHandler implements AttachmentHandler {

    private static final String OBJECT_KEY = "s3os.plugin.halo.run/object-key";

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
            .doOnNext(context -> {
                var annotations = context.attachment().getMetadata().getAnnotations();
                if (annotations == null || !annotations.containsKey(OBJECT_KEY)) {
                    return;
                }
                var objectName = annotations.get(OBJECT_KEY);
                var properties = getProperties(deleteContext.configMap());
                var client = buildOsClient(properties);
                ossExecute(() -> {
                    log.info("{}/{} is being deleted from S3ObjectStorage", properties.getBucket(),
                        objectName);
                    client.deleteObject(properties.getBucket(), objectName);
                    log.info("{}/{} was deleted successfully from S3ObjectStorage", properties.getBucket(),
                        objectName);
                    return null;
                }, client::shutdown);
            }).map(DeleteContext::attachment);
    }

    <T> T ossExecute(Supplier<T> runnable, Runnable finalizer) {
        try {
            return runnable.get();
        } catch (AmazonServiceException ase) {
            log.error("""
                Caught an AmazonServiceException, which means your request made it to S3ObjectStorage, but was 
                rejected with an error response for some reason. 
                Error message: {}
                """, ase.getMessage());
            throw Exceptions.propagate(ase);
        } catch (SdkClientException sce) {
            log.error("""
                Caught an SdkClientException, which means the client encountered a serious internal 
                problem while trying to communicate with S3ObjectStorage, such as not being able to access 
                the network.
                Error message: {}
                """, sce.getMessage());
            throw Exceptions.propagate(sce);
        } finally {
            if (finalizer != null) {
                finalizer.run();
            }
        }
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
            externalLink = properties.getProtocol() + "://" + host + "/" + objectDetail.objectName();
        } else {
            externalLink = properties.getProtocol() + "://" + properties.getDomain() + "/" + objectDetail.objectName();
        }

        var metadata = new Metadata();
        metadata.setName(UUID.randomUUID().toString());
        metadata.setAnnotations(
            Map.of(OBJECT_KEY, objectDetail.objectName(), Constant.EXTERNAL_LINK_ANNO_KEY,
                UriUtils.encodePath(externalLink, StandardCharsets.UTF_8)));

        var objectMetadata = objectDetail.objectMetadata();
        var spec = new AttachmentSpec();
        spec.setSize(objectMetadata.getContentLength());
        spec.setDisplayName(uploadContext.file().filename());
        spec.setMediaType(objectMetadata.getContentType());

        var attachment = new Attachment();
        attachment.setMetadata(metadata);
        attachment.setSpec(spec);
        return attachment;
    }

    AmazonS3 buildOsClient(S3OsProperties properties) {
        return AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(properties.getAccessKey(), properties.getAccessSecret())))
                .withEndpointConfiguration(
                        new AwsClientBuilder.EndpointConfiguration(
                                properties.getEndpointProtocol() + "://" + properties.getEndpoint(),
                                properties.getRegion()))
                .withPathStyleAccessEnabled(false)
                .withChunkedEncodingDisabled(true)
                .build();
    }

    Mono<ObjectDetail> upload(UploadContext uploadContext, S3OsProperties properties) {
        return Mono.fromCallable(() -> {
            var client = buildOsClient(properties);
            // build object name
            var originFilename = uploadContext.file().filename();
            var objectName = properties.getObjectName(originFilename);

            var pos = new PipedOutputStream();
            var pis = new PipedInputStream(pos);
            DataBufferUtils.write(uploadContext.file().content(), pos)
                .subscribeOn(Schedulers.boundedElastic()).doOnComplete(() -> {
                    try {
                        pos.close();
                    } catch (IOException ioe) {
                        // close the stream quietly
                        log.warn("Failed to close output stream", ioe);
                    }
                }).subscribe(DataBufferUtils.releaseConsumer());

            final var bucket = properties.getBucket();
            var metadata = new ObjectMetadata();
            var contentType = MediaTypeFactory.getMediaType(originFilename)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
            metadata.setContentType(contentType);
            var request = new PutObjectRequest(bucket, objectName, pis,
                    metadata);
            log.info("Uploading {} into S3ObjectStorage {}/{}/{}", originFilename,
                properties.getEndpoint(), bucket, objectName);

            return ossExecute(() -> {
                var result = client.putObject(request);
                if (log.isDebugEnabled()) {
                    debug(result);
                }
                var objectMetadata = client.getObjectMetadata(bucket, objectName);
                return new ObjectDetail(bucket, objectName, objectMetadata);
            }, client::shutdown);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    void debug(PutObjectResult result) {
        log.debug("""
                PutObjectResult: VersionId: {}, ETag: {}, ContentMd5: {}, ExpirationTime: {}, ExpirationTimeRuleId: {}, 
                response RawMetadata: {}, UserMetadata: {}
                """, result.getVersionId(), result.getETag(), result.getContentMd5(), result.getExpirationTime(),
                result.getExpirationTimeRuleId(), result.getMetadata().getRawMetadata(),
                result.getMetadata().getUserMetadata());
    }

    boolean shouldHandle(Policy policy) {
        if (policy == null || policy.getSpec() == null ||
            policy.getSpec().getTemplateName() == null) {
            return false;
        }
        String templateName = policy.getSpec().getTemplateName();
        return "s3os".equals(templateName);
    }

    record ObjectDetail(String bucketName, String objectName, ObjectMetadata objectMetadata) {
    }

}
