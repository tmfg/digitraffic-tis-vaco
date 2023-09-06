package fi.digitraffic.tis.vaco.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.rules.validation.gtfs.CanonicalGtfsValidatorRule;
import fi.digitraffic.tis.vaco.rules.validation.netex.EnturNetexValidatorRule;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

/**
 * All validation rules as Spring beans
 */
@Configuration
public class ValidationRuleConfiguration {

    @Bean
    @Qualifier("validation")
    public CanonicalGtfsValidatorRule canonicalGtfsValidatorRule(
            ObjectMapper objectMapper,
            VacoProperties vacoProperties,
            S3TransferManager s3TransferManager,
            ErrorHandlerService errorHandlerService,
            RulesetRepository rulesetRepository) {
        return new CanonicalGtfsValidatorRule(objectMapper, vacoProperties, s3TransferManager, errorHandlerService, rulesetRepository);
    }

    @Bean
    @Qualifier("validation")
    public EnturNetexValidatorRule enturNetexValidatorRule(
        RulesetRepository rulesetRepository,
        ErrorHandlerService errorhandlerService,
        ObjectMapper objectMapper) {
        return new EnturNetexValidatorRule(rulesetRepository, errorhandlerService, objectMapper);
    }
}
