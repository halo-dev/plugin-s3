package run.halo.s3os;

import static run.halo.s3os.S3OsAttachmentHandler.OBJECT_KEY;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.MetadataUtil;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.QueryFactory;
import run.halo.app.extension.router.selector.FieldSelector;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;


@Service
@RequiredArgsConstructor
@Slf4j
public class S3LinkServiceImpl implements S3LinkService {
    private final ReactiveExtensionClient client;
    private final S3OsAttachmentHandler handler;

    /**
     * Map of linking file, used as a lock, key is policyName/objectKey, value is policyName/objectKey.
     */
    private final Map<String, Object> linkingFile = new ConcurrentHashMap<>();


    @Override
    public Flux<Policy> listS3Policies() {
        return client.list(Policy.class, (policy) -> "s3os".equals(
            policy.getSpec().getTemplateName()), null);
    }

    @Override
    public Mono<S3ListResult> listObjects(String policyName, String continuationToken,
        Integer pageSize) {
        return client.fetch(Policy.class, policyName)
            .flatMap((policy) -> {
                var configMapName = policy.getSpec().getConfigMapName();
                return client.fetch(ConfigMap.class, configMapName);
            })
            .flatMap((configMap) -> {
                var properties = handler.getProperties(configMap);
                var finalLocation = FilePathUtils.getFilePathByPlaceholder(properties.getLocation());
                return Mono.using(() -> handler.buildS3Client(properties),
                        (s3Client) -> Mono.fromCallable(
                            () -> s3Client.listObjectsV2(ListObjectsV2Request.builder()
                                .bucket(properties.getBucket())
                                .prefix(StringUtils.isNotEmpty(finalLocation)
                                    ? finalLocation + "/" : null)
                                .delimiter("/")
                                .maxKeys(pageSize)
                                .continuationToken(StringUtils.isNotEmpty(continuationToken)
                                    ? continuationToken : null)
                                .build())).subscribeOn(Schedulers.boundedElastic()),
                        S3Client::close)
                    .flatMap(listObjectsV2Response -> {
                        List<S3Object> contents = listObjectsV2Response.contents();
                        var objectVos = contents
                            .stream().map(S3ListResult.ObjectVo::fromS3Object)
                            .filter(objectVo -> !objectVo.getKey().endsWith("/"))
                            .collect(Collectors.toMap(S3ListResult.ObjectVo::getKey, o -> o));
                        ListOptions listOptions = new ListOptions();
                        listOptions.setFieldSelector(
                            FieldSelector.of(QueryFactory.equal("spec.policyName", policyName)));
                        return client.listAll(Attachment.class, listOptions, null)
                            .doOnNext(attachment -> {
                                S3ListResult.ObjectVo objectVo =
                                    objectVos.get(attachment.getMetadata().getAnnotations()
                                        .getOrDefault(OBJECT_KEY, ""));
                                if (objectVo != null) {
                                    objectVo.setIsLinked(true);
                                }
                            })
                            .then()
                            .thenReturn(new S3ListResult(new ArrayList<>(objectVos.values()),
                                listObjectsV2Response.continuationToken(),
                                null, null,
                                listObjectsV2Response.nextContinuationToken(),
                                listObjectsV2Response.isTruncated()));
                    });
            })
            .onErrorMap(S3ExceptionHandler::map);
    }

    @Override
    public Mono<LinkResult> addAttachmentRecords(String policyName, Set<String> objectKeys) {
        return getOperableObjectKeys(objectKeys, policyName)
            .flatMap(operableObjectKeys -> getExistingAttachments(objectKeys, policyName)
                .flatMap(existingAttachments -> getLinkResultItems(objectKeys, operableObjectKeys,
                    existingAttachments, policyName)
                    .collectList()
                    .map(LinkResult::new)));
    }

    private Mono<Set<String>> getOperableObjectKeys(Set<String> objectKeys, String policyName) {
        return Flux.fromIterable(objectKeys)
            .filter(objectKey ->
                linkingFile.put(policyName + "/" + objectKey, policyName + "/" + objectKey) == null)
            .collect(Collectors.toSet());
    }

    private Mono<Set<String>> getExistingAttachments(Set<String> objectKeys,
                                                          String policyName) {
        ListOptions listOptions = new ListOptions();
        listOptions.setFieldSelector(
            FieldSelector.of(QueryFactory.equal("spec.policyName", policyName)));
        return client.listAll(Attachment.class, listOptions, null)
            .filter(attachment -> StringUtils.isNotBlank(
                MetadataUtil.nullSafeAnnotations(attachment).get(S3OsAttachmentHandler.OBJECT_KEY))
                && objectKeys.contains(
                MetadataUtil.nullSafeAnnotations(attachment).get(S3OsAttachmentHandler.OBJECT_KEY)))
            .map(attachment -> MetadataUtil.nullSafeAnnotations(attachment)
                .get(S3OsAttachmentHandler.OBJECT_KEY))
            .collect(Collectors.toSet());
    }

    private Flux<LinkResult.LinkResultItem> getLinkResultItems(Set<String> objectKeys,
                                                               Set<String> operableObjectKeys,
                                                               Set<String> existingAttachments,
                                                               String policyName) {
        return Flux.fromIterable(objectKeys)
            .flatMap((objectKey) -> {
                if (operableObjectKeys.contains(objectKey) &&
                    !existingAttachments.contains(objectKey)) {
                    return addAttachmentRecord(policyName, objectKey)
                        .onErrorResume((throwable) -> Mono.just(
                            new LinkResult.LinkResultItem(objectKey, false,
                                throwable.getMessage())));
                } else {
                    return Mono.just(
                        new LinkResult.LinkResultItem(objectKey, false, "附件库中已存在该对象"));
                }
            })
            .doFinally(signalType -> operableObjectKeys.forEach(
                objectKey -> linkingFile.remove(policyName + "/" + objectKey)));
    }

    @Override
    public Mono<S3ListResult> listObjectsUnlinked(String policyName, String continuationToken,
        String continuationObject, Integer pageSize) {
        // TODO 优化成查一次数据库
        return Mono.defer(() -> {
            List<S3ListResult.ObjectVo> s3Objects = new ArrayList<>();
            AtomicBoolean continuationObjectMatched = new AtomicBoolean(false);
            AtomicReference<String> currToken = new AtomicReference<>(continuationToken);

            return Flux.defer(() -> Flux.just(
                    new TokenState(null, currToken.get() == null ? "" : currToken.get())))
                .flatMap(tokenState -> listObjects(policyName, tokenState.nextToken, pageSize))
                .flatMap(s3ListResult -> {
                    var filteredObjects = s3ListResult.getObjects();
                    if (!continuationObjectMatched.get()) {
                        // 判断s3ListResult.getObjects()里是否有continuationObject
                        var continuationObjectVo = s3ListResult.getObjects().stream()
                            .filter(objectVo -> objectVo.getKey().equals(continuationObject))
                            .findFirst();
                        if (continuationObjectVo.isPresent()) {
                            s3Objects.clear();
                            // 删除continuationObject及之前的所有对象
                            filteredObjects = s3ListResult.getObjects().stream()
                                .dropWhile(objectVo -> !objectVo.getKey()
                                    .equals(continuationObject))
                                .skip(1)
                                .toList();
                            continuationObjectMatched.set(true);
                        }
                    }
                    filteredObjects = filteredObjects.stream()
                        .filter(objectVo -> !objectVo.getIsLinked())
                        .toList();
                    s3Objects.addAll(filteredObjects);
                    currToken.set(s3ListResult.getNextToken());
                    return Mono.just(new TokenState(s3ListResult.getCurrentToken(),
                        s3ListResult.getNextToken()));
                })
                .repeat()
                .takeUntil(
                    tokenState -> tokenState.nextToken() == null || s3Objects.size() >= pageSize)
                .last()
                .map(tokenState -> {
                    var limitedObjects = s3Objects.stream().limit(pageSize).toList();
                    return new S3ListResult(limitedObjects, continuationToken, continuationObject,
                            !limitedObjects.isEmpty() ? limitedObjects.get(limitedObjects.size() - 1)
                            .getKey() : null, tokenState.currToken,
                        limitedObjects.size() == pageSize);
                });
        })
        .onErrorMap(S3ExceptionHandler::map);
    }

    record TokenState(String currToken, String nextToken) {
    }

    public Mono<LinkResult.LinkResultItem> addAttachmentRecord(String policyName,
        String objectKey) {
        return authenticationConsumer(authentication -> client.fetch(Policy.class, policyName)
            .flatMap((policy) -> {
                var configMapName = policy.getSpec().getConfigMapName();
                return client.fetch(ConfigMap.class, configMapName);
            })
            .flatMap(configMap -> {
                var properties = handler.getProperties(configMap);
                return Mono.using(() -> handler.buildS3Client(properties),
                        (s3Client) -> Mono.fromCallable(
                                () -> s3Client.headObject(
                                    HeadObjectRequest.builder()
                                        .bucket(properties.getBucket())
                                        .key(objectKey)
                                        .build()))
                            .subscribeOn(Schedulers.boundedElastic()),
                        S3Client::close)
                    .map(headObjectResponse -> {
                        var objectDetail = new S3OsAttachmentHandler.ObjectDetail(
                                new S3OsAttachmentHandler.UploadState(properties,
                                        FileNameUtils.extractFileNameFromS3Key(objectKey), false),
                                headObjectResponse);
                        return handler.buildAttachment(properties, objectDetail);
                    })
                    .doOnNext(attachment -> {
                        var spec = attachment.getSpec();
                        if (spec == null) {
                            spec = new Attachment.AttachmentSpec();
                            attachment.setSpec(spec);
                        }
                        spec.setOwnerName(authentication.getName());
                        spec.setPolicyName(policyName);
                    })
                    .flatMap(client::create)
                    .thenReturn(new LinkResult.LinkResultItem(objectKey, true, null));
            }))
            .onErrorMap(S3ExceptionHandler::map)
            .onErrorResume(throwable ->
                Mono.just(new LinkResult.LinkResultItem(objectKey, false, throwable.getMessage())));
    }

    private <T> Mono<T> authenticationConsumer(Function<Authentication, Mono<T>> func) {
        return ReactiveSecurityContextHolder.getContext()
            .switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Authentication required.")))
            .map(SecurityContext::getAuthentication)
            .flatMap(func);
    }

}
