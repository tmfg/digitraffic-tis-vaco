package fi.digitraffic.tis.aws.s3;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class S3PathTests {

    @Test
    void toStringLooksLikePlainString() {
        assertThat(ImmutableS3Path.of("foo").toString(), equalTo("foo"));
    }

    @Test
    void canResolveChildren() {
        assertThat(ImmutableS3Path.of(List.of("a", "b")).resolve("c").toString(), equalTo("a/b/c"));
        assertThat(ImmutableS3Path.of(List.of("a", "b")).resolve("c/d/e").toString(), equalTo("a/b/c/d/e"));
    }

    @Test
    void canResolveParent() {
        assertThat(ImmutableS3Path.of(List.of("a", "b", "c")).parent().toString(), equalTo("a/b"));
        assertThat(ImmutableS3Path.of(List.of("a", "b", "c")).parent().parent().toString(), equalTo("a"));
    }

    @Test
    void resolvingParentOfEmptyPathResultsInEmptyPath() {
        assertThat(ImmutableS3Path.of("").parent().toString(), equalTo(""));

    }
}
