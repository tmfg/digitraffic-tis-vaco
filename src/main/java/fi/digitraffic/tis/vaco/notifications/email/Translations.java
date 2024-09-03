package fi.digitraffic.tis.vaco.notifications.email;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public class Translations {
    private final ResourceBundle bundle;

    public Translations(String language, String bundleName) {
        this.bundle = ResourceBundle.getBundle(bundleName, Locale.forLanguageTag(language));
    }

    public String get(String key) {
        return bundle.getString(key);
    }

    public String get(String key, Map<String, String> substitutions) {
        String translation = get(key);
        for (Map.Entry<String, String> substitution : substitutions.entrySet()) {
            translation = translation.replace("{" + substitution.getKey() + "}", substitution.getValue());
        }
        return translation;
    }
}
