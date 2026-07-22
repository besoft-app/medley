package app.besoft.medley.core.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import app.besoft.medley.core.diff.HtmlSerializer;
import app.besoft.medley.core.vnode.VNode;
import org.junit.jupiter.api.Test;

/**
 * Default slot / children projection — Stage 6, increment 6.2. The body of a
 * {@code <medley-component>} renders in the PARENT's scope with parent ids, and the child splices it
 * at its {@code <medley-slot>}. The slot carries the parent's component id as {@code data-medley-cid}
 * so the client's walk-up routes projected events to the parent, not to the child.
 */
class DefaultSlotRenderTest {

    @Annotations.MedleyComponent("panel")
    static class Panel {
        @Annotations.Param String title = "";
        public String getTitle() { return title; }
    }

    static class Owner {
        public int clicks = 7;
    }

    /** Fake host: mounts a child and hands it the projection, mirroring the starter coordinator. */
    static class FakeHost implements ComponentHost {
        private final Map<String, Supplier<Object>> comps = new HashMap<>();
        private final Map<String, String> templates = new HashMap<>();
        private final Map<String, Mounted> mounted = new HashMap<>();

        private record Mounted(Object component, TemplateRenderer renderer, VNode tree) {}

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
            TemplateRenderer r = TemplateRenderer.of(templates.get(name));
            VNode tree = r.render(childId, c, depth, this, projection);
            mounted.put(childId, new Mounted(c, r, tree));
            return tree;
        }
    }

    private static final String PANEL_TPL =
            "<section><h3>{{ title }}</h3><medley-slot></medley-slot></section>";

    private static VNode render(String owner, ComponentHost host, Object ctx) {
        return TemplateRenderer.of(owner).render("root", ctx, host);
    }

    @Test
    void bodyIsProjectedIntoTheSlotWithParentIds() {
        FakeHost h = new FakeHost().add("panel", Panel::new, PANEL_TPL);
        VNode.VElement div = (VNode.VElement) render(
                "<div><medley-component name=\"panel\" title=\"T\">"
              + "<p>clicks: {{ clicks }}</p>"
              + "</medley-component></div>", h, new Owner());

        VNode.VElement boundary = (VNode.VElement) div.children().get(0);
        VNode.VElement childRoot = (VNode.VElement) boundary.children().get(0);
        VNode.VElement slot = (VNode.VElement) childRoot.children().get(1);

        assertEquals("medley-slot", slot.tag());
        assertEquals("root.0::panel.1", slot.id(), "the slot itself lives in the CHILD's id-space");

        VNode.VElement projected = (VNode.VElement) slot.children().get(0);
        assertEquals("p", projected.tag());
        assertEquals("root.0.0", projected.id(), "projected content keeps PARENT ids");
        assertTrue(text(projected).contains("clicks: 7"), "body is evaluated against the parent");
    }

    @Test
    void slotCarriesTheOwnerComponentIdSoEventsRouteToTheParent() {
        FakeHost h = new FakeHost().add("panel", Panel::new, PANEL_TPL);
        VNode.VElement div = (VNode.VElement) render(
                "<div><medley-component name=\"panel\" title=\"T\">"
              + "<button @click=\"bump\">+1</button>"
              + "</medley-component></div>", h, new Owner());

        VNode.VElement boundary = (VNode.VElement) div.children().get(0);
        VNode.VElement childRoot = (VNode.VElement) boundary.children().get(0);
        VNode.VElement slot = (VNode.VElement) childRoot.children().get(1);

        assertEquals("root", slot.attrs().get("data-medley-cid"),
                "without this the client would dispatch the projected @click to the child");
        VNode.VElement button = (VNode.VElement) slot.children().get(0);
        assertEquals("bump", button.events().get("click"));
    }

    @Test
    void aBoundaryWithNoBodyLeavesTheSlotEmpty() {
        FakeHost h = new FakeHost().add("panel", Panel::new, PANEL_TPL);
        VNode.VElement div = (VNode.VElement) render(
                "<div><medley-component name=\"panel\" title=\"T\"></medley-component></div>",
                h, new Owner());

        VNode.VElement boundary = (VNode.VElement) div.children().get(0);
        VNode.VElement childRoot = (VNode.VElement) boundary.children().get(0);
        VNode.VElement slot = (VNode.VElement) childRoot.children().get(1);

        assertEquals(List.of(), slot.children());
        assertNull(slot.attrs().get("data-medley-cid"),
                "no projection -> no cid, so the slot adds no attribute noise to the diff");
    }

    @Test
    void ifAndKeyedForInsideProjectedContentStillBehave() {
        class OwnerWithList {
            public boolean show = false;
            public List<String> items = List.of("a", "b");
        }
        FakeHost h = new FakeHost().add("panel", Panel::new, PANEL_TPL);
        VNode.VElement div = (VNode.VElement) render(
                "<div><medley-component name=\"panel\" title=\"T\">"
              + "<span *if=\"show\">hidden</span>"
              + "<ul><li *for=\"i : items\" key=\"i\">{{ i }}</li></ul>"
              + "</medley-component></div>", h, new OwnerWithList());

        VNode.VElement boundary = (VNode.VElement) div.children().get(0);
        VNode.VElement childRoot = (VNode.VElement) boundary.children().get(0);
        VNode.VElement slot = (VNode.VElement) childRoot.children().get(1);

        VNode.VElement placeholder = (VNode.VElement) slot.children().get(0);
        assertEquals("medley-placeholder", placeholder.tag(), "*if keeps its stable slot");
        assertEquals("root.0.0", placeholder.id());

        VNode.VElement ul = (VNode.VElement) slot.children().get(1);
        VNode.VElement firstLi = (VNode.VElement) ul.children().get(0);
        // ul is the projection's second node (root.0.1); its keyed items are its own children.
        assertEquals("root.0.1.0[a]", firstLi.id(), "keyed *for ids stay in the parent's space");
        assertEquals("a", firstLi.key());
    }

    @Test
    void bodyContentForASlotlessChildIsRejected() {
        // Catching the author's typo beats silently swallowing the content.
        TemplateRenderer slotless = TemplateRenderer.of("<section><h3>{{ title }}</h3></section>");
        assertFalse(slotless.mayDeclareSlot());

        ComponentInstance child = new ComponentInstance("root.0::panel", new PanelComponent(), slotless);
        Projection projection = new Projection("root",
                List.of(new VNode.VText("root.0.0", "hi", true)));

        TemplateException e = assertThrows(TemplateException.class,
                () -> child.setProjection(projection));
        assertTrue(e.getMessage().contains("medley-slot"), e.getMessage());
    }

    @Test
    void aTemplateUsingPartialsIsNotRejected() {
        // A slot may live inside a partial fragment, which a static scan of this template cannot see,
        // so the guard must stay silent rather than raise a false alarm.
        TemplateRenderer viaPartial = TemplateRenderer.of(
                "<section><medley-partial name=\"body\"></medley-partial></section>");
        assertTrue(viaPartial.mayDeclareSlot());
    }

    @Test
    void aSlotHiddenByAFalseIfIsStillDeclared() {
        // The guard is a STATIC scan on purpose: a runtime check would fire spuriously here.
        TemplateRenderer conditional = TemplateRenderer.of(
                "<section><medley-slot *if=\"open\"></medley-slot></section>");
        assertTrue(conditional.mayDeclareSlot());
    }

    @Test
    void twoSlotsInOneRenderAreRejected() {
        // Splicing the same projection twice would emit duplicate data-medley-ids and break the
        // addressability invariant (MEDLEY_DESIGN §11a).
        FakeHost h = new FakeHost().add("panel", Panel::new,
                "<section><medley-slot></medley-slot><medley-slot></medley-slot></section>");
        assertThrows(TemplateException.class, () -> render(
                "<div><medley-component name=\"panel\" title=\"T\"><p>x</p></medley-component></div>",
                h, new Owner()));
    }

    @Test
    void everyProjectedIdIsCarriedByAnElementInTheSerializedHtml() {
        // §11a: whatever the differ can address must exist as an element in the served HTML.
        FakeHost h = new FakeHost().add("panel", Panel::new, PANEL_TPL);
        VNode tree = render(
                "<div><medley-component name=\"panel\" title=\"T\">"
              + "<p>clicks: {{ clicks }}</p>"
              + "</medley-component></div>", h, new Owner());

        String html = HtmlSerializer.serialize(tree);
        assertTrue(html.contains("<medley-slot data-medley-id=\"root.0::panel.1\""),
                "the slot is a real element: " + html);
        assertTrue(html.contains("data-medley-cid=\"root\""), "cid reaches the DOM: " + html);
        assertTrue(html.contains("<medley-text data-medley-id=\"root.0.0.1\""),
                "the projected interpolation has its own addressable host: " + html);
    }

    /** A minimal {@link Component} for the instance-level guard test (setProjection needs a real one). */
    @Annotations.MedleyComponent("panel")
    static class PanelComponent extends Component {
        @Annotations.Param String title = "";
        public String getTitle() { return title; }
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
