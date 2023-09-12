package fi.digitraffic.tis.aws.s3;

import org.immutables.value.Value;

/**
 * Wrapper for all S3 paths to provide type level clarity on indicating what is being passed around.
 */
@Value.Immutable
public abstract class S3Path {
    @Value.Parameter
    public abstract String path();

    @Override
    public String toString() {
        return path();
    }
}
