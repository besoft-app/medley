package app.besoft.medley.core.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import app.besoft.medley.core.vnode.VNode;
import org.junit.jupiter.api.Test;

class TemplateRendererTest {

    static class Ctx {
        public int count;
        public String label = "n";
        public List<Item> items = List.of(new Item("1", "one"), new Item("2", "two"));
        Ctx(int count) { this.count = count; }
    }

    record Item(String id, String text) {
        public String getId() { return id; }
        public String getText() { return text; }
    }

    @Test
    void rendersInterpolation() {
        VNode v = TemplateRenderer.of("<span>{{ label }}: {{ count }}</span>")
                .render("root", new Ctx(5));
        VNode.VElement el = (VNode.VElement) v;
        // children: text "" , interp label, text ": ", interp count
        String text = collectText(el);
        assertEquals("n: 5", text.strip());
    }

    @Test
    void rendersBoundAttribute() {
        VNode v = TemplateRenderer.of("<div :data-n=\"count\">x</div>").render("root", new Ctx(7));
        VNode.VElement el = (VNode.VElement) v;
        assertEquals("7", el.attrs().get("data-n"));
    }

    @Test
    void ifTrueRendersElement() {
        VNode v = TemplateRenderer.of("<div><button *if=\"count > 0\">go</button></div>")
                .render("root", new Ctx(1));
        VNode.VElement root = (VNode.VElement) v;
        VNode.VElement child = (VNode.VElement) root.children().get(0);
        assertEquals("button", child.tag());
    }

    @Test
    void ifFalseRendersPlaceholder() {
        VNode v = TemplateRenderer.of("<div><button *if=\"count > 0\">go</button></div>")
                .render("root", new Ctx(0));
        VNode.VElement root = (VNode.VElement) v;
        VNode.VElement child = (VNode.VElement) root.children().get(0);
        assertEquals("medley-placeholder", child.tag());
        // crucially, the slot id is stable regardless of branch
        assertEquals("root.0", child.id());
    }

    @Test
    void ifBranchesShareStableId() {
        TemplateRenderer r = TemplateRenderer.of("<div><button *if=\"count > 0\">go</button></div>");
        VNode whenFalse = r.render("root", new Ctx(0));
        VNode whenTrue = r.render("root", new Ctx(1));
        String idFalse = ((VNode.VElement) whenFalse).children().get(0).id();
        String idTrue = ((VNode.VElement) whenTrue).children().get(0).id();
        assertEquals(idFalse, idTrue, "conditional slot must keep a stable id across branches");
    }

    @Test
    void forLoopExpands() {
        VNode v = TemplateRenderer.of(
                "<ul><li *for=\"item : items\" key=\"item.id\">{{ item.text }}</li></ul>")
                .render("root", new Ctx(0));
        VNode.VElement ul = (VNode.VElement) v;
        assertEquals(2, ul.children().size());
        VNode.VElement li0 = (VNode.VElement) ul.children().get(0);
        assertEquals("1", li0.key());
        assertEquals("one", collectText(li0).strip());
        VNode.VElement li1 = (VNode.VElement) ul.children().get(1);
        assertEquals("2", li1.key());
    }

    @Test
    void forLoopIdsAreKeyDerived() {
        VNode v = TemplateRenderer.of(
                "<ul><li *for=\"item : items\" key=\"item.id\">{{ item.text }}</li></ul>")
                .render("root", new Ctx(0));
        VNode.VElement ul = (VNode.VElement) v;
        assertTrue(ul.children().get(0).id().contains("[1]"));
        assertTrue(ul.children().get(1).id().contains("[2]"));
    }

    @Test
    void eventsCarriedToVNode() {
        VNode v = TemplateRenderer.of("<button @click=\"inc\">+</button>").render("root", new Ctx(0));
        VNode.VElement el = (VNode.VElement) v;
        assertEquals("inc", el.events().get("click"));
    }

    private static String collectText(VNode node) {
        if (node instanceof VNode.VText t) return t.value();
        StringBuilder sb = new StringBuilder();
        for (VNode c : ((VNode.VElement) node).children()) sb.append(collectText(c));
        return sb.toString();
    }
}
