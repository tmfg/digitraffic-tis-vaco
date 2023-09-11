package fi.digitraffic.tis.vaco.packages;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.packages.model.ImmutablePackage;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

class PackagesServiceTests extends SpringBootIntegrationTestBase {

    @Autowired
    PackagesService packagesService;

    @Autowired
    QueueHandlerRepository queueHandlerRepository;

    @BeforeAll
    static void beforeAll(@Autowired VacoProperties vacoProperties) {
        awsS3Client.createBucket(CreateBucketRequest.builder().bucket(vacoProperties.getS3ProcessingBucket()).build());
    }

    @Test
    void roundtrippingPackageEntityWorks() {
        ImmutableTask task = TestObjects.aTask().build();
        ImmutableEntry entry = queueHandlerRepository.create(TestObjects.anEntry("gbfs").addTasks(task).build());
        ImmutablePackage saved = packagesService.createPackage(entry, task, "FAKE_RULE", "nothing/in/this/path", "resulting.zip");
        Optional<Package> loaded = packagesService.findPackage(entry.publicId(), "FAKE_RULE");

        assertThat(loaded.isPresent(), equalTo(true));

        assertThat(loaded.get(), equalTo(saved));
    }
}
