package run.halo.alioss;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.attachment.Policy.PolicySpec;
import run.halo.app.extension.Ref;

class AliOssAttachmentHandlerTest {

    AliOssAttachmentHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AliOssAttachmentHandler();
    }

    @Test
    void acceptHandlingWhenPolicyTemplateIsExpected() {
        var policy = mock(Policy.class);
        var spec = mock(PolicySpec.class);
        when(policy.getSpec()).thenReturn(spec);

        when(spec.getTemplateRef()).thenReturn(Ref.of("alioss"));
        assertTrue(handler.shouldHandle(policy));

        when(spec.getTemplateRef()).thenReturn(Ref.of("invalid"));
        assertFalse(handler.shouldHandle(policy));

        // policy is null
        assertFalse(handler.shouldHandle(null));
    }
}