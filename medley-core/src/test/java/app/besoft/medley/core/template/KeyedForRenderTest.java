package app.besoft.medley.core.template;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import app.besoft.medley.core.diff.Differ;
import app.besoft.medley.core.diff.Patch;
import app.besoft.medley.core.vnode.VNode;
import org.junit.jupiter.api.Test;

/**
 * End-to-end keyed {@code *for}: render a real (whitespace-tight) list template, mutate the
 * backing list, diff, and assert the minimal patch set. The container is authored tightly so
 * every child of the loop parent is a keyed element (see {@code DifferTest} for why interspersed
 * whitespace would drop to positional diffing).
 */
class KeyedForRenderTest {

    static class Ctx {
        List<Row> rows;
        Ctx(List<Row> rows) { this.rows = rows; }
        public List<Row> getRows() { return rows; }
    }

    record Row(String id, String name) {}

    private static final TemplateRenderer LIST =
            TemplateRenderer.of("<ul><li *for=\"row : rows\" key=\"row.id\">{{ row.name }}</li></ul>");

    @Test
    void addingARowEmitsASingleInsert() {
        Ctx ctx = new Ctx(new ArrayList<>(List.of(new Row("1", "one"), new Row("2", "two"))));
        VNode before = LIST.render("root", ctx);
        ctx.rows.add(new Row("3", "three"));
        VNode after = LIST.render("root", ctx);

        List<Patch> patches = Differ.diff(before, after);
        assertEquals(1, patches.size());
        Patch.Insert insert = (Patch.Insert) patches.get(0);
        // The *for <li> is child 0 of the <ul>, so keyed ids are "<ul-id>.0[<key>]".
        assertEquals("root.0[3]", insert.id());
        assertEquals("root", insert.parentId());
        assertEquals(2, insert.index());
    }

    @Test
    void removingAMiddleRowEmitsASingleRemove() {
        Ctx ctx = new Ctx(new ArrayList<>(List.of(
                new Row("1", "one"), new Row("2", "two"), new Row("3", "three"))));
        VNode before = LIST.render("root", ctx);
        ctx.rows.remove(1); // drop key "2"
        VNode after = LIST.render("root", ctx);

        List<Patch> patches = Differ.diff(before, after);
        assertEquals(1, patches.size());
        assertEquals("root.0[2]", ((Patch.Remove) patches.get(0)).id());
    }

    @Test
    void updatingARowInPlaceEmitsASingleSetText() {
        Ctx ctx = new Ctx(new ArrayList<>(List.of(new Row("1", "one"), new Row("2", "two"))));
        VNode before = LIST.render("root", ctx);
        ctx.rows.set(1, new Row("2", "TWO")); // same key, new content
        VNode after = LIST.render("root", ctx);

        List<Patch> patches = Differ.diff(before, after);
        assertEquals(1, patches.size());
        Patch.SetText patch = (Patch.SetText) patches.get(0);
        assertEquals("root.0[2].0", patch.id());
        assertEquals("TWO", patch.value());
    }
}
