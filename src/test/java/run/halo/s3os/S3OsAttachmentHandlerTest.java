package run.halo.s3os;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import run.halo.app.core.extension.attachment.Policy;

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
}
