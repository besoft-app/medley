package app.besoft.medley.core.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import app.besoft.medley.core.vnode.VNode;
import org.junit.jupiter.api.Test;

class DifferTest {

    private static VNode.VElement el(String id, String tag, Map<String, String> attrs,
                                     Map<String, String> events, List<VNode> children, String key) {
        return new VNode.VElement(id, tag, attrs, events, children, key);
    }

    private static VNode.VText txt(String id, String value) {
        return new VNode.VText(id, value);
    }

    @Test
    void noChangeNoPatches() {
        VNode a = el("r", "div", Map.of(), Map.of(), List.of(txt("r.0", "x")), null);
        VNode b = el("r", "div", Map.of(), Map.of(), List.of(txt("r.0", "x")), null);
        assertTrue(Differ.diff(a, b).isEmpty());
    }

    @Test
    void textChangeProducesSetText() {
        VNode a = el("r", "div", Map.of(), Map.of(), List.of(txt("r.0", "1")), null);
        VNode b = el("r", "div", Map.of(), Map.of(), List.of(txt("r.0", "2")), null);
        List<Patch> patches = Differ.diff(a, b);
        assertEquals(1, patches.size());
        assertTrue(patches.get(0) instanceof Patch.SetText);
        Patch.SetText p = (Patch.SetText) patches.get(0);
        assertEquals("r.0", p.id());
        assertEquals("2", p.value());
    }

    @Test
    void attributeAddedAndRemoved() {
        VNode a = el("r", "div", Map.of("class", "a"), Map.of(), List.of(), null);
        VNode b = el("r", "div", Map.of("id", "x"), Map.of(), List.of(), null);
        List<Patch> patches = Differ.diff(a, b);
        // expect one SetAttr(id=x) and one RemoveAttr(class)
        assertTrue(patches.stream().anyMatch(p -> p instanceof Patch.SetAttr sa
                && sa.name().equals("id") && sa.value().equals("x")));
        assertTrue(patches.stream().anyMatch(p -> p instanceof Patch.RemoveAttr ra
                && ra.name().equals("class")));
    }

    @Test
    void eventRewired() {
        VNode a = el("r", "button", Map.of(), Map.of("click", "old"), List.of(), null);
        VNode b = el("r", "button", Map.of(), Map.of("click", "new"), List.of(), null);
        List<Patch> patches = Differ.diff(a, b);
        assertEquals(1, patches.size());
        assertTrue(patches.get(0) instanceof Patch.SetEvent);
        assertEquals("new", ((Patch.SetEvent) patches.get(0)).action());
    }

    @Test
    void tagChangeProducesReplace() {
        VNode a = el("r", "div", Map.of(), Map.of(), List.of(), null);
        VNode b = el("r", "section", Map.of(), Map.of(), List.of(), null);
        List<Patch> patches = Differ.diff(a, b);
        assertEquals(1, patches.size());
        assertTrue(patches.get(0) instanceof Patch.Replace);
    }

    @Test
    void textToElementProducesReplace() {
        VNode a = el("r", "div", Map.of(), Map.of(), List.of(txt("r.0", "x")), null);
        VNode b = el("r", "div", Map.of(), Map.of(),
                List.of(el("r.0", "span", Map.of(), Map.of(), List.of(), null)), null);
        List<Patch> patches = Differ.diff(a, b);
        assertEquals(1, patches.size());
        assertTrue(patches.get(0) instanceof Patch.Replace);
    }

    @Test
    void childInserted() {
        VNode a = el("r", "div", Map.of(), Map.of(), List.of(txt("r.0", "a")), null);
        VNode b = el("r", "div", Map.of(), Map.of(),
                List.of(txt("r.0", "a"), txt("r.1", "b")), null);
        List<Patch> patches = Differ.diff(a, b);
        assertEquals(1, patches.size());
        assertTrue(patches.get(0) instanceof Patch.Insert);
    }

    @Test
    void childRemoved() {
        VNode a = el("r", "div", Map.of(), Map.of(),
                List.of(txt("r.0", "a"), txt("r.1", "b")), null);
        VNode b = el("r", "div", Map.of(), Map.of(), List.of(txt("r.0", "a")), null);
        List<Patch> patches = Differ.diff(a, b);
        assertEquals(1, patches.size());
        assertTrue(patches.get(0) instanceof Patch.Remove);
    }

    @Test
    void keyedReorderPreservesNodesButDoesNotVisuallyReorder() {
        VNode.VElement i1 = el("r[1]", "li", Map.of(), Map.of(), List.of(txt("r[1].0", "one")), "1");
        VNode.VElement i2 = el("r[2]", "li", Map.of(), Map.of(), List.of(txt("r[2].0", "two")), "2");
        VNode a = el("r", "ul", Map.of(), Map.of(), List.of(i1, i2), null);
        VNode b = el("r", "ul", Map.of(), Map.of(), List.of(i2, i1), null); // reordered
        List<Patch> patches = Differ.diff(a, b);
        // Same keys present in both -> matched by key -> nodes are preserved (no insert/remove).
        // NOTE: this proves node preservation, NOT visual reordering — there is no `move` op, so a
        // pure reorder is a diff no-op and the DOM order does not change (see the test below and
        // MEDLEY_DESIGN.md §4.3/§10). Visual reorder is a deferred increment (adds a `move` op).
        assertTrue(patches.stream().noneMatch(p -> p instanceof Patch.Insert));
        assertTrue(patches.stream().noneMatch(p -> p instanceof Patch.Remove));
    }

    @Test
    void pureKeyedReorderEmitsNoPatches() {
        VNode.VElement i1 = el("r[1]", "li", Map.of(), Map.of(), List.of(txt("r[1].0", "one")), "1");
        VNode.VElement i2 = el("r[2]", "li", Map.of(), Map.of(), List.of(txt("r[2].0", "two")), "2");
        VNode a = el("r", "ul", Map.of(), Map.of(), List.of(i1, i2), null);
        VNode b = el("r", "ul", Map.of(), Map.of(), List.of(i2, i1), null);
        // Documents the current limitation: reorder of identical keyed nodes = zero patches.
        assertTrue(Differ.diff(a, b).isEmpty());
    }

    @Test
    void keyedItemAddedProducesInsert() {
        VNode.VElement i1 = el("r[1]", "li", Map.of(), Map.of(), List.of(), "1");
        VNode.VElement i2 = el("r[2]", "li", Map.of(), Map.of(), List.of(), "2");
        VNode a = el("r", "ul", Map.of(), Map.of(), List.of(i1), null);
        VNode b = el("r", "ul", Map.of(), Map.of(), List.of(i1, i2), null);
        List<Patch> patches = Differ.diff(a, b);
        assertTrue(patches.stream().anyMatch(p -> p instanceof Patch.Insert));
    }

    @Test
    void keyedItemInsertedAtFrontIsPositionedAndLeavesSiblingUntouched() {
        VNode.VElement i2 = el("r[2]", "li", Map.of(), Map.of(), List.of(txt("r[2].0", "two")), "2");
        VNode.VElement i1 = el("r[1]", "li", Map.of(), Map.of(), List.of(txt("r[1].0", "one")), "1");
        VNode a = el("r", "ul", Map.of(), Map.of(), List.of(i2), null);
        VNode b = el("r", "ul", Map.of(), Map.of(), List.of(i1, i2), null); // new key at front
        List<Patch> patches = Differ.diff(a, b);
        assertEquals(1, patches.size());
        Patch.Insert insert = (Patch.Insert) patches.get(0);
        assertEquals("r[1]", insert.id());
        assertEquals(0, insert.index());
        assertEquals("r", insert.parentId());
    }

    @Test
    void keyedItemRemovedFromMiddleProducesSingleRemoveNoCascade() {
        VNode.VElement a1 = el("r[1]", "li", Map.of(), Map.of(), List.of(txt("r[1].0", "one")), "1");
        VNode.VElement a2 = el("r[2]", "li", Map.of(), Map.of(), List.of(txt("r[2].0", "two")), "2");
        VNode.VElement a3 = el("r[3]", "li", Map.of(), Map.of(), List.of(txt("r[3].0", "three")), "3");
        VNode a = el("r", "ul", Map.of(), Map.of(), List.of(a1, a2, a3), null);
        VNode b = el("r", "ul", Map.of(), Map.of(), List.of(a1, a3), null); // middle key removed
        List<Patch> patches = Differ.diff(a, b);
        assertEquals(1, patches.size());
        assertEquals("r[2]", ((Patch.Remove) patches.get(0)).id());
    }

    @Test
    void keyedInPlaceContentUpdateIsASinglePatch() {
        VNode.VElement a1 = el("r[1]", "li", Map.of(), Map.of(), List.of(txt("r[1].0", "one")), "1");
        VNode.VElement a2 = el("r[2]", "li", Map.of(), Map.of(), List.of(txt("r[2].0", "two")), "2");
        VNode.VElement b2 = el("r[2]", "li", Map.of(), Map.of(), List.of(txt("r[2].0", "TWO")), "2");
        VNode a = el("r", "ul", Map.of(), Map.of(), List.of(a1, a2), null);
        VNode b = el("r", "ul", Map.of(), Map.of(), List.of(a1, b2), null);
        List<Patch> patches = Differ.diff(a, b);
        assertEquals(1, patches.size());
        Patch.SetText p = (Patch.SetText) patches.get(0);
        assertEquals("r[2].0", p.id());
        assertEquals("TWO", p.value());
    }

    @Test
    void whitespaceSiblingForcesPositionalFallback() {
        // Keyed reconciliation only engages when EVERY child is a keyed element. A stray text node
        // (as the template parser emits for inter-tag whitespace) forces positional diffing, so an
        // otherwise-clean keyed reorder is no longer a no-op. Pins the "author tightly" precondition.
        VNode.VElement i1 = el("r[1]", "li", Map.of(), Map.of(), List.of(txt("r[1].0", "one")), "1");
        VNode.VElement i2 = el("r[2]", "li", Map.of(), Map.of(), List.of(txt("r[2].0", "two")), "2");
        VNode a = el("r", "ul", Map.of(), Map.of(), List.of(txt("r.ws", " "), i1, i2), null);
        VNode b = el("r", "ul", Map.of(), Map.of(), List.of(txt("r.ws", " "), i2, i1), null);
        // Not all children are keyed -> positional fallback -> reorder is NOT a clean no-op.
        assertTrue(!Differ.diff(a, b).isEmpty(),
                "a whitespace sibling must drop keyed reconciliation to positional diffing");
    }
}
