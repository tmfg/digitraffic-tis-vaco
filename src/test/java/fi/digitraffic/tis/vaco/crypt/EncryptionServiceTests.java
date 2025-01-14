package fi.digitraffic.tis.vaco.crypt;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.TestObjects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.kms.KmsAsyncClient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class EncryptionServiceTests {

    @Mock
    private KmsAsyncClient kmsAsyncClient;

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(kmsAsyncClient);
    }

    @Test
    void name() {
        EncryptionService service = new EncryptionService(TestObjects.vacoProperties(), new ObjectMapper(), kmsAsyncClient);
        String original = "Hail, traveler!";

        assertThat("Roundtripping simple string works",
            service.decrypt(service.encrypt(original), String.class),
            equalTo(original));

        Testing t = new Testing("yo yo yo");
        assertThat("Roundtripping complex type works",
            service.decrypt(service.encrypt(t), Testing.class),
            equalTo(t));
    }

    public record Testing(String greeting) {}
}
