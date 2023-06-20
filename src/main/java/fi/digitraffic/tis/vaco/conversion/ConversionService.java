package fi.digitraffic.tis.vaco.conversion;

import fi.digitraffic.tis.http.HttpClientUtility;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.conversion.model.ImmutableConversionJobMessage;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import fi.digitraffic.tis.vaco.validation.rules.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ConversionService {

    public static final String DOWNLOAD_PHASE = "conversion.download";
    public static final String RULESET_SELECTION_PHASE = "conversion.rulesets";
    public static final String EXECUTION_PHASE = "conversion.execute";
    public static final String OUTPUT_VALIDATION_PHASE = "conversion.outputvalidation";

    private static final Logger LOGGER = LoggerFactory.getLogger(ConversionService.class);

    private final S3TransferManager s3TransferManager;
    private final QueueHandlerService queueHandlerService;
    private final HttpClientUtility httpClientUtility;
    private final RulesetRepository rulesetRepository;
    private final Map<String, Rule> rules;
    private final ErrorHandlerService errorHandlerService;

    public ConversionService(VacoProperties vacoProperties,
                             S3TransferManager s3TransferManager,
                             QueueHandlerService queueHandlerService,
                             HttpClientUtility httpClientUtility,
                             RulesetRepository rulesetRepository,
                             List<Rule> rules,
                             ErrorHandlerService errorHandlerService) {
        this.s3TransferManager = s3TransferManager;
        this.queueHandlerService = queueHandlerService;
        this.httpClientUtility = httpClientUtility;
        this.rulesetRepository = rulesetRepository;
        this.rules = rules.stream().collect(Collectors.toMap(Rule::getIdentifyingName, Function.identity()));
        this.errorHandlerService = errorHandlerService;
    }

    public ImmutableConversionJobMessage convert(ImmutableConversionJobMessage jobDescription) {
        LOGGER.info("Convert {}", jobDescription);

        //Result<ImmutableFileReferences> s3path = downloadFile(jobDescription.message());

        return jobDescription;
    }

}
