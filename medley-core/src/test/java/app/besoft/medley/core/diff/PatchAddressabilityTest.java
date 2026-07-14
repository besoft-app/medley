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
        // key="…" is the real keyed syntax (`:key` would be parsed as an ordinary bound attribute and
        // silently fall back to index keys — i.e. this test would not be testing keyed reconciliation).
        String tpl = "<ul><li *for=\"item : items\" key=\"item.id\">{{ item.text }}</li></ul>";
        assertAddressable(tpl, Ctx.items("a:one", "b:two"), Ctx.items("a:one", "b:two", "c:three"));
        assertAddressable(tpl, Ctx.items("a:one", "b:two"), Ctx.items("a:one"));
        assertAddressable(tpl, Ctx.items("a:one", "b:two"), Ctx.items("a:ONE", "b:two"));
    }

    /**
     * A {@code *for} whose container also holds <em>dynamic</em> siblings is fine: growing the list shifts
     * the following slots, and the positional diff repairs them with a {@code Replace} + {@code Insert}
     * — which now resolve, because an interpolation's marker is a real element. Before the marker existed
     * the {@code Replace} landed on a bare text node and vanished.
     */
    @Test
    void forSharingAContainerWithADynamicSibling() {
        assertAddressable("<div><span *for=\"item : items\" key=\"item.id\">x</span>{{ label }}</div>",
                Ctx.items("a:one"), Ctx.items("a:one", "b:two"));
    }

    /**
     * <b>Known limitation, deliberately pinned</b> (MEDLEY_DESIGN §4.3): a {@code *for} whose container
     * also holds <em>static</em> text — including the whitespace of a prettily-indented template. Growing
     * the list shifts the following slots, and the positional diff then tries to {@code Replace} a static
     * text node, which carries no id and cannot be addressed. This is why list containers must be authored
     * whitespace-tight with all children keyed. The invariant above does not claim to cover this shape.
     */
    @Test
    void knownLimitation_forSharingAContainerWithStaticText() {
        String tpl = "<div>\n  <span *for=\"item : items\" key=\"item.id\">x</span>\n  total: {{ count }}\n</div>";
        try {
            assertAddressable(tpl, Ctx.items("a:one"), Ctx.items("a:one", "b:two"));
        } catch (AssertionError expected) {
            return; // the documented limitation still holds
        }
        fail("The positional-diff misalignment around static text (MEDLEY_DESIGN §4.3) appears to be "
                + "FIXED. Delete this test, move the template into the battery above, and update §4.3/§11a.");
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
        assertAddressable("<textarea>{{ label }}</textarea>", new Ctx(0), new Ctx(0, "draft"));
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
                // an in-place update addresses a node that exists in both DOMs (its id is stable)
                case Patch.SetText t -> require(nextDom, t.id(), p, template);
                case Patch.SetAttr a -> require(nextDom, a.id(), p, template);
                case Patch.RemoveAttr a -> require(nextDom, a.id(), p, template);
                case Patch.SetEvent e -> require(nextDom, e.id(), p, template);
                case Patch.RemoveEvent e -> require(nextDom, e.id(), p, template);
                // structural ops are resolved by the client against the DOM it is *currently holding*,
                // i.e. the previous one — and their payload must be an element (see requirePayload)
                case Patch.Replace r -> {
                    require(prevDom, r.id(), p, template);
                    requirePayload(r.html(), p, template);
                }
                case Patch.Insert i -> {
                    require(nextDom, i.parentId(), p, template);
                    requirePayload(i.html(), p, template);
                }
                case Patch.Remove r -> require(prevDom, r.id(), p, template);
            }
        }
    }

    /**
     * A structural payload must be an <b>element</b>: the client does
     * {@code htmlToElement(html).firstElementChild}, so a payload that serialized to bare text yields
     * {@code null} and the patch is dropped on the floor.
     */
    private void requirePayload(String html, Patch patch, String template) {
        Document payload = parse("<fragment>" + html + "</fragment>");
        NodeList children = payload.getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                return;
            }
        }
        fail("patch " + patch + " carries a payload with no element root, so the client's "
                + "htmlToElement(...).firstElementChild is null and the patch is dropped.\npayload: "
                + html + "\ntemplate: " + template);
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

        Ctx(int count, String label) {
            this.count = count;
            this.label = label;
        }

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
