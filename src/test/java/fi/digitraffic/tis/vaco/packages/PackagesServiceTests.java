package fi.digitraffic.tis.vaco.packages;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.nio.file.Path;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class PackagesServiceTests extends SpringBootIntegrationTestBase {

    @Autowired
    PackagesService packagesService;

    @Autowired
    QueueHandlerRepository queueHandlerRepository;

    @BeforeAll
    static void beforeAll(@Autowired VacoProperties vacoProperties) {
        awsS3Client.createBucket(CreateBucketRequest.builder().bucket(vacoProperties.s3ProcessingBucket()).build());
    }

    @Test
    void roundtrippingPackageEntityWorks() {
        ImmutableEntry entry = TestObjects.anEntry("gbfs").build();
        Entry createdEntry = queueHandlerRepository.create(entry.withTasks(TestObjects.aTask(entry).build()));
        Task task = createdEntry.tasks().get(0);
        Package saved = packagesService.createPackage(createdEntry, task, "FAKE_RULE", ImmutableS3Path.of("nothing/in/this/path"), "resulting.zip", p -> true);
        Optional<Package> loaded = packagesService.findPackage(task, "FAKE_RULE");

        assertThat(loaded.isPresent(), equalTo(true));

        assertThat(loaded.get(), equalTo(saved));
    }

    @Test
    void providesHelperForDownloadingReferencedFile() {
        ImmutableEntry entry = TestObjects.anEntry("gbfs").build();
        Entry createdEntry = queueHandlerRepository.create(entry.withTasks(TestObjects.aTask(entry).build()));
        Task task = createdEntry.tasks().get(0);
        Package saved = packagesService.createPackage(createdEntry, task, "FAKE_RULE", ImmutableS3Path.of("nothing/in/this/path"), "resulting.zip", p -> true);
        Optional<Path> loaded = packagesService.downloadPackage(entry, task, "FAKE_RULE");

        assertThat(loaded.isPresent(), equalTo(true));

        Path file = loaded.get();
        assertThat(file.getFileName(), equalTo(Path.of("resulting.zip")));
    }
}
