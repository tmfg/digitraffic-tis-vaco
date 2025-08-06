package fi.digitraffic.tis.vaco.packages;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.ContextRecord;
import fi.digitraffic.tis.vaco.db.model.CredentialsRecord;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.db.repositories.TaskRepository;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class PackagesServiceTests extends SpringBootIntegrationTestBase {

    @Autowired
    PackagesService packagesService;

    @Autowired
    EntryRepository entryRepository;
    @Autowired
    RecordMapper recordMapper;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private TaskService taskService;

    @BeforeAll
    static void beforeAll(@Autowired VacoProperties vacoProperties) {
        createBucket(vacoProperties.s3ProcessingBucket());
        createBucket(vacoProperties.s3PackagesBucket());
    }

    @Test
    void roundtrippingPackageEntityWorks() {
        ImmutableEntry entry = TestObjects.anEntry("gtfs").build();
        EntryRecord createdEntry = entryRepository.create(entry, Optional.empty(), Optional.empty()).get();
        Task task = forceTaskCreation(createdEntry, ImmutableTask.of("FAKE_TASK", 1));
        Optional<ContextRecord> context = Optional.empty(); // TODO: add actual context
        Optional<CredentialsRecord> credentials = Optional.empty(); // TODO: add actual credentials
        Package saved = packagesService.createPackage(recordMapper.toEntryBuilder(createdEntry, context, credentials).build(), task, "FAKE_RULE", ImmutableS3Path.of("nothing/in/this/path"), "resulting.zip", p -> true);
        Optional<Package> loaded = packagesService.findPackage(task, "FAKE_RULE");

        assertThat(loaded.isPresent(), equalTo(true));

        assertThat(loaded.get(), equalTo(saved));
    }

    @Test
    void providesHelperForDownloadingReferencedFile() {
        ImmutableEntry entry = TestObjects.anEntry("gtfs").build();
        EntryRecord createdEntry = entryRepository.create(entry, Optional.empty(), Optional.empty()).get();
        Task task = forceTaskCreation(createdEntry, ImmutableTask.of("FAKE_TASK", 1));
        Optional<ContextRecord> context = Optional.empty(); // TODO: add actual context
        Optional<CredentialsRecord> credentials = Optional.empty(); // TODO: add actual credentials
        packagesService.createPackage(recordMapper.toEntryBuilder(createdEntry, context, credentials).build(), task, "FAKE_RULE", ImmutableS3Path.of(entry.publicId() + "/" + task.publicId()), "resulting.zip", p -> true);
        Optional<Path> loaded = packagesService.downloadPackage(entry, task, "FAKE_RULE");

        assertThat(loaded.isPresent(), equalTo(true));

        Path file = loaded.get();
        assertThat(file.getFileName(), equalTo(Path.of("resulting.zip")));
    }

    /**
     * XXX: This bypasses task dependency resolution and DOES NOT match with production code! This exists only to allow
     *     for testing of PackagesService more easily.
     * @param createdEntry
     * @param task
     * @return
     */
    private Task forceTaskCreation(EntryRecord createdEntry, Task task) {
        taskRepository.createTasks(createdEntry, List.of(task));
        return taskService.findTask(createdEntry.id(), task.name()).get();
    }
}
