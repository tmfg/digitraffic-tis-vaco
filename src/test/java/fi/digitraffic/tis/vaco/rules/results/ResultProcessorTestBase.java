package fi.digitraffic.tis.vaco.rules.results;

import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.mockito.BDDMockito;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public abstract class ResultProcessorTestBase {

    /**
     * This is ignored because in mocking the files residing in S3 are not available for filtering.
     */
    public final static String IGNORED_PATH_VALUE = "IGNORED ON PURPOSE. If you see this in output, something is broken.";

    @Mock protected PackagesService packagesService;
    @Mock protected TaskService taskService;

    protected BDDMockito.BDDMyOngoingStubbing<Package> givenPackageIsCreated(String packageName, Entry entry, Task task) {
        return given(packagesService.createPackage(
            eq(entry),
            eq(task),
            eq(packageName),
            eq(S3Path.of("outputs")),
            eq(packageName + ".zip"),
            any()));
    }

    protected void givenTaskStatusIsMarkedAs(Status status) {
        given(taskService.markStatus(any(), eq(status))).will(a -> a.getArgument(0));
    }
}
