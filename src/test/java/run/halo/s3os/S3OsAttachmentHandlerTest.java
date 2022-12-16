package run.halo.s3os;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
}
