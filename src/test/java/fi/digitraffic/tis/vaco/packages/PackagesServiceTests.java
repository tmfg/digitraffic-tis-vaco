package fi.digitraffic.tis.vaco.packages;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.packages.model.ImmutablePackage;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
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
        ImmutableTask task = TestObjects.aTask().build();
        Entry entry = queueHandlerRepository.create(TestObjects.anEntry("gbfs").addTasks(task).build());
        ImmutablePackage saved = packagesService.createPackage(entry, task, "FAKE_RULE", ImmutableS3Path.of("nothing/in/this/path"), "resulting.zip");
        Optional<Package> loaded = packagesService.findPackage(entry, "FAKE_RULE");

        assertThat(loaded.isPresent(), equalTo(true));

        assertThat(loaded.get(), equalTo(saved));
    }

    @Test
    void providesHelperForDownloadingReferencedFile() {
        ImmutableTask task = TestObjects.aTask().build();
        Entry entry = queueHandlerRepository.create(TestObjects.anEntry("gbfs").addTasks(task).build());
        ImmutablePackage saved = packagesService.createPackage(entry, task, "FAKE_RULE", ImmutableS3Path.of("nothing/in/this/path"), "resulting.zip");
        Optional<Path> loaded = packagesService.downloadPackage(entry, "FAKE_RULE");

        assertThat(loaded.isPresent(), equalTo(true));

        Path file = loaded.get();
        assertThat(file.getFileName(), equalTo(Path.of("resulting.zip")));
    }
}
