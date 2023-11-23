package fi.digitraffic.tis.vaco.email;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

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
