package fi.digitraffic.tis.aws.s3;

import org.immutables.value.Value;

import java.util.ArrayList;
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
        if (pathLikeString.contains("s3://")) {
            throw new AwsS3Exception("S3Path should not contain s3://! Given path was " + pathLikeString);
        }
        pathLikeString = pathLikeString.trim();
        if (pathLikeString.startsWith("/")) {
            pathLikeString = pathLikeString.substring(1);
        }
        return ImmutableS3Path.of(Arrays.asList(pathLikeString.split("/")));
    }

    @Override
    public String toString() {
        return String.join("/", path());
    }

    public S3Path parent() {
        if (path().size() <= 1) {
            return ImmutableS3Path.of(List.of());
        } else {
            List<String> parentPath = new ArrayList<>(path());
            parentPath.remove(path().size() - 1);
            return ImmutableS3Path.of(parentPath);
        }
    }

    public String getLast() {
        return path().get(path().size() - 1);
    }

    public ImmutableS3Path resolve(String more) {
        List<String> newPath = new ArrayList<>(path());
        newPath.addAll(Arrays.asList(more.split(("/"))));
        return ImmutableS3Path.of(newPath);
    }

    public String asUri(String bucket) {
        return "s3://" + bucket + "/" + this;
    }
}
