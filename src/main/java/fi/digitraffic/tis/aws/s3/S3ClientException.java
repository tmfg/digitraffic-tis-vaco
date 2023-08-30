package fi.digitraffic.tis.aws.s3;

import fi.digitraffic.tis.TisException;

class S3ClientException extends TisException {
    public S3ClientException(String message) {
        super(message);
    }

    public S3ClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
