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
 * Nested server components — 4b.1 (static child render + {@code @Param} down). A
 * {@code <medley-component name="x" ...>} resolves a fresh child, injects its {@code @Param}s from
 * the passed attributes (owner-evaluated for {@code :attr}), and renders the child's template at the
 * child instance id {@code hostId::name}. Action routing / independent diffing is 4b.2.
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

    /** Fake factory mirroring PartialRenderTest's fake resolver — real ParamBinder injection, and
     *  child renderers wired with the same factory so nested components (and recursion) work. */
    static class FakeChildren implements ChildComponentFactory {
        private final Map<String, Supplier<Object>> comps = new HashMap<>();
        private final Map<String, String> templates = new HashMap<>();

        FakeChildren add(String name, Supplier<Object> comp, String template) {
            comps.put(name, comp);
            templates.put(name, template);
            return this;
        }

        @Override
        public Child create(String name, Map<String, Object> params) {
            if (!comps.containsKey(name)) return null;
            Object c = comps.get(name).get();
            ParamBinder.inject(c, params);
            return new Child(c, TemplateRenderer.of(templates.get(name), null, this));
        }
    }

    private static VNode render(String owner, ChildComponentFactory factory, Object ctx) {
        return TemplateRenderer.of(owner, null, factory).render("root", ctx);
    }

    private static final String COUNTER_TPL = "<div>count: {{ start }}</div>";

    @Test
    void childRendersWithBoundParamDown() {
        FakeChildren f = new FakeChildren().add("counter", Counter::new, COUNTER_TPL);
        VNode.VElement div = (VNode.VElement) render(
                "<div><medley-component name=\"counter\" :start=\"n\"></medley-component></div>",
                f, new Owner());

        VNode.VElement host = (VNode.VElement) div.children().get(0);
        assertEquals("medley-component", host.tag());
        assertEquals("root.0", host.id(), "host takes the parent slot id");
        assertEquals("root.0::counter", host.attrs().get("data-medley-cid"),
                "host carries the child instance id marker");

        VNode.VElement childRoot = (VNode.VElement) host.children().get(0);
        assertEquals("root.0::counter", childRoot.id(), "child tree is rooted at hostId::name");
        assertTrue(text(childRoot).contains("count: 5"), "bound :start=n resolved against the owner");
    }

    @Test
    void twoChildrenGetDistinctIds() {
        FakeChildren f = new FakeChildren().add("counter", Counter::new, COUNTER_TPL);
        VNode.VElement div = (VNode.VElement) render(
                "<div><medley-component name=\"counter\" :start=\"n\"></medley-component>"
              + "<medley-component name=\"counter\" start=\"9\"></medley-component></div>",
                f, new Owner());

        VNode.VElement first = (VNode.VElement) div.children().get(0);
        VNode.VElement second = (VNode.VElement) div.children().get(1);
        assertEquals("root.0::counter", first.attrs().get("data-medley-cid"));
        assertEquals("root.1::counter", second.attrs().get("data-medley-cid"));
        assertTrue(text((VNode.VElement) first.children().get(0)).contains("count: 5"));
        assertTrue(text((VNode.VElement) second.children().get(0)).contains("count: 9"),
                "static start=\"9\" coerces to the int @Param");
    }

    @Test
    void unknownComponentThrows() {
        assertThrows(TemplateException.class, () -> render(
                "<div><medley-component name=\"missing\"></medley-component></div>",
                new FakeChildren(), new Owner()));
    }

    @Test
    void componentWithoutFactoryThrows() {
        TemplateRenderer r = TemplateRenderer.of(
                "<div><medley-component name=\"counter\"></medley-component></div>");
        assertThrows(TemplateException.class, () -> r.render("root", new Owner()));
    }

    @Test
    void blankNameThrows() {
        FakeChildren f = new FakeChildren().add("counter", Counter::new, COUNTER_TPL);
        assertThrows(TemplateException.class, () -> render(
                "<div><medley-component name=\"\"></medley-component></div>", f, new Owner()));
    }

    @Test
    void nullBoundParamIntoPrimitiveIsHandled() {
        // A bound :start that evaluates to null against a primitive int @Param must surface as a
        // handled TemplateException, not a raw IllegalArgumentException from Field.set.
        class OwnerWithNull {
            public Integer missing = null;
        }
        FakeChildren f = new FakeChildren().add("counter", Counter::new, COUNTER_TPL);
        assertThrows(TemplateException.class, () -> render(
                "<div><medley-component name=\"counter\" :start=\"missing\"></medley-component></div>",
                f, new OwnerWithNull()));
    }

    @Test
    void recursiveComponentIsGuarded() {
        FakeChildren f = new FakeChildren().add("loop", Object::new,
                "<div><medley-component name=\"loop\"></medley-component></div>");
        assertThrows(TemplateException.class, () -> render(
                "<div><medley-component name=\"loop\"></medley-component></div>", f, new Owner()));
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
