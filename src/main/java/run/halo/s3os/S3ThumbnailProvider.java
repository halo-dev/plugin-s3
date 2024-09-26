package run.halo.s3os;

import java.net.URI;
import java.net.URL;
import java.util.Map;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.attachment.ThumbnailProvider;
import run.halo.app.core.attachment.ThumbnailSize;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ReactiveExtensionClient;

@Component
@RequiredArgsConstructor
public class S3ThumbnailProvider implements ThumbnailProvider {
    static final String WIDTH_PLACEHOLDER = "{width}";
    private final Cache<String, S3PropsCacheValue> s3PropsCache = CacheBuilder.newBuilder()
        .maximumSize(50)
        .build();

    private final ReactiveExtensionClient client;
    private final S3LinkService s3LinkService;

    @Override
    public Mono<URI> generate(ThumbnailContext thumbnailContext) {
        var url = thumbnailContext.getImageUrl().toString();
        var size = thumbnailContext.getSize();
        return getCacheValue(url)
            .mapNotNull(cacheValue -> placedPattern(cacheValue.pattern(), size))
            .map(param -> {
                if (param.startsWith("?")) {
                    return UriComponentsBuilder.fromHttpUrl(url)
                        .queryParam(param.substring(1))
                        .build()
                        .toString();
                }
                return url + param;
            })
            .map(URI::create);
    }

    private static String placedPattern(String pattern, ThumbnailSize size) {
        return StringUtils.replace(pattern, WIDTH_PLACEHOLDER, String.valueOf(size.getWidth()));
    }

    @Override
    public Mono<Void> delete(URL url) {
        // do nothing for s3
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> supports(ThumbnailContext thumbnailContext) {
        var url = thumbnailContext.getImageUrl().toString();
        return getCacheValue(url).hasElement();
    }

    private Mono<S3PropsCacheValue> getCacheValue(String imageUrl) {
        return Flux.fromIterable(s3PropsCache.asMap().entrySet())
            .filter(entry -> imageUrl.startsWith(entry.getKey()))
            .next()
            .map(Map.Entry::getValue)
            .switchIfEmpty(Mono.defer(() -> listAllS3ObjectDomain()
                .filter(entry -> imageUrl.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .next()
            ));
    }

    @Builder
    record S3PropsCacheValue(String pattern, String configMapName) {
    }

    private Flux<Map.Entry<String, S3PropsCacheValue>> listAllS3ObjectDomain() {
        return s3LinkService.listS3Policies()
            .flatMap(s3Policy -> {
                var s3ConfigMapName = s3Policy.getSpec().getConfigMapName();
                return fetchS3PropsByConfigMapName(s3ConfigMapName)
                    .mapNotNull(properties -> {
                        var thumbnailParam = properties.getThumbnailParamPattern();
                        if (StringUtils.isBlank(thumbnailParam)) {
                            return null;
                        }
                        var objectDomain = properties.toObjectURL("");
                        var cacheValue = S3PropsCacheValue.builder()
                            .pattern(thumbnailParam)
                            .configMapName(s3ConfigMapName)
                            .build();
                        return Map.entry(objectDomain, cacheValue);
                    });
            })
            .doOnNext(cache -> s3PropsCache.put(cache.getKey(), cache.getValue()));
    }

    private Mono<S3OsProperties> fetchS3PropsByConfigMapName(String name) {
        return client.fetch(ConfigMap.class, name)
            .map(S3OsProperties::convertFrom);
    }
}
