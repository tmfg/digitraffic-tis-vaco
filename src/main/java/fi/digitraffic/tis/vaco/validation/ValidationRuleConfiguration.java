package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.validation.rules.gtfs.CanonicalGtfsValidatorRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

/**
 * All validation rules as Spring beans
 */
@Configuration
public class ValidationRuleConfiguration {

    // TODO: Implement some rules

    @Bean
    public CanonicalGtfsValidatorRule canonicalGtfsValidatorRule(S3TransferManager s3TransferManager, VacoProperties vacoProperties) {
        return new CanonicalGtfsValidatorRule(s3TransferManager, vacoProperties);
    }

}
