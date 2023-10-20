package fi.digitraffic.tis.utilities;

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

}
