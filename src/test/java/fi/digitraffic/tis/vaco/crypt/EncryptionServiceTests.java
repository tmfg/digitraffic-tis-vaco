package fi.digitraffic.tis.vaco.crypt;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.TestObjects;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class EncryptionServiceTests {

    @Test
    void name() {
        EncryptionService service = new EncryptionService(TestObjects.vacoProperties(), new ObjectMapper());
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
