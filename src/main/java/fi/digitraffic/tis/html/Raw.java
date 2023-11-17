package fi.digitraffic.tis.html;

import static com.google.common.base.Strings.repeat;

public final class Raw implements HtmlContent {
    private final String raw;

    public Raw(String raw) {
        this.raw = raw;
    }

    @Override
    public String build(HtmlBuildOptions buildOptions) {
        if (buildOptions.pretty()) {
            String indentString = repeat(" ", buildOptions.indent());
            return indentString + raw.replace("\n", indentString + "\n");
        } else {
            return raw;
        }
    }
}
