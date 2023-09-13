package fi.digitraffic.tis.aws.s3;

import org.immutables.value.Value;

import java.util.Arrays;
import java.util.List;

/**
 * Wrapper for all S3 paths to provide type level clarity on indicating what is being passed around.
 */
@Value.Immutable
public abstract class S3Path {
    @Value.Parameter
    public abstract List<String> path();

    public static S3Path of(String pathLikeString) {
        return ImmutableS3Path.of(Arrays.asList(pathLikeString.split("/")));
    }

    @Override
    public String toString() {
        return String.join("/", path());
    }
}
