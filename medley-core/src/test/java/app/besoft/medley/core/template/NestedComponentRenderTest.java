package app.besoft.medley.core.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import app.besoft.medley.core.component.Annotations;
import app.besoft.medley.core.component.ParamBinder;
import app.besoft.medley.core.vnode.VNode;
import org.junit.jupiter.api.Test;

/**
 * Nested server components — 4b.2 (persistent child + opaque boundary + {@code @Param} down). A
 * {@code <medley-component name="x" ...>} resolves its child through a {@link ComponentHost}, which
 * mounts a fresh instance on first encounter and reuses it on later parent re-renders; the child's
 * subtree is embedded under a diff-opaque host at the child instance id {@code hostId::name}.
 */
class NestedComponentRenderTest {

    /** A display-only child with a single @Param. */
    @Annotations.MedleyComponent("counter")
    static class Counter {
        @Annotations.Param int start;
    }

    static class Owner {
        public int n = 5;
    }

    /**
     * Fake host mirroring the starter coordinator: creates + injects a child on first mount, persists
     * it per child instance id (so a re-render reuses it), and threads {@code depth}/{@code this} so
     * nested components and the recursion guard work. Records how many times each id was created.
     */
    static class FakeHost implements ComponentHost {
        private final Map<String, Supplier<Object>> comps = new HashMap<>();
        private final Map<String, String> templates = new HashMap<>();
        private final Map<String, Mounted> mounted = new HashMap<>();
        final Map<String, Integer> createCounts = new HashMap<>();

        private record Mounted(Object component, TemplateRenderer renderer, VNode tree) {}

        FakeHost add(String name, Supplier<Object> comp, String template) {
            comps.put(name, comp);
            templates.put(name, template);
            return this;
        }

        @Override
        public VNode mountChild(String childId, String name, Map<String, Object> params, int depth) {
            if (!comps.containsKey(name)) return null;
            Mounted existing = mounted.get(childId);
            if (existing != null) return existing.tree(); // reuse — @State survives a parent re-render
            createCounts.merge(childId, 1, Integer::sum);
            Object c = comps.get(name).get();
            ParamBinder.inject(c, params);
            TemplateRenderer r = TemplateRenderer.of(templates.get(name));
            VNode tree = r.render(childId, c, depth, this);
            mounted.put(childId, new Mounted(c, r, tree));
            return tree;
        }
    }

    private static VNode render(String owner, ComponentHost host, Object ctx) {
        return TemplateRenderer.of(owner).render("root", ctx, host);
    }

    private static final String COUNTER_TPL = "<div>count: {{ start }}</div>";

    @Test
    void childRendersWithBoundParamDown() {
        FakeHost h = new FakeHost().add("counter", Counter::new, COUNTER_TPL);
        VNode.VElement div = (VNode.VElement) render(
                "<div><medley-component name=\"counter\" :start=\"n\"></medley-component></div>",
                h, new Owner());

        VNode.VElement host = (VNode.VElement) div.children().get(0);
        assertEquals("medley-component", host.tag());
        assertEquals("root.0", host.id(), "host takes the parent slot id");
        assertTrue(host.opaque(), "the boundary host is diff-opaque");
        assertEquals("root.0::counter", host.attrs().get("data-medley-cid"),
                "host carries the child instance id marker");

        VNode.VElement childRoot = (VNode.VElement) host.children().get(0);
        assertEquals("root.0::counter", childRoot.id(), "child tree is rooted at hostId::name");
        assertTrue(text(childRoot).contains("count: 5"), "bound :start=n resolved against the owner");
    }

    @Test
    void twoChildrenGetDistinctIds() {
        FakeHost h = new FakeHost().add("counter", Counter::new, COUNTER_TPL);
        VNode.VElement div = (VNode.VElement) render(
                "<div><medley-component name=\"counter\" :start=\"n\"></medley-component>"
              + "<medley-component name=\"counter\" start=\"9\"></medley-component></div>",
                h, new Owner());

        VNode.VElement first = (VNode.VElement) div.children().get(0);
        VNode.VElement second = (VNode.VElement) div.children().get(1);
        assertEquals("root.0::counter", first.attrs().get("data-medley-cid"));
        assertEquals("root.1::counter", second.attrs().get("data-medley-cid"));
        assertTrue(text((VNode.VElement) first.children().get(0)).contains("count: 5"));
        assertTrue(text((VNode.VElement) second.children().get(0)).contains("count: 9"),
                "static start=\"9\" coerces to the int @Param");
    }

    @Test
    void childInstancePersistsAcrossParentReRender() {
        // 4b.2: a parent re-render reuses the same child instance (its @State survives) — the host is
        // asked for the child again but must not create a second one.
        FakeHost h = new FakeHost().add("counter", Counter::new, COUNTER_TPL);
        String owner = "<div><medley-component name=\"counter\" :start=\"n\"></medley-component></div>";
        Owner ctx = new Owner();
        render(owner, h, ctx);
        render(owner, h, ctx); // parent re-render
        assertEquals(1, h.createCounts.get("root.0::counter"),
                "the child must be created once and reused on re-render");
    }

    @Test
    void unknownComponentThrows() {
        assertThrows(TemplateException.class, () -> render(
                "<div><medley-component name=\"missing\"></medley-component></div>",
                new FakeHost(), new Owner()));
    }

    @Test
    void componentWithoutHostThrows() {
        TemplateRenderer r = TemplateRenderer.of(
                "<div><medley-component name=\"counter\"></medley-component></div>");
        assertThrows(TemplateException.class, () -> r.render("root", new Owner()));
    }

    @Test
    void blankNameThrows() {
        FakeHost h = new FakeHost().add("counter", Counter::new, COUNTER_TPL);
        assertThrows(TemplateException.class, () -> render(
                "<div><medley-component name=\"\"></medley-component></div>", h, new Owner()));
    }

    @Test
    void nullBoundParamIntoPrimitiveIsHandled() {
        // A bound :start that evaluates to null against a primitive int @Param must surface as a
        // handled TemplateException, not a raw IllegalArgumentException from Field.set.
        class OwnerWithNull {
            public Integer missing = null;
        }
        FakeHost h = new FakeHost().add("counter", Counter::new, COUNTER_TPL);
        assertThrows(TemplateException.class, () -> render(
                "<div><medley-component name=\"counter\" :start=\"missing\"></medley-component></div>",
                h, new OwnerWithNull()));
    }

    @Test
    void recursiveComponentIsGuarded() {
        FakeHost h = new FakeHost().add("loop", Object::new,
                "<div><medley-component name=\"loop\"></medley-component></div>");
        assertThrows(TemplateException.class, () -> render(
                "<div><medley-component name=\"loop\"></medley-component></div>", h, new Owner()));
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
