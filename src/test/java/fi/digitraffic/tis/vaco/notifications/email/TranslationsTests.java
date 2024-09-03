package fi.digitraffic.tis.vaco.notifications.email;

import fi.digitraffic.tis.vaco.notifications.email.Translations;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class TranslationsTests {

    @Test
    void supportsSimpleSubstitution() {
        Translations t = new Translations("en", "translations/substitutions");
        assertThat(t.get("keyWithSubstitution", Map.of("word", "expression")),
            equalTo("The curlied expression should be substituted."));
    }

    @Test
    void wontReplaceUndefinedSubtitutions() {
        Translations t = new Translations("en", "translations/substitutions");
        assertThat(t.get("keyWithSubstitution", Map.of("not_present", "expression")),
            equalTo("The curlied {word} should be substituted."));
    }
}
