package fi.digitraffic.tis.html;

import java.util.Map;
import java.util.function.Consumer;

public class ContentBuilder {

    public Element element(String name) {
        return new Element(name);
    }

    public Element element(String name, Consumer<Element> o) {
        Element builder = new Element(name);
        o.accept(builder);
        return builder;
    }

    public Element element(String name, Map<String, String> attributes) {
        Element builder = new Element(name);
        builder.attributes(attributes);
        return builder;
    }

    public HtmlContent raw(String raw) {
        return new Raw(raw);
    }
}
