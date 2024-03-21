package fi.digitraffic.tis.vaco.rules.results;

import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.findings.FindingService;
import fi.digitraffic.tis.vaco.packages.model.ImmutablePackage;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static fi.digitraffic.tis.vaco.rules.ResultProcessorTestHelpers.asResultMessage;
import static fi.digitraffic.tis.vaco.rules.ResultProcessorTestHelpers.entryWithTask;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class InternalRuleResultProcessorTests extends ResultProcessorTestBase {

    private Entry entry;
    private Task downloadTask;

    private ResultMessage downloadMessage;

    private InternalRuleResultProcessor resultProcessor;
    private VacoProperties vacoProperties;
    @Mock private S3Client s3Client;
    @Mock private FindingService findingService;
    @Captor private ArgumentCaptor<ImmutablePackage> registeredPackage;

    @BeforeEach
    void setUp() {
        vacoProperties = TestObjects.vacoProperties();
        resultProcessor = new InternalRuleResultProcessor(vacoProperties, packagesService, s3Client, taskService, findingService);

        entry = entryWithTask(e -> ImmutableTask.of(new Random().nextLong(), DownloadRule.PREPARE_DOWNLOAD_TASK, 100).withId(9_000_000L));
        downloadTask = entry.tasks().get(0);
        Map<String, List<String>> uploadedFiles = Map.of("archive.zip", List.of("result"));
        downloadMessage = asResultMessage(vacoProperties, DownloadRule.PREPARE_DOWNLOAD_TASK, entry, uploadedFiles);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(s3Client, packagesService, taskService, findingService);
    }

    @Test
    void registersResultPackageAsIs() {
        givenPackageIsRegistered();
        givenTaskStatusIsMarkedAs(entry, Status.SUCCESS);

        resultProcessor.processResults(downloadMessage, entry, downloadTask);

        ImmutablePackage pkg = registeredPackage.getValue();
        assertThat(pkg.name(), equalTo("result"));
        assertThat(pkg.path(), equalTo("archive.zip"));
    }

    private void givenPackageIsRegistered() {
        given(packagesService.registerPackage(registeredPackage.capture())).willAnswer(i -> i.getArgument(0));
    }

}
