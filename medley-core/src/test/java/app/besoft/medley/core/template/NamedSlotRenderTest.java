package app.besoft.medley.core.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import app.besoft.medley.core.component.Annotations;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.core.component.ComponentInstance;
import app.besoft.medley.core.component.ParamBinder;
import app.besoft.medley.core.vnode.VNode;
import org.junit.jupiter.api.Test;

/**
 * Named / multiple slots + fallback — Stage 6, increment 6.3. A parent marks a top-level body node with
 * {@code slot="x"} to target {@code <medley-slot name="x">}; unnamed content fills the default slot; a
 * slot the parent does not fill renders its own children as fallback, in the CHILD's scope.
 */
class NamedSlotRenderTest {

    @Annotations.MedleyComponent("card")
    static class Card {
        @Annotations.Param String heading = "";
        public String getHeading() { return heading; }
    }

    /** A real {@link Component} for the instance-level fail-fast tests (setProjection needs one). */
    @Annotations.MedleyComponent("card")
    static class CardComp extends Component {}

    static class Owner {
        public String title = "Hello";
        public int n = 3;
    }

    /** Fake host mirroring the starter coordinator: mounts a child and hands it the projection. */
    static class FakeHost implements ComponentHost {
        private final Map<String, Supplier<Object>> comps = new HashMap<>();
        private final Map<String, String> templates = new HashMap<>();

        FakeHost add(String name, Supplier<Object> comp, String template) {
            comps.put(name, comp);
            templates.put(name, template);
            return this;
        }

        @Override
        public VNode mountChild(String childId, String name, Map<String, Object> params,
                                Map<String, String> outputs, Projection projection, int depth) {
            if (!comps.containsKey(name)) return null;
            Object c = comps.get(name).get();
            ParamBinder.inject(c, params);
            return TemplateRenderer.of(templates.get(name)).render(childId, c, depth, this, projection);
        }
    }

    // <header> holds the named "header" slot with fallback "Untitled"; then the default slot.
    private static final String CARD_TPL =
            "<article><header><medley-slot name=\"header\">Untitled</medley-slot></header>"
          + "<medley-slot></medley-slot></article>";

    private static VNode render(String owner, ComponentHost host, Object ctx) {
        return TemplateRenderer.of(owner).render("root", ctx, host);
    }

    private static VNode.VElement childOf(VNode.VElement div) {
        VNode.VElement boundary = (VNode.VElement) div.children().get(0);
        return (VNode.VElement) boundary.children().get(0); // the child root (<article>)
    }

    @Test
    void namedAndDefaultContentRouteToTheirSlotsWithParentIds() {
        FakeHost h = new FakeHost().add("card", Card::new, CARD_TPL);
        VNode.VElement div = (VNode.VElement) render(
                "<div><medley-component name=\"card\">"
              + "<h3 slot=\"header\">{{ title }}</h3>"
              + "<p>body {{ n }}</p>"
              + "</medley-component></div>", h, new Owner());

        VNode.VElement article = childOf(div);
        VNode.VElement headerSlot = (VNode.VElement) ((VNode.VElement) article.children().get(0)).children().get(0);
        VNode.VElement defaultSlot = (VNode.VElement) article.children().get(1);

        assertEquals("medley-slot", headerSlot.tag());
        VNode.VElement h3 = (VNode.VElement) headerSlot.children().get(0);
        assertEquals("h3", h3.tag());
        assertEquals("root.0.0", h3.id(), "projected node keeps its absolute-source-position parent id");
        assertTrue(text(h3).contains("Hello"));

        VNode.VElement p = (VNode.VElement) defaultSlot.children().get(0);
        assertEquals("p", p.tag());
        assertEquals("root.0.1", p.id(), "the default-slot node keeps its parent id too");
        assertTrue(text(p).contains("body 3"));

        assertEquals("root", headerSlot.attrs().get("data-medley-cid"), "named slot routes to the parent");
        assertEquals("root", defaultSlot.attrs().get("data-medley-cid"));
    }

    @Test
    void theSlotAttributeIsStrippedFromTheProjectedNode() {
        FakeHost h = new FakeHost().add("card", Card::new, CARD_TPL);
        VNode.VElement div = (VNode.VElement) render(
                "<div><medley-component name=\"card\">"
              + "<h3 slot=\"header\">x</h3><p>y</p></medley-component></div>", h, new Owner());
        VNode.VElement headerSlot = (VNode.VElement) ((VNode.VElement) childOf(div).children().get(0)).children().get(0);
        VNode.VElement h3 = (VNode.VElement) headerSlot.children().get(0);
        assertNull(h3.attrs().get("slot"), "slot= is a routing directive, not a DOM attribute");
    }

    @Test
    void multipleNodesWithTheSameSlotNameFillItInOrder() {
        FakeHost h = new FakeHost().add("card", Card::new, CARD_TPL);
        VNode.VElement div = (VNode.VElement) render(
                "<div><medley-component name=\"card\">"
              + "<h3 slot=\"header\">a</h3><em slot=\"header\">b</em>"
              + "<p>body</p></medley-component></div>", h, new Owner());
        VNode.VElement headerSlot = (VNode.VElement) ((VNode.VElement) childOf(div).children().get(0)).children().get(0);
        assertEquals(2, headerSlot.children().size());
        assertEquals("root.0.0", ((VNode.VElement) headerSlot.children().get(0)).id());
        assertEquals("root.0.1", ((VNode.VElement) headerSlot.children().get(1)).id());
    }

    @Test
    void anUnfilledNamedSlotRendersItsFallbackInTheChildScope() {
        FakeHost h = new FakeHost().add("card", Card::new, CARD_TPL);
        // parent fills only the default slot; the header slot falls back
        VNode.VElement div = (VNode.VElement) render(
                "<div><medley-component name=\"card\"><p>body</p></medley-component></div>", h, new Owner());
        VNode.VElement headerSlot = (VNode.VElement) ((VNode.VElement) childOf(div).children().get(0)).children().get(0);

        assertNull(headerSlot.attrs().get("data-medley-cid"), "fallback is child-owned — no parent cid");
        VNode.VText fallback = (VNode.VText) headerSlot.children().get(0);
        assertEquals("root.0::card.0.0.0", fallback.id(), "fallback text lives at a CHILD id under the slot");
        assertEquals("Untitled", fallback.value());
    }

    @Test
    void fallbackReadsChildStateAndBindsChildActions() {
        @Annotations.MedleyComponent("box") class Box {
            @Annotations.State public int count = 9;
            public int getCount() { return count; }
        }
        FakeHost h = new FakeHost().add("box", Box::new,
                "<div><medley-slot name=\"body\"><button @click=\"tick\">{{ count }}</button></medley-slot></div>");
        VNode.VElement div = (VNode.VElement) render(
                "<div><medley-component name=\"box\"></medley-component></div>", h, new Owner());
        VNode.VElement slot = (VNode.VElement) childOf(div).children().get(0);
        VNode.VElement button = (VNode.VElement) slot.children().get(0);
        assertEquals("tick", button.events().get("click"), "fallback @event is a CHILD action");
        assertTrue(text(button).contains("9"), "fallback {{ }} reads CHILD state");
    }

    @Test
    void contentTargetingAnUndeclaredSlotIsRejected() {
        // The fail-fast is a static check against the child template's declared names, enforced at mount
        // (ComponentInstance.setProjection) — the same layer as the 6.2 slotless-child guard.
        ComponentInstance child = new ComponentInstance(
                "root.0::card", new CardComp(), TemplateRenderer.of(CARD_TPL)); // declares header + default
        Projection footer = new Projection("root",
                Map.of("footer", List.of(new VNode.VText("root.0.0", "nope", true))));
        TemplateException e = assertThrows(TemplateException.class, () -> child.setProjection(footer));
        assertTrue(e.getMessage().contains("footer"), e.getMessage());
    }

    @Test
    void unnamedContentWithNoDefaultSlotIsRejected() {
        ComponentInstance child = new ComponentInstance("root.0::x", new CardComp(),
                TemplateRenderer.of("<div><medley-slot name=\"header\"></medley-slot></div>")); // no default
        Projection unnamed = new Projection("root",
                Map.of(Projection.DEFAULT, List.of(new VNode.VText("root.0.0", "orphan", true))));
        assertThrows(TemplateException.class, () -> child.setProjection(unnamed));
    }

    @Test
    void twoSlotsWithTheSameNameInOneRenderAreRejected() {
        FakeHost h = new FakeHost().add("dup", Card::new,
                "<div><medley-slot name=\"a\"></medley-slot><medley-slot name=\"a\"></medley-slot></div>");
        assertThrows(TemplateException.class, () -> render(
                "<div><medley-component name=\"dup\"><h3 slot=\"a\">x</h3></medley-component></div>",
                h, new Owner()));
    }

    @Test
    void declaredSlotNamesScanCollectsNamesAndDefault() {
        TemplateRenderer r = TemplateRenderer.of(CARD_TPL);
        assertTrue(r.declaredSlotNames().contains("header"));
        assertTrue(r.declaredSlotNames().contains(""), "the default slot is the empty-string name");

        TemplateRenderer viaPartial = TemplateRenderer.of(
                "<div><medley-partial name=\"x\"></medley-partial></div>");
        assertNull(viaPartial.declaredSlotNames(), "a partial makes the declared set unknown (permissive)");
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
