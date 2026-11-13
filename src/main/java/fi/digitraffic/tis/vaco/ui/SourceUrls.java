package fi.digitraffic.tis.vaco.ui;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Removes the parts of a source URL that a visitor must not see. The query string, the fragment and the user
 * information can carry an access token or a password.
 */
public final class SourceUrls {

    /**
     * Marker that the data delivery overview uses for a company with no data.
     */
    private static final String NO_DATA = "NO_DATA";

    private SourceUrls() {}

    /**
     * Returns the URL without query string, fragment and user information.
     *
     * @param url Source URL, or null
     * @return The URL for a visitor. The result is empty when the URL cannot be parsed, so that the output holds
     *         nothing of a broken URL. The {@code NO_DATA} marker and null do not change.
     */
    public static String forVisitor(String url) {
        if (url == null || NO_DATA.equals(url)) {
            return url;
        }
        try {
            URI uri = new URI(url);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return "";
            }
            StringBuilder masked = new StringBuilder()
                .append(uri.getScheme())
                .append("://")
                .append(uri.getHost());
            if (uri.getPort() != -1) {
                masked.append(':').append(uri.getPort());
            }
            if (uri.getRawPath() != null) {
                masked.append(uri.getRawPath());
            }
            return masked.toString();
        } catch (URISyntaxException _) {
            return "";
        }
    }

    /**
     * Replaces each occurrence of the URL in the text with the URL that a visitor may see.
     *
     * @param text Text that can quote the URL, or null
     * @param url Source URL, or null
     * @return The text with the URL masked
     */
    public static String maskInText(String text, String url) {
        if (text == null || url == null || url.isEmpty()) {
            return text;
        }
        return text.replace(url, forVisitor(url));
    }
}
