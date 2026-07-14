package app.besoft.medley.core.diff;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import app.besoft.medley.core.template.TemplateRenderer;
import app.besoft.medley.core.vnode.VNode;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * <b>The addressability invariant.</b> Every id the differ can put in a patch must be reachable in the
 * DOM the client actually has — i.e. it must belong to an element carrying {@code data-medley-id} in the
 * serialized HTML. The client resolves a patch target with
 * {@code querySelector('[data-medley-id="…"]')} and nothing else, so an id that no element carries is a
 * patch that silently disappears.
 *
 * <p>This exists because that is precisely what happened: the differ addressed a text update by the
 * <em>text node's</em> id, {@code HtmlSerializer} gave ids to elements only, and every text patch was
 * dropped in the browser while the whole suite stayed green — the Java tests asserted the patch, the JS
 * harness fabricated an element carrying the text patch's id, and the dev-server checked the wire. None
 * compared the two halves. This test compares them, for the whole patch surface.</p>
 */
class PatchAddressabilityTest {

    // --- the templates a component author actually writes ---

    @Test
    void interpolationMixedWithLiteralText() {
        assertAddressable("<span>{{ label }}: {{ count }}</span>", new Ctx(0), new Ctx(1));
    }

    @Test
    void interpolationAsTheSoleChild() {
        assertAddressable("<span>{{ count }}</span>", new Ctx(0), new Ctx(1));
    }

    @Test
    void interpolationNestedInsideMarkup() {
        assertAddressable("<p>hi {{ label }} <b>{{ count }}</b></p>", new Ctx(0), new Ctx(1));
    }

    @Test
    void conditionalTurningOnAndOff() {
        assertAddressable("<div><button *if=\"count > 0\">reset</button><span>{{ count }}</span></div>",
                new Ctx(0), new Ctx(1));
        assertAddressable("<div><button *if=\"count > 0\">reset</button><span>{{ count }}</span></div>",
                new Ctx(1), new Ctx(0));
    }

    @Test
    void keyedListAddRemoveAndInPlaceEdit() {
        String tpl = "<ul><li *for=\"item : items\" :key=\"item.id\">{{ item.text }}</li></ul>";
        assertAddressable(tpl, Ctx.items("a:one", "b:two"), Ctx.items("a:one", "b:two", "c:three"));
        assertAddressable(tpl, Ctx.items("a:one", "b:two"), Ctx.items("a:one"));
        assertAddressable(tpl, Ctx.items("a:one", "b:two"), Ctx.items("a:ONE", "b:two"));
    }

    @Test
    void boundAttributeAndEvent() {
        assertAddressable("<button :data-n=\"count\" @click=\"go\">{{ count }}</button>",
                new Ctx(0), new Ctx(1));
    }

    /**
     * Raw-text elements ({@code <textarea>}, {@code <title>}, {@code <option>}) cannot host a marker
     * element — their content model is text — so their dynamic text must be addressed by the element's
     * own id. Either way the invariant is the same: the patch must land on an element that exists.
     */
    @Test
    void rawTextElement() {
        assertAddressable("<textarea>{{ label }}</textarea>", new Ctx(0), new Ctx(1) {{ label = "x"; }});
    }

    // --- the invariant ---

    /**
     * Render both states, diff them, and require that every patch the differ produced can be resolved
     * against the serialized HTML exactly the way {@code medley.js} would resolve it.
     */
    private void assertAddressable(String template, Object before, Object after) {
        TemplateRenderer renderer = TemplateRenderer.of(template);
        VNode prev = renderer.render("root", before);
        VNode next = renderer.render("root", after);

        List<Patch> patches = Differ.diff(prev, next);
        if (patches.isEmpty()) {
            fail("template produced no patches, so it proves nothing: " + template);
        }
        Document prevDom = parse(HtmlSerializer.serialize(prev));
        Document nextDom = parse(HtmlSerializer.serialize(next));

        for (Patch p : patches) {
            switch (p) {
                // the target must exist in the DOM the client is holding *after* the update
                case Patch.SetText t -> require(nextDom, t.id(), p, template);
                case Patch.SetAttr a -> require(nextDom, a.id(), p, template);
                case Patch.RemoveAttr a -> require(nextDom, a.id(), p, template);
                case Patch.SetEvent e -> require(nextDom, e.id(), p, template);
                case Patch.RemoveEvent e -> require(nextDom, e.id(), p, template);
                case Patch.Replace r -> require(nextDom, r.id(), p, template);
                // an insert addresses its parent; a remove addresses a node of the *previous* DOM
                case Patch.Insert i -> require(nextDom, i.parentId(), p, template);
                case Patch.Remove r -> require(prevDom, r.id(), p, template);
            }
        }
    }

    /** Fail unless an element carries this {@code data-medley-id} — the only lookup the client has. */
    private void require(Document dom, String id, Patch patch, String template) {
        assertNotNull(byMedleyId(dom, id),
                () -> "patch " + patch + " addresses '" + id + "', which no element carries in the "
                        + "rendered HTML — the client's querySelector would find nothing and drop it "
                        + "silently.\ntemplate: " + template);
    }

    private static Element byMedleyId(Document dom, String id) {
        NodeList all = dom.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Node n = all.item(i);
            if (n instanceof Element el && id.equals(el.getAttribute("data-medley-id"))) {
                return el;
            }
        }
        return null;
    }

    /** The serializer's output is well-formed XML (quoted attrs, closed tags, escaped {@code &<>}). */
    private static Document parse(String html) {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError("serialized HTML is not parseable: " + html, e);
        }
    }

    // --- fixtures ---

    public static class Ctx {
        public int count;
        public String label = "n";
        public List<Item> items = List.of();

        Ctx(int count) { this.count = count; }

        static Ctx items(String... specs) {
            Ctx c = new Ctx(0);
            c.items = java.util.Arrays.stream(specs)
                    .map(s -> new Item(s.split(":")[0], s.split(":")[1]))
                    .toList();
            return c;
        }

        public int getCount() { return count; }
        public String getLabel() { return label; }
        public List<Item> getItems() { return items; }
    }

    public record Item(String id, String text) {
        public String getId() { return id; }
        public String getText() { return text; }
    }
}
