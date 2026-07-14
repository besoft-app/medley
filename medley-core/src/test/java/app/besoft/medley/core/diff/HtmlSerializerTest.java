package app.besoft.medley.core.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import app.besoft.medley.core.vnode.VNode;
import org.junit.jupiter.api.Test;

class HtmlSerializerTest {

    /**
     * Dynamic text (an interpolation) is patchable, so it must be addressable: it gets a host element
     * carrying <b>its own</b> text-node id. Without this the client's {@code [data-medley-id]} lookup
     * finds nothing and the text patch is silently dropped — see {@link PatchAddressabilityTest}.
     */
    @Test
    void dynamicTextIsWrappedInAnAddressableHost() {
        VNode v = new VNode.VElement("root.3", "span", Map.of(), Map.of(),
                List.of(new VNode.VText("root.3.0", "n: "),
                        new VNode.VText("root.3.1", "0", true)), null);
        String html = HtmlSerializer.serialize(v);
        assertEquals("<span data-medley-id=\"root.3\">n: "
                + "<medley-text data-medley-id=\"root.3.1\">0</medley-text></span>", html);
    }

    /** Static text is never patched, so it stays bare — no marker for every whitespace node. */
    @Test
    void staticTextStaysBare() {
        VNode v = new VNode.VElement("r", "div", Map.of(), Map.of(),
                List.of(new VNode.VText("r.0", "hello")), null);
        assertEquals("<div data-medley-id=\"r\">hello</div>", HtmlSerializer.serialize(v));
    }

    @Test
    void dynamicTextIsStillEscapedInsideItsHost() {
        VNode v = new VNode.VElement("r", "div", Map.of(), Map.of(),
                List.of(new VNode.VText("r.0", "<script>&", true)), null);
        String html = HtmlSerializer.serialize(v);
        assertTrue(html.contains(">&lt;script&gt;&amp;</medley-text>"), html);
    }

    /**
     * A raw-text element cannot contain a marker (its content model is text), so the renderer hands it a
     * single text node carrying the element's own id and the patch addresses the element itself.
     */
    @Test
    void rawTextElementGetsNoMarker() {
        VNode v = new VNode.VElement("r", "textarea", Map.of(), Map.of(),
                List.of(new VNode.VText("r", "draft", true)), null);
        assertEquals("<textarea data-medley-id=\"r\">draft</textarea>", HtmlSerializer.serialize(v));
    }

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
