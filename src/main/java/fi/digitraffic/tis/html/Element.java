package fi.digitraffic.tis.html;

import com.google.common.html.HtmlEscapers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Strings.repeat;

public final class Element implements HtmlContent {
    private final String name;
    private final Map<String, String> attributes;
    private final List<HtmlContent> children;

    public Element(String name) {
        this.name = name;
        this.attributes = new LinkedHashMap<>();
        this.children = new ArrayList<>();
    }

    @Override
    public String build(HtmlBuildOptions buildOptions) {
        StringBuilder sb = new StringBuilder();
        String indentString = repeat(" ", buildOptions.indent());
        if (buildOptions.pretty()) {
            sb.append(indentString);
        }
        sb.append("<").append(name).append(buildAttributes(buildOptions));
        if (!children.isEmpty()) {
            sb.append(">");
            sb.append(buildChildren(new HtmlBuildOptions(buildOptions.indent() + 4, buildOptions.pretty())));
            if (buildOptions.pretty()) {
                sb.append(indentString);
            }
            sb.append("</").append(name).append(">");
        } else {
            sb.append(" />");
        }
        return sb.toString();
    }

    private String buildAttributes(HtmlBuildOptions buildOptions) {
        StringBuilder sb = new StringBuilder();
        String indentString = repeat(" ", buildOptions.indent() + name.length() + 1);
        List<String> copyOf = List.copyOf(attributes.keySet());

        for (int i = 0; i < copyOf.size(); i++) {
            String key = copyOf.get(i);
            sb.append(" ")
                .append(key)
                .append("=\"")
                .append(HtmlEscapers.htmlEscaper().escape(attributes.get(key)))
                .append("\"");
            if (buildOptions.pretty() && (i != copyOf.size() - 1)) {
                sb.append("\n").append(indentString);
            }
        }
        return sb.toString();
    }

    private String buildChildren(HtmlBuildOptions buildOptions) {
        StringBuilder sb = new StringBuilder();
        children.forEach(c -> {
            if (buildOptions.pretty()) {
                sb.append("\n");
            }
            sb.append(c.build(buildOptions));
            if (buildOptions.pretty()) {
                sb.append("\n");
            }
        });

        return sb.toString();
    }

    public Element attribute(String attribute, String value) {
        attributes.put(attribute, value);
        return this;
    }

    public Element children(HtmlContent... c) {
        Collections.addAll(this.children, c);
        return this;
    }

    public Element children(List<HtmlContent> c) {
        children.addAll(c);
        return this;
    }

    public Element attributes(Map<String, String> attributes) {
        this.attributes.putAll(attributes);
        return this;
    }

    public Element raw(String raw) {
        children.add(new Raw(raw));
        return this;
    }

    public Element text(String text) {
        children.add(new Text(text));
        return this;
    }
}
