package fi.digitraffic.tis.vaco.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.ruleset.RuleSetRepository;
import fi.digitraffic.tis.vaco.validation.rules.gtfs.CanonicalGtfsValidatorRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

/**
 * All validation rules as Spring beans
 */
@Configuration
public class ValidationRuleConfiguration {

    @Bean
    public CanonicalGtfsValidatorRule canonicalGtfsValidatorRule(
            ObjectMapper objectMapper,
            VacoProperties vacoProperties,
            S3TransferManager s3TransferManager,
            ErrorHandlerService errorHandlerService,
            RuleSetRepository rulesetRepository) {
        return new CanonicalGtfsValidatorRule(objectMapper, vacoProperties, s3TransferManager, errorHandlerService, rulesetRepository);
    }

}
