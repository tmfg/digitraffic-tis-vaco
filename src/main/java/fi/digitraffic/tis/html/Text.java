package fi.digitraffic.tis.html;

import com.google.common.html.HtmlEscapers;

import static com.google.common.base.Strings.repeat;

public final class Text implements HtmlContent {
    private final String text;

    public Text(String text) {
        this.text = text;
    }

    @Override
    public String build(HtmlBuildOptions buildOptions) {
        if (buildOptions.pretty()) {
            String indentString = repeat(" ", buildOptions.indent());
            return indentString + HtmlEscapers.htmlEscaper().escape(text).replace("\n", indentString + "\n");
        } else {
            return HtmlEscapers.htmlEscaper().escape(text);
        }
    }
}
