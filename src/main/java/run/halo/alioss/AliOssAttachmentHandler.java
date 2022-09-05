package run.halo.alioss;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.internal.OSSHeaders;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.aliyun.oss.model.StorageClass;
import com.aliyun.oss.model.VoidResult;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.Extension;
import org.springframework.core.io.buffer.DataBufferUtils;
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

@Slf4j
@Extension
public class AliOssAttachmentHandler implements AttachmentHandler {

    private static final String OBJECT_KEY = "alioss.plugin.halo.run/object-key";

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
                var oss = buildOss(properties);
                ossExecute(() -> {
                    log.info("{}/{} is being deleted from AliOSS", properties.getBucket(),
                        objectName);
                    VoidResult result = oss.deleteObject(properties.getBucket(), objectName);
                    if (log.isDebugEnabled()) {
                        debug(result);
                    }
                    log.info("{}/{} was deleted successfully from AliOSS", properties.getBucket(),
                        objectName);
                    return result;
                }, oss::shutdown);
            }).map(DeleteContext::attachment);
    }

    <T> T ossExecute(Supplier<T> runnable, Runnable finalizer) {
        try {
            return runnable.get();
        } catch (OSSException oe) {
            log.error("""
                Caught an OSSException, which means your request made it to OSS, but was 
                rejected with an error response for some reason. 
                Error message: {}, error code: {}, request id: {}, host id: {}
                """, oe.getErrorCode(), oe.getErrorCode(), oe.getRequestId(), oe.getHostId());
            throw Exceptions.propagate(oe);
        } catch (ClientException ce) {
            log.error("""
                Caught an ClientException, which means the client encountered a serious internal 
                problem while trying to communicate with OSS, such as not being able to access 
                the network.
                """);
            throw Exceptions.propagate(ce);
        } finally {
            if (finalizer != null) {
                finalizer.run();
            }
        }
    }

    AliOssProperties getProperties(ConfigMap configMap) {
        var settingJson = configMap.getData().getOrDefault("default", "{}");
        return JsonUtils.jsonToObject(settingJson, AliOssProperties.class);
    }

    Attachment buildAttachment(UploadContext uploadContext, AliOssProperties properties,
                               ObjectDetail objectDetail) {
        var host = properties.getBucket() + "." + properties.getEndpoint();
        var externalLink =
            properties.getProtocol() + "://" + host + "/" + objectDetail.objectName();

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

    OSS buildOss(AliOssProperties properties) {
        return new OSSClientBuilder().build(properties.getEndpoint(), properties.getAccessKey(),
            properties.getAccessSecret());
    }

    Mono<ObjectDetail> upload(UploadContext uploadContext, AliOssProperties properties) {
        return Mono.fromCallable(() -> {
            var client = buildOss(properties);
            // build object name
            var objectName = properties.getObjectName(uploadContext.file().filename());

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
            log.info("Uploading {} into AliOSS {}/{}/{}", uploadContext.file().filename(),
                properties.getEndpoint(), bucket, objectName);

            var request = new PutObjectRequest(bucket, objectName, pis);
            var metadata = new ObjectMetadata();
            metadata.setHeader(OSSHeaders.OSS_STORAGE_CLASS, StorageClass.Standard.toString());
            metadata.setObjectAcl(CannedAccessControlList.PublicRead);
            request.setMetadata(metadata);

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
                PutObjectResult: request id: {}, version id: {}, server CRC: {}, 
                client CRC: {}, etag: {}, response status: {}, response headers: {}, response body: {}
                """, result.getRequestId(), result.getVersionId(), result.getServerCRC(),
            result.getClientCRC(), result.getETag(), result.getResponse().getStatusCode(),
            result.getResponse().getHeaders(), result.getResponse().getErrorResponseAsString());
    }

    void debug(VoidResult result) {
        log.debug("""
                VoidResult: request id: {}, server CRC: {}, 
                client CRC: {}, response status: {}, response headers: {}, response body: {}
                """, result.getRequestId(), result.getServerCRC(), result.getClientCRC(),
            result.getResponse().getStatusCode(), result.getResponse().getHeaders(),
            result.getResponse().getErrorResponseAsString());
    }

    boolean shouldHandle(Policy policy) {
        if (policy == null || policy.getSpec() == null ||
            policy.getSpec().getTemplateRef() == null) {
            return false;
        }
        var templateRef = policy.getSpec().getTemplateRef();
        return "alioss".equals(templateRef.getName());
    }

    record ObjectDetail(String bucketName, String objectName, ObjectMetadata objectMetadata) {
    }

}
