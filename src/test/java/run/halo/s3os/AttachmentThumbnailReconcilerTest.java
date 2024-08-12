package run.halo.s3os;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AttachmentThumbnailReconcilerTest {

    @Test
    void isMadeWithIn1DayTest() {
        var madeTime = Instant.now().plusSeconds(2).toString();
        var result = AttachmentThumbnailReconciler.isMadeWithIn1Day(madeTime);
        assertThat(result).isTrue();

        madeTime = Instant.now().plus(Duration.ofDays(2)).toString();
        result = AttachmentThumbnailReconciler.isMadeWithIn1Day(madeTime);
        assertThat(result).isTrue();

        madeTime = Instant.now().minus(Duration.ofHours(25)).toString();
        result = AttachmentThumbnailReconciler.isMadeWithIn1Day(madeTime);
        assertThat(result).isFalse();
    }
}
