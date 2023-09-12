package fi.digitraffic.tis.aws.s3;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

class S3PathTests {

    @Test
    void toStringLooksLikePlainString() {
        assertThat(ImmutableS3Path.of("foo").toString(), equalTo("foo"));
    }
}
