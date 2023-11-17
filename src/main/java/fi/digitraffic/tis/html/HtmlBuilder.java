package fi.digitraffic.tis.html;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class HtmlBuilder {
    private final List<HtmlContent> contents = new ArrayList<>();

    public HtmlBuilder html5doctype() {
        contents.add(buildOptions -> "<!DOCTYPE html>" + (buildOptions.pretty() ? "\n" : ""));
        return this;
    }

    public HtmlBuilder content(Function<ContentBuilder, Element> o) {
        ContentBuilder builder = new ContentBuilder();
        contents.add(o.apply(builder));
        return this;
    }

    public String build(HtmlBuildOptions buildOptions) {
        StringBuilder sb = new StringBuilder();
        contents.forEach(e -> sb.append(e.build(buildOptions)));
        return sb.toString();
    }
}
