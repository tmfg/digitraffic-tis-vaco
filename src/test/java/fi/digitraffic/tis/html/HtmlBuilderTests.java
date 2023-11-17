package fi.digitraffic.tis.html;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class HtmlBuilderTests {

    @Test
    void canProduceHtml5Doctype() {
        HtmlBuilder htmlBuilder = new HtmlBuilder();

        htmlBuilder.html5doctype();

        assertThat(htmlBuilder.build(new HtmlBuildOptions(0, false)), equalTo("<!DOCTYPE html>"));
        assertThat(htmlBuilder.build(new HtmlBuildOptions(0, true)), equalTo("<!DOCTYPE html>\n"));
    }

    @Test
    void canCreateEmptyElement() {
        HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.content(content -> content.element("simple"));
        assertThat(htmlBuilder.build(new HtmlBuildOptions(0, false)), equalTo("<simple />"));
        assertThat(htmlBuilder.build(new HtmlBuildOptions(0, true)), equalTo("<simple />"));
    }

    @Test
    void canCreateElementWithAttributes() {
        HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.content(content -> content.element("attrs").attribute("a", "1").attribute("b", "2"));
        assertThat(htmlBuilder.build(new HtmlBuildOptions(0, false)), equalTo("<attrs a=\"1\" b=\"2\" />"));
        assertThat(htmlBuilder.build(new HtmlBuildOptions(0, true)), equalTo("<attrs a=\"1\"\n" +
                                                                             "       b=\"2\" />"));
    }

    @Test
    void canCreateElementWithChildren() {
        HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.content(content -> content.element("p")
            .children(content.element("c")));
        assertThat(htmlBuilder.build(new HtmlBuildOptions(0, false)), equalTo("<p><c /></p>"));
        assertThat(htmlBuilder.build(new HtmlBuildOptions(0, true)), equalTo("<p>\n" +
                                                                             "    <c />\n" +
                                                                             "</p>"));
    }

    @Test
    void alignsTextContentAccordingToPrettyRules() {
        HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.content(content -> content.element("h1").text("Hello World!"));

        assertThat(htmlBuilder.build(new HtmlBuildOptions(0, false)), equalTo("<h1>Hello World!</h1>"));
        assertThat(htmlBuilder.build(new HtmlBuildOptions(0, true)), equalTo("<h1>\n    Hello World!\n</h1>"));
        assertThat(htmlBuilder.build(new HtmlBuildOptions(1, true)), equalTo(" <h1>\n     Hello World!\n </h1>"));
    }

    @Test
    void name() {
        HtmlBuilder htmlBuilder = new HtmlBuilder();

        String html = htmlBuilder.html5doctype()
            .content(c ->
                c.element("html")
                    .attribute("lang", "en")
                    .attribute("xmlns", "http://www.w3.org/1999/xhtml")
                    .attribute("xmlns:v", "urn:schemas-microsoft-com:vml")
                    .attribute("xmlns:o", "urn:schemas-microsoft-com:office:office")
                    .children(
                        c.element("head")
                            .children(
                                c.element("meta")
                                    .attribute("name", "viewport")
                                    .attribute("content", "width=device-width, initial-scale=1.0"),
                                c.element("meta")
                                    .attribute("http-equiv", "Content-Type")
                                    .attribute("content", "text/html; charset=UTF-8"),
                                c.element("style").children(
                                    c.raw("@media only screen and (max-width: 620px) {}"))),
                        c.element("body")
                            .children(c.element("h1").text("Hello World!"))))
            .build(new HtmlBuildOptions(0, true));

        System.out.println("html = " + html);

        String entirePage = """
           <!DOCTYPE html>
           <html lang="en"
                 xmlns="http://www.w3.org/1999/xhtml"
                 xmlns:v="urn:schemas-microsoft-com:vml"
                 xmlns:o="urn:schemas-microsoft-com:office:office">
               <head>
                   <meta name="viewport"
                         content="width=device-width, initial-scale=1.0" />

                   <meta http-equiv="Content-Type"
                         content="text/html; charset=UTF-8" />

                   <style>
                       @media only screen and (max-width: 620px) {}
                   </style>
               </head>

               <body>
                   <h1>
                       Hello World!
                   </h1>
               </body>
           </html>""";

        assertThat(html, equalTo(entirePage));
    }

}
