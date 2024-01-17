package fi.digitraffic.tis.vaco.rules.internal;

import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class StopsAndQuaysRuleTests {

    private StopsAndQuaysRule rule;
    private VacoProperties vacoProperties;

    @Mock
    private TaskService taskService;
    @Mock
    private S3Client s3Client;

    @Captor
    private ArgumentCaptor<S3Path> targetPath;
    @Captor
    private ArgumentCaptor<Path> sourcePath;

    @BeforeEach
    void setUp() {
        vacoProperties = TestObjects.vacoProperties();
        rule = new StopsAndQuaysRule(taskService, vacoProperties, s3Client);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(taskService, s3Client);
    }

    @Test
    void copiesStaticFileToOutputs() {
        ImmutableEntry.Builder entryBuilder = TestObjects.anEntry("gtfs").id(9500000L);
        Task saqTask = ImmutableTask.of(-1L, StopsAndQuaysRule.STOPS_AND_QUAYS_TASK, -1).withId(5000000L);
        Entry entry = entryBuilder.addTasks(saqTask).build();

        given(taskService.findTask(entry.id(), StopsAndQuaysRule.STOPS_AND_QUAYS_TASK)).willReturn(Optional.of(saqTask));
        given(taskService.trackTask(entry, saqTask, ProcessingState.START)).willReturn(saqTask);
        given(s3Client.uploadFile(eq(vacoProperties.s3ProcessingBucket()), targetPath.capture(), sourcePath.capture())).willReturn(CompletableFuture.completedFuture(null));
        given(taskService.trackTask(entry, saqTask, ProcessingState.COMPLETE)).willReturn(saqTask);

        ResultMessage result = rule.execute(entry).join();

        assertThat(result.ruleName(), equalTo(StopsAndQuaysRule.STOPS_AND_QUAYS_TASK));

        assertThat(targetPath.getValue().toString(), equalTo("entries/" + entry.publicId() + "/tasks/prepare.stopsAndQuays/rules/prepare.stopsAndQuays/output/stopsAndQuays.zip"));
    }
}
