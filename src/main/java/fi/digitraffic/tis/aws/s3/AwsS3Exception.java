package fi.digitraffic.tis.aws.s3;

import fi.digitraffic.exceptions.LibraryException;

public class AwsS3Exception extends LibraryException {

    public AwsS3Exception(String message) {
        super(message);
    }

    public AwsS3Exception(String message, Throwable cause) {
        super(message, cause);
    }

    public AwsS3Exception(Throwable cause) {
        super(cause);
    }

}
