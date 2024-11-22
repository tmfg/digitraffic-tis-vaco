package fi.digitraffic.tis.vaco.hamcrest;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import fi.digitraffic.tis.utilities.Strings;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

/**
 * Some custom Hamcrest matchers to make life easier when dealing with VACO data.
 */
public class VacoMatchers {
    public static TypeSafeMatcher<String> nanoId() {
        return new TypeSafeMatcher<String>() {
            @Override
            protected boolean matchesSafely(String item) {
                return item != null && item.length() == NanoIdUtils.DEFAULT_SIZE && Strings.matches(NanoIdUtils.DEFAULT_ALPHABET, item);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("does not match expected Nanoid pattern (21 characters with URI safe alphabet)");
            }
        };
    }
}
