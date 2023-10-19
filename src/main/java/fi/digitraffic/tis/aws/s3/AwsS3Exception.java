package fi.digitraffic.tis.aws.s3;

import fi.digitraffic.tis.exceptions.LibraryException;

public class AwsS3Exception extends LibraryException {

    protected AwsS3Exception(String message) {
        super(message);
    }

    public AwsS3Exception(String message, Throwable cause) {
        super(message, cause);
    }

    protected AwsS3Exception(Throwable cause) {
        super(cause);
    }

    protected AwsS3Exception(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
