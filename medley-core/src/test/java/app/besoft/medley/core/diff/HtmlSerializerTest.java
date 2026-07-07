package app.besoft.medley.core.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import app.besoft.medley.core.vnode.VNode;
import org.junit.jupiter.api.Test;

class HtmlSerializerTest {

    @Test
    void serializesElementWithIdAndAttrs() {
        VNode v = new VNode.VElement("root", "div",
                Map.of("class", "counter"), Map.of(), List.of(), null);
        String html = HtmlSerializer.serialize(v);
        assertTrue(html.contains("data-medley-id=\"root\""));
        assertTrue(html.contains("class=\"counter\""));
        assertTrue(html.startsWith("<div"));
        assertTrue(html.endsWith("</div>"));
    }

    @Test
    void serializesEventsAsDataAttrs() {
        VNode v = new VNode.VElement("b", "button",
                Map.of(), Map.of("click", "increment"), List.of(), null);
        String html = HtmlSerializer.serialize(v);
        assertTrue(html.contains("data-medley-on-click=\"increment\""));
    }

    @Test
    void serializesKey() {
        VNode v = new VNode.VElement("li1", "li",
                Map.of(), Map.of(), List.of(), "k1");
        String html = HtmlSerializer.serialize(v);
        assertTrue(html.contains("data-medley-key=\"k1\""));
    }

    @Test
    void escapesTextContent() {
        VNode v = new VNode.VElement("r", "div", Map.of(), Map.of(),
                List.of(new VNode.VText("r.0", "<script>&\"")), null);
        String html = HtmlSerializer.serialize(v);
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("&amp;"));
    }

    @Test
    void escapesAttributeValues() {
        VNode v = new VNode.VElement("r", "div",
                Map.of("title", "a\"b"), Map.of(), List.of(), null);
        String html = HtmlSerializer.serialize(v);
        assertTrue(html.contains("&quot;"));
    }

    @Test
    void nestsChildren() {
        VNode child = new VNode.VElement("r.0", "span", Map.of(), Map.of(),
                List.of(new VNode.VText("r.0.0", "hi")), null);
        VNode root = new VNode.VElement("r", "div", Map.of(), Map.of(), List.of(child), null);
        String html = HtmlSerializer.serialize(root);
        assertEquals(
                "<div data-medley-id=\"r\"><span data-medley-id=\"r.0\">hi</span></div>",
                html);
    }
}
