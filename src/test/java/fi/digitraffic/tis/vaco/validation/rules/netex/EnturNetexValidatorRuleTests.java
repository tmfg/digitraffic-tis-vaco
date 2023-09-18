package fi.digitraffic.tis.vaco.validation.rules.netex;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.AwsIntegrationTestBase;
import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.model.ImmutableValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.rules.validation.ValidatorRule;
import fi.digitraffic.tis.vaco.rules.validation.gtfs.CanonicalGtfsValidatorRule;
import fi.digitraffic.tis.vaco.rules.validation.netex.EnturNetexValidatorRule;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Entur's test files are from https://enturas.atlassian.net/wiki/spaces/PUBLIC/pages/728891505/NeTEx+examples+catalogue
 */
@ExtendWith(MockitoExtension.class)
class EnturNetexValidatorRuleTests extends AwsIntegrationTestBase {

    /* General TODO and implementation note:
     * NeTEx is a complex format and related work is at the time of writing in progress on Fintraffic's side. Therefore
     * this validator cannot be tested in its entirety right now; we have to return to this eventually once we have more
     * experience with the format and Fintraffic's own feeds.
     *
     * It should be noted that a common testing base class is probably reasonable for all ValidatorRules, as there's
     * lots of shared code in the mocking infrastructure of these test classes.
     */

    private static final Long MOCK_TASK_ID = 4009763L;
    private static final Long MOCK_VALIDATION_RULE_ID = 2003091L;

    static S3Path s3Root = ImmutableS3Path.of("entur-netex-test");
    static S3Path s3Input = ImmutableS3Path.of(s3Root + "/input");

    private ValidatorRule rule;

    private ObjectMapper objectMapper;
    @Mock
    private ErrorHandlerService errorHandlerService;
    @Mock
    private RulesetRepository rulesetRepository;
    private ImmutableEntry entry;
    private Task task;
    private S3Client s3Client;
    private static VacoProperties vacoProperties = TestObjects.vacoProperties();

    @BeforeAll
    static void beforeAll() {
        createBucket(vacoProperties.getS3ProcessingBucket());
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        s3Client = new S3Client(vacoProperties, s3TransferManager, awsS3Client);
        rule = new EnturNetexValidatorRule(
            rulesetRepository,
            errorHandlerService,
            objectMapper,
            s3Client,
            vacoProperties);
        entry = TestObjects.anEntry("NeTEx").build();
        task = TestObjects.aTask().entryId(entry.id()).build();
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(errorHandlerService, rulesetRepository);
    }

    @Test
    void validatesEntursExampleFilesWithoutErrors() throws URISyntaxException, IOException {
        // XXX: A lot of this is copy-paste from CanonicalGtfsValidatorRuleTest
        givenTestFile("public/testfiles/entur-netex.zip", ImmutableS3Path.of(s3Input + "/" + entry.format() + ".zip"));
        // zero errors -> no need for this. Left for future imporvements' sake
        // whenFindValidationRuleByName();
        ValidationRuleJobMessage message = ImmutableValidationRuleJobMessage.<ValidationInput>builder()
            .entry(entry)
            .task(task)
            .workDirectory(s3Input.toString())
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        ValidationReport report = rule.execute(message).join();

        assertThat(report.errors().size(), equalTo(0));
    }

    private Path forInput(String testFile) throws URISyntaxException {
        return testResource(testFile);
    }

    private Path testResource(String resourceName) throws URISyntaxException {
        URL resource = getClass().getClassLoader().getResource(resourceName);
        return Path.of(Objects.requireNonNull(resource).toURI());
    }

    private void whenFindValidationRuleByName() {
        when(rulesetRepository.findByName(EnturNetexValidatorRule.RULE_NAME)).thenReturn(Optional.of(mockValidationRule()));
    }

    @NotNull
    private static ImmutableRuleset mockValidationRule() {
        return TestObjects.aRuleset()
            .id(MOCK_VALIDATION_RULE_ID)
            .identifyingName(CanonicalGtfsValidatorRule.RULE_NAME)
            .description("injected mock version of the rule")
            .build();
    }

    private void givenTestFile(String file, S3Path target) throws URISyntaxException, IOException {
        URL resource = EnturNetexValidatorRule.class.getClassLoader().getResource(file);
        s3Client.uploadFile(vacoProperties.getS3ProcessingBucket(), target, Path.of(resource.toURI())).join();
    }

}
