package fi.digitraffic.tis.vaco.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.rules.validation.gtfs.CanonicalGtfsValidatorRule;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * All validation rules as Spring beans
 */
@Configuration
public class ValidationRuleConfiguration {

    @Bean
    @Qualifier("validation")
    public CanonicalGtfsValidatorRule canonicalGtfsValidatorRule(ObjectMapper objectMapper,
                                                                 VacoProperties vacoProperties,
                                                                 ErrorHandlerService errorHandlerService,
                                                                 RulesetRepository rulesetRepository,
                                                                 S3Client s3Client,
                                                                 PackagesService packagesService,
                                                                 TaskService taskService) {
        return new CanonicalGtfsValidatorRule(
            objectMapper,
            vacoProperties,
            errorHandlerService,
            rulesetRepository,
            s3Client,
            packagesService,
            taskService);
    }
}
