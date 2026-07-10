package app.besoft.medley.core.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import app.besoft.medley.core.vnode.VNode;
import org.junit.jupiter.api.Test;

/**
 * Template partials (Stage 4, increment 4): {@code <medley-partial name="x" .../>} expands a named
 * fragment inline into the owner's tree and id-space, evaluated against a local scope built from the
 * passed attributes. No child component, no separate state, no wire change.
 */
class PartialRenderTest {

    /** Build a resolver from a map of partial-name -> template source. */
    private static PartialResolver resolver(Map<String, String> partials) {
        Map<String, TemplateNode.Element> parsed = new HashMap<>();
        partials.forEach((k, v) -> parsed.put(k, TemplateParser.parse(v)));
        return parsed::get; // returns null for unknown names
    }

    /** Owner context exposing a couple of fields the fragments can read via scope-leakage. */
    static class Owner {
        public String name = "Ada";
        public int count = 2;
        public java.util.List<Integer> nums = java.util.List.of(1, 2);
        public Map<String, String> errors = new HashMap<>();
    }

    private static VNode render(String owner, Map<String, String> partials, Object ctx) {
        return TemplateRenderer.of(owner, resolver(partials)).render("root", ctx);
    }

    @Test
    void expandsInlineWithParamScope() {
        VNode tree = render(
                "<div><medley-partial name=\"greeting\" who=\"World\"></medley-partial></div>",
                Map.of("greeting", "<p>Hello {{ who }}!</p>"),
                new Owner());

        VNode.VElement div = assertInstanceOf(VNode.VElement.class, tree);
        assertEquals("div", div.tag());
        // The <medley-partial> is replaced by the fragment root at the partial's slot id (root.0).
        VNode.VElement p = assertInstanceOf(VNode.VElement.class, div.children().get(0));
        assertEquals("p", p.tag());
        assertEquals("root.0", p.id());
        assertTrue(text(p).contains("Hello World!"), "param 'who' resolves in the fragment");
    }

    @Test
    void samePartialUsedTwiceGetsDistinctIds() {
        VNode.VElement div = (VNode.VElement) render(
                "<div><medley-partial name=\"g\" who=\"A\"></medley-partial>"
              + "<medley-partial name=\"g\" who=\"B\"></medley-partial></div>",
                Map.of("g", "<p>{{ who }}</p>"),
                new Owner());

        VNode.VElement first = (VNode.VElement) div.children().get(0);
        VNode.VElement second = (VNode.VElement) div.children().get(1);
        assertEquals("root.0", first.id());
        assertEquals("root.1", second.id());
        assertTrue(text(first).contains("A"));
        assertTrue(text(second).contains("B"));
    }

    @Test
    void boundParamEvaluatesAgainstOwner() {
        VNode.VElement div = (VNode.VElement) render(
                "<div><medley-partial name=\"g\" :who=\"name\"></medley-partial></div>",
                Map.of("g", "<p>Hi {{ who }}</p>"),
                new Owner());
        assertTrue(text((VNode.VElement) div.children().get(0)).contains("Hi Ada"));
    }

    @Test
    void fragmentReadsOwnerStateViaScopeLeakage() {
        VNode.VElement div = (VNode.VElement) render(
                "<div><medley-partial name=\"badge\"></medley-partial></div>",
                Map.of("badge", "<span>{{ count }}</span>"),
                new Owner());
        assertTrue(text((VNode.VElement) div.children().get(0)).contains("2"));
    }

    @Test
    void eventInsidePartialRoutesToOwnerActionNamedByParam() {
        VNode.VElement div = (VNode.VElement) render(
                "<div><medley-partial name=\"btn\" onClick=\"increment\"></medley-partial></div>",
                Map.of("btn", "<button @click=\"onClick\">+</button>"),
                new Owner());
        VNode.VElement button = (VNode.VElement) div.children().get(0);
        assertEquals("increment", button.events().get("click"),
                "the fragment's @click=onClick is rewritten to the owner action passed as a param");
    }

    @Test
    void eventParamSubstitutionPreservesCallSyntaxArgs() {
        VNode.VElement div = (VNode.VElement) render(
                "<div><medley-partial name=\"fld\" onChange=\"setName\"></medley-partial></div>",
                Map.of("fld", "<input @input=\"onChange($value)\">"),
                new Owner());
        VNode.VElement input = (VNode.VElement) div.children().get(0);
        assertEquals("setName($value)", input.events().get("input"),
                "param substitution keeps the Inc-2 $value token intact");
    }

    @Test
    void partialAsKeyedForItemRetainsKeyAndId() {
        // Critical: a <medley-partial> used as a keyed *for item must carry the key on its fragment
        // root, or keyed reconciliation silently breaks (minimal-diff regression).
        VNode.VElement div = (VNode.VElement) render(
                "<div><medley-partial name=\"row\" *for=\"n : nums\" key=\"n\" :label=\"n\">"
              + "</medley-partial></div>",
                Map.of("row", "<p>{{ label }}</p>"),
                new Owner());

        VNode.VElement p0 = (VNode.VElement) div.children().get(0);
        VNode.VElement p1 = (VNode.VElement) div.children().get(1);
        assertEquals("1", p0.key(), "fragment root keeps the keyed *for key");
        assertEquals("2", p1.key());
        // The partial sits at div's child slot (root.0), so keyed items take root.0[<key>] ids.
        assertEquals("root.0[1]", p0.id(), "fragment root takes the keyed slot id");
        assertEquals("root.0[2]", p1.id());
        assertTrue(text(p0).contains("1"), ":label bound to the loop var reaches the fragment");
    }

    @Test
    void eventSubstitutionAppliesInsideForWithinPartial() {
        // A *for inside a partial fragment builds a (non-partial) loop scope; param-action
        // substitution must still resolve up the scope chain, not silently drop.
        VNode.VElement div = (VNode.VElement) render(
                "<div><medley-partial name=\"list\" onPick=\"choose\"></medley-partial></div>",
                Map.of("list", "<ul><li *for=\"n : nums\" key=\"n\">"
                             + "<button @click=\"onPick\">{{ n }}</button></li></ul>"),
                new Owner());

        VNode.VElement ul = (VNode.VElement) div.children().get(0);
        VNode.VElement li0 = (VNode.VElement) ul.children().get(0);
        VNode.VElement button = (VNode.VElement) li0.children().get(0);
        assertEquals("choose", button.events().get("click"),
                "param action substitution must reach events inside a *for within the partial");
    }

    @Test
    void blankPartialNameThrows() {
        assertThrows(TemplateException.class, () -> render(
                "<div><medley-partial name=\"\"></medley-partial></div>",
                Map.of("x", "<p>x</p>"), new Owner()));
    }

    @Test
    void fragmentWithNonSingleRootThrows() {
        // A fragment whose root is itself a *for expands to many nodes — not a single root.
        assertThrows(TemplateException.class, () -> render(
                "<div><medley-partial name=\"multi\"></medley-partial></div>",
                Map.of("multi", "<p *for=\"n : nums\" key=\"n\">{{ n }}</p>"),
                new Owner()));
    }

    @Test
    void unknownPartialThrows() {
        assertThrows(TemplateException.class, () -> render(
                "<div><medley-partial name=\"missing\"></medley-partial></div>",
                Map.of(), new Owner()));
    }

    @Test
    void partialWithoutResolverThrows() {
        TemplateRenderer r = TemplateRenderer.of(
                "<div><medley-partial name=\"x\"></medley-partial></div>");
        assertThrows(TemplateException.class, () -> r.render("root", new Owner()));
    }

    @Test
    void recursivePartialIsGuarded() {
        assertThrows(TemplateException.class, () -> render(
                "<div><medley-partial name=\"loop\"></medley-partial></div>",
                Map.of("loop", "<div><medley-partial name=\"loop\"></medley-partial></div>"),
                new Owner()));
    }

    private static String text(VNode.VElement el) {
        StringBuilder sb = new StringBuilder();
        for (VNode c : el.children()) {
            if (c instanceof VNode.VText t) sb.append(t.value());
            else if (c instanceof VNode.VElement e) sb.append(text(e));
        }
        return sb.toString();
    }
}
