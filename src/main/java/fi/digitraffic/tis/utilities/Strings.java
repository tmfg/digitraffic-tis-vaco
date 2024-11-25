package fi.digitraffic.tis.utilities;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntPredicate;

public final class Strings {
    private Strings() {}

    public static final String BOM_UTF_8 = new String(new int[ ]{ 0xEF, 0xBB, 0xBF }, 0, 3);
    public static final String BOM_UTF_16_BE = "\uFEFF";
    public static final String BOM_UTF_16_LE = "\uFFFE";
    public static final String BOM_UTF_32_BE = "\u0000\uFEFF";
    public static final String BOM_UTF_32_LE = "\uFFFE\u0000";

    /**
     * Inspects given string on character level and strips any BOM if present.
     *
     * @param s string to sanitize
     * @return string without BOM or null if input was null
     */
    public static String stripBOM(String s) {
        if (s == null) {
            return null;
        }
        // ladder replacements from longest to shortest
        if (s.startsWith(BOM_UTF_32_LE)) {
            return s.substring(BOM_UTF_32_LE.length());
        }
        if (s.startsWith(BOM_UTF_32_BE)) {
            return s.substring(BOM_UTF_32_BE.length());
        }
        if (s.startsWith(BOM_UTF_16_LE)) {
            return s.substring(BOM_UTF_16_LE.length());
        }
        if (s.startsWith(BOM_UTF_16_BE)) {
            return s.substring(BOM_UTF_16_BE.length());
        }
        if (s.startsWith(BOM_UTF_8)) {
            return s.substring(BOM_UTF_8.length());
        }
        return s;
    }

    /**
     * Tests that given String <code>s</code> matches (=contains only) characters in given <code>alphabet</code>.
     * <p>
     * Tests only exactly given alphabet, so no automatic case insensitivy or anything like that.
     *
     * @param alphabet Alphabet of allowed characters.
     * @param s String to test
     * @return true if all charactes in string match, false otherwise
     */
    public static boolean matches(char[] alphabet, String s) {
        return s.chars().allMatch(asIntPredicate(alphabet));
    }

    private static IntPredicate asIntPredicate(char[] alphabet) {
        Set<Integer> alphabetSet = new HashSet<>();

        for (char c : alphabet) {
            alphabetSet.add((int) c);
        }

        return alphabetSet::contains;
    }
}
