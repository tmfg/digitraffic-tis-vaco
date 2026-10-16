package fi.digitraffic.tis.aws.s3;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DownloadedFileTests {

    @Test
    void rejectsNullLastModified() {
        assertThrows(NullPointerException.class, () -> new S3Client.DownloadedFile(1L, null));
    }

    @Test
    void acceptsNonNullLastModified() {
        Instant instant = Instant.parse("2026-01-01T00:00:00Z");

        S3Client.DownloadedFile downloaded = new S3Client.DownloadedFile(1L, instant);

        assertThat(downloaded.lastModified(), equalTo(instant));
        assertThat(downloaded.contentLength(), equalTo(1L));
    }
}
