package app.besoft.medley.core.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import app.besoft.medley.core.diff.Differ;
import app.besoft.medley.core.diff.Patch;
import app.besoft.medley.core.vnode.VNode;
import org.junit.jupiter.api.Test;

/**
 * A {@code <medley-island>} is a client-owned region. The server must render it as an opaque,
 * childless host so the differ only ever patches its host attributes (props) and never diffs or
 * replaces its client-managed internal DOM — otherwise a re-render would wipe island state.
 */
class IslandRenderTest {

    static class Ctx {
        public String points = "1,2,3";
        public boolean show = true;
    }

    @Test
    void islandRendersChildlessWithPropsAsAttributes() {
        VNode v = TemplateRenderer.of(
                "<div><medley-island name=\"sparkline\" :data-points=\"points\">"
                        + "server child ignored</medley-island></div>")
                .render("root", new Ctx());

        VNode.VElement island = (VNode.VElement) ((VNode.VElement) v).children().get(0);
        assertEquals("medley-island", island.tag());
        assertTrue(island.children().isEmpty(), "island must be rendered childless server-side");
        assertEquals("sparkline", island.attrs().get("name"));
        assertEquals("1,2,3", island.attrs().get("data-points"), "bound prop rendered as attribute");
    }

    @Test
    void propChangeYieldsExactlyOneAttrPatchOnTheHost() {
        TemplateRenderer r = TemplateRenderer.of(
                "<div><medley-island name=\"sparkline\" :data-points=\"points\"></medley-island></div>");

        Ctx ctx = new Ctx();
        VNode before = r.render("root", ctx);
        ctx.points = "1,2,3,4";
        VNode after = r.render("root", ctx);

        List<Patch> patches = Differ.diff(before, after);

        assertEquals(1, patches.size(), "a props change must be a single host-attribute patch");
        Patch.SetAttr patch = (Patch.SetAttr) patches.get(0);
        assertEquals("root.0", patch.id(), "patch targets the island host, not any child");
        assertEquals("data-points", patch.name());
        assertEquals("1,2,3,4", patch.value());
    }

    @Test
    void islandUnderFalseIfStillBecomesPlaceholder() {
        VNode v = TemplateRenderer.of(
                "<div><medley-island name=\"x\" *if=\"show\"></medley-island></div>")
                .render("root", new Ctx() {{ show = false; }});

        VNode.VElement child = (VNode.VElement) ((VNode.VElement) v).children().get(0);
        assertEquals("medley-placeholder", child.tag(), "*if is not special-cased away for islands");
        assertEquals("root.0", child.id(), "conditional slot keeps a stable id");
    }
}
