package run.halo.s3os;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import run.halo.app.core.attachment.ThumbnailSize;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S3OsAttachmentHandlerTest {

    S3OsAttachmentHandler handler;

    @BeforeEach
    void setUp() {
        handler = new S3OsAttachmentHandler();
    }

    @Test
    void acceptHandlingWhenPolicyTemplateIsExpected() {
        var policy = mock(Policy.class);
        var spec = mock(Policy.PolicySpec.class);
        when(policy.getSpec()).thenReturn(spec);

        when(spec.getTemplateName()).thenReturn("s3os");
        assertTrue(handler.shouldHandle(policy));

        when(spec.getTemplateName()).thenReturn("invalid");
        assertFalse(handler.shouldHandle(policy));

        // policy is null
        assertFalse(handler.shouldHandle(null));
    }

    @Test
    void reshapeDataBufferWithSmallerBufferSize() {
        var handler = new S3OsAttachmentHandler();
        var factory = DefaultDataBufferFactory.sharedInstance;
        var content = Flux.<DataBuffer>fromIterable(List.of(factory.wrap("halo".getBytes())));

        StepVerifier.create(handler.reshape(content, 2))
            .assertNext(dataBuffer -> {
                var str = dataBuffer.toString(UTF_8);
                assertEquals("ha", str);
            })
            .assertNext(dataBuffer -> {
                var str = dataBuffer.toString(UTF_8);
                assertEquals("lo", str);
            })
            .verifyComplete();
    }

    @Test
    void reshapeDataBufferWithBiggerBufferSize() {
        var handler = new S3OsAttachmentHandler();
        var factory = DefaultDataBufferFactory.sharedInstance;
        var content = Flux.<DataBuffer>fromIterable(List.of(factory.wrap("halo".getBytes())));

        StepVerifier.create(handler.reshape(content, 10))
            .assertNext(dataBuffer -> {
                var str = dataBuffer.toString(UTF_8);
                assertEquals("halo", str);
            })
            .verifyComplete();
    }

    @Test
    void reshapeDataBuffersWithBiggerBufferSize() {
        var handler = new S3OsAttachmentHandler();
        var factory = DefaultDataBufferFactory.sharedInstance;
        var content = Flux.<DataBuffer>fromIterable(List.of(
            factory.wrap("ha".getBytes()),
            factory.wrap("lo".getBytes())
        ));

        StepVerifier.create(handler.reshape(content, 3))
            .assertNext(dataBuffer -> {
                var str = dataBuffer.toString(UTF_8);
                assertEquals("hal", str);
            })
            .assertNext(dataBuffer -> {
                var str = dataBuffer.toString(UTF_8);
                assertEquals("o", str);
            })
            .verifyComplete();
    }

    @Test
    void shouldGetThumbnailsIfPatternIsQueryParam() {
        var attachment = createAttachment("https://s3.halo.run/halo.png?existing-query=existing-value");

        var policy = new Policy();
        policy.setSpec(new Policy.PolicySpec());
        policy.getSpec().setTemplateName("s3os");

        var configMap = new ConfigMap();
        configMap.setData(new HashMap<>());
        configMap.getData().put("default", """
            {
              "thumbnailParamPattern": "?width={width}&quality=80"
            }
            """);
        handler.getThumbnailLinks(attachment, policy, configMap)
            .as(StepVerifier::create)
            .expectNext(Map.of(
                ThumbnailSize.S, URI.create("https://s3.halo.run/halo.png?existing-query=existing-value&width=400&quality=80"),
                ThumbnailSize.M, URI.create("https://s3.halo.run/halo.png?existing-query=existing-value&width=800&quality=80"),
                ThumbnailSize.L, URI.create("https://s3.halo.run/halo.png?existing-query=existing-value&width=1200&quality=80"),
                ThumbnailSize.XL, URI.create("https://s3.halo.run/halo.png?existing-query=existing-value&width=1600&quality=80")
            ))
            .verifyComplete();
    }

    @Test
    void shouldGetThumbnailsIfPatternIsPath() {
        var attachment = createAttachment("https://s3.halo.run/existing-path/halo.png");

        var policy = new Policy();
        policy.setSpec(new Policy.PolicySpec());
        policy.getSpec().setTemplateName("s3os");

        var configMap = new ConfigMap();
        configMap.setData(new HashMap<>());
        configMap.getData().put("default", """
            {
              "thumbnailParamPattern": "!path/width/{width}"
            }
            """);
        handler.getThumbnailLinks(attachment, policy, configMap)
            .as(StepVerifier::create)
            .expectNext(Map.of(
                ThumbnailSize.S, URI.create("https://s3.halo.run/existing-path/halo.png!path/width/400"),
                ThumbnailSize.M, URI.create("https://s3.halo.run/existing-path/halo.png!path/width/800"),
                ThumbnailSize.L, URI.create("https://s3.halo.run/existing-path/halo.png!path/width/1200"),
                ThumbnailSize.XL, URI.create("https://s3.halo.run/existing-path/halo.png!path/width/1600")
            ))
            .verifyComplete();
    }

    @Test
    void shouldGetEmptyThumbnailsIfNoPattern() {
        var attachment = createAttachment("https://s3.halo.run/halo.png");

        var policy = new Policy();
        policy.setSpec(new Policy.PolicySpec());
        policy.getSpec().setTemplateName("s3os");

        var configMap = new ConfigMap();
        configMap.setData(new HashMap<>());
        configMap.getData().put("default", """
            {}
            """);
        handler.getThumbnailLinks(attachment, policy, configMap)
            .as(StepVerifier::create)
            .expectNext(Map.of())
            .verifyComplete();
    }

    @Test
    void shouldGetEmptyThumbnailsIfNotImage() {
        var attachment = createAttachment("application/pdf", "https://s3.halo.run/halo.pdf");

        var policy = new Policy();
        policy.setSpec(new Policy.PolicySpec());
        policy.getSpec().setTemplateName("s3os");

        var configMap = new ConfigMap();
        configMap.setData(new HashMap<>());
        configMap.getData().put("default", """
            {
              "thumbnailParamPattern": "!path/width/{width}"
            }
            """);
        handler.getThumbnailLinks(attachment, policy, configMap)
            .as(StepVerifier::create)
            .expectNext(Map.of())
            .verifyComplete();
    }

    static Attachment createAttachment(String permalink) {
        return createAttachment("image/png", permalink);
    }

    static Attachment createAttachment(String mediaType, String permalink) {
        var attachment = new Attachment();
        attachment.setMetadata(new Metadata());
        attachment.getMetadata().setName("fake-attachment");
        attachment.setSpec(new Attachment.AttachmentSpec());
        attachment.getSpec().setMediaType(mediaType);
        attachment.setStatus(new Attachment.AttachmentStatus());
        attachment.getStatus().setPermalink(permalink);
        return attachment;
    }

}
