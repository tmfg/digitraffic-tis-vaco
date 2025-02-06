package fi.digitraffic.tis.aws.s3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.transfer.s3.progress.TransferListener;

class SimpleTransferListener implements TransferListener {
    private final String bucketName;
    private final S3Path key;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public SimpleTransferListener(String bucketName, S3Path key) {
        this.bucketName = bucketName;
        this.key = key;
    }

    @Override
    public void transferInitiated(Context.TransferInitiated context) {
        logger.info("Initiated transfer of {}", key.asUri(bucketName));
    }

    @Override
    public void transferComplete(Context.TransferComplete context) {
        logger.info("Completed transfer of {}", key.asUri(bucketName));
    }

}
