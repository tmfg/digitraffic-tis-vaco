package fi.digitraffic.tis.vaco.validation.rules.netex;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.AwsIntegrationTestBase;
import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.errorhandling.Error;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.model.ImmutableValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.rules.validation.ValidatorRule;
import fi.digitraffic.tis.vaco.rules.validation.netex.EnturNetexValidatorRule;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
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
    static S3Path s3Input = s3Root.resolve("input");
    static S3Path s3Output = s3Root.resolve("output");

    private ValidatorRule rule;

    private ObjectMapper objectMapper;
    @Mock
    private ErrorHandlerService errorHandlerService;
    @Mock
    private RulesetRepository rulesetRepository;
    @Mock
    private PackagesService packagesService;
    @Mock
    private MessagingService messagingService;
    @Mock
    private TaskService taskService;

    @Captor
    private ArgumentCaptor<List<Error>> errorCaptor;

    private ImmutableEntry entry;
    private ImmutableTask task;
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
            vacoProperties,
            packagesService,
            messagingService,
            taskService);
        entry = TestObjects.anEntry("NeTEx").build();
        task = TestObjects.aTask().entryId(entry.id()).build();
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(errorHandlerService, rulesetRepository, packagesService, messagingService, taskService);
    }

    @Test
    void validatesEntursExampleFilesWithoutErrors() throws URISyntaxException, IOException {
        // XXX: A lot of this is copy-paste from CanonicalGtfsValidatorRuleTest
        givenTestFile("public/testfiles/entur-netex.zip", ImmutableS3Path.of(s3Input + "/" + entry.format() + ".zip"));
        // zero errors -> no need for this. Left for future improvements' sake
        //whenFindValidationRuleByName();
        when(taskService.findTask(eq(entry.id()), eq(RuleName.NETEX_ENTUR_1_0_1))).thenReturn(task);
        when(taskService.trackTask(eq(task), eq(ProcessingState.START))).thenReturn(task);
        when(taskService.trackTask(eq(task), eq(ProcessingState.COMPLETE))).thenReturn(task);

        whenReportErrors();
        ValidationRuleJobMessage message = ImmutableValidationRuleJobMessage.builder()
            .entry(entry)
            .task(task)
            .inputs(s3Input.toString())
            .outputs(s3Output.toString())
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        ValidationReport report = rule.execute(message).join();

        assertThat(report.errors().size(), equalTo(0));

        verify(packagesService).createPackage(
            eq(entry),
            eq(task),
            eq(RuleName.NETEX_ENTUR_1_0_1),
            eq(s3Output),
            eq("content.zip"));
    }

    private void whenReportErrors() {
        doAnswer(voidCall()).when(errorHandlerService).reportErrors(errorCaptor.capture());
    }

    @NotNull
    private static Answer voidCall() {
        return invocation -> {
            Object[] args = invocation.getArguments();
            Object mock = invocation.getMock();
            return null;
        };
    }

    private void givenTestFile(String file, S3Path target) throws URISyntaxException, IOException {
        URL resource = EnturNetexValidatorRule.class.getClassLoader().getResource(file);
        s3Client.uploadFile(vacoProperties.getS3ProcessingBucket(), target, Path.of(resource.toURI())).join();
    }

}
