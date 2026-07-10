package app.besoft.medley.core.diff;

import app.besoft.medley.core.vnode.VNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares two VNode trees and produces a minimal-ish list of {@link Patch}es.
 *
 * <p>This is the classic same-level tree diff used by React/Blazor — not a full
 * tree-edit-distance algorithm. The heuristic: nodes are compared positionally at each
 * level, except keyed children which are matched by key so list reordering does not
 * destroy and recreate nodes. Complexity is linear in the number of nodes visited.</p>
 *
 * <p>Rules:
 * <ul>
 *   <li>different node kind (text vs element) or different tag -> {@link Patch.Replace}</li>
 *   <li>same text node, different value -> {@link Patch.SetText}</li>
 *   <li>same element -> diff attributes, diff events, recurse into children</li>
 *   <li>keyed children -> match by key (insert / remove / recurse), unkeyed -> by index</li>
 * </ul>
 */
public final class Differ {

    private final List<Patch> patches = new ArrayList<>();

    public static List<Patch> diff(VNode prev, VNode next) {
        Differ d = new Differ();
        d.diffNode(prev, next);
        return d.patches;
    }

    private void diffNode(VNode prev, VNode next) {
        // kind change -> replace
        if (prev.getClass() != next.getClass()) {
            patches.add(new Patch.Replace(prev.id(), HtmlSerializer.serialize(next)));
            return;
        }
        if (prev instanceof VNode.VText pt && next instanceof VNode.VText nt) {
            if (!pt.value().equals(nt.value())) {
                patches.add(new Patch.SetText(pt.id(), nt.value()));
            }
            return;
        }
        VNode.VElement pe = (VNode.VElement) prev;
        VNode.VElement ne = (VNode.VElement) next;

        // tag change -> replace whole subtree
        if (!pe.tag().equals(ne.tag())) {
            patches.add(new Patch.Replace(pe.id(), HtmlSerializer.serialize(ne)));
            return;
        }

        diffAttrs(pe, ne);
        diffEvents(pe, ne);

        // An opaque boundary (a <medley-component> host) owns its child subtree via its own
        // instance's diff loop, so a parent re-render diffs the host's attributes/events but never
        // recurses into the children. This is what keeps a nested child's DOM (and @State) untouched
        // when only the parent re-renders, and is symmetric to how an island's internals are opaque.
        if (ne.opaque()) {
            return;
        }
        diffChildren(pe, ne);
    }

    private void diffAttrs(VNode.VElement pe, VNode.VElement ne) {
        // set or update
        for (Map.Entry<String, String> e : ne.attrs().entrySet()) {
            String oldVal = pe.attrs().get(e.getKey());
            if (oldVal == null || !oldVal.equals(e.getValue())) {
                patches.add(new Patch.SetAttr(ne.id(), e.getKey(), e.getValue()));
            }
        }
        // remove
        for (String name : pe.attrs().keySet()) {
            if (!ne.attrs().containsKey(name)) {
                patches.add(new Patch.RemoveAttr(ne.id(), name));
            }
        }
    }

    private void diffEvents(VNode.VElement pe, VNode.VElement ne) {
        for (Map.Entry<String, String> e : ne.events().entrySet()) {
            String oldVal = pe.events().get(e.getKey());
            if (oldVal == null || !oldVal.equals(e.getValue())) {
                patches.add(new Patch.SetEvent(ne.id(), e.getKey(), e.getValue()));
            }
        }
        for (String ev : pe.events().keySet()) {
            if (!ne.events().containsKey(ev)) {
                patches.add(new Patch.RemoveEvent(ne.id(), ev));
            }
        }
    }

    private void diffChildren(VNode.VElement pe, VNode.VElement ne) {
        boolean keyed = !ne.children().isEmpty()
                && ne.children().stream().allMatch(c -> c instanceof VNode.VElement el && el.key() != null);

        if (keyed) {
            diffKeyedChildren(pe, ne);
        } else {
            diffPositionalChildren(pe, ne);
        }
    }

    private void diffPositionalChildren(VNode.VElement pe, VNode.VElement ne) {
        List<VNode> prev = pe.children();
        List<VNode> next = ne.children();
        int common = Math.min(prev.size(), next.size());

        for (int i = 0; i < common; i++) {
            diffNode(prev.get(i), next.get(i));
        }
        // extra new children -> insert
        for (int i = common; i < next.size(); i++) {
            patches.add(new Patch.Insert(next.get(i).id(), ne.id(), i, HtmlSerializer.serialize(next.get(i))));
        }
        // removed old children -> remove
        for (int i = common; i < prev.size(); i++) {
            patches.add(new Patch.Remove(prev.get(i).id()));
        }
    }

    private void diffKeyedChildren(VNode.VElement pe, VNode.VElement ne) {
        Map<String, VNode.VElement> prevByKey = new LinkedHashMap<>();
        for (VNode c : pe.children()) {
            if (c instanceof VNode.VElement el && el.key() != null) {
                prevByKey.put(el.key(), el);
            }
        }

        int index = 0;
        for (VNode c : ne.children()) {
            VNode.VElement nel = (VNode.VElement) c;
            VNode.VElement pel = prevByKey.remove(nel.key());
            if (pel == null) {
                // new key -> insert
                patches.add(new Patch.Insert(nel.id(), ne.id(), index, HtmlSerializer.serialize(nel)));
            } else {
                // existing key -> diff in place (ids are key-derived so they line up)
                diffNode(pel, nel);
            }
            index++;
        }
        // leftover prev keys were removed
        for (VNode.VElement removed : prevByKey.values()) {
            patches.add(new Patch.Remove(removed.id()));
        }
    }
}
