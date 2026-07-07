package app.besoft.medley.core.vnode;

import java.util.List;
import java.util.Map;

/**
 * Virtual node: an immutable, server-side description of a piece of UI.
 *
 * <p>The render pipeline turns a component + its template into a tree of {@code VNode}s.
 * The differ compares the previous tree with the next one and emits patches. The VNode
 * tree never touches the real DOM directly — that is the job of {@code medley.js} on the
 * client, which applies the patches.</p>
 */
public sealed interface VNode permits VNode.VElement, VNode.VText {

    /** Stable identifier assigned during rendering, used to address patches to real DOM. */
    String id();

    /**
     * An element node: {@code <div>}, {@code <button>}, {@code <medley-island>}, etc.
     *
     * @param tag      the lowercase tag name
     * @param attrs    static + computed attributes (already escaped where needed)
     * @param events   DOM event -> server action id (e.g. "click" -> "increment")
     * @param children child nodes in document order
     * @param key      optional stable key used by the differ for list reconciliation
     */
    record VElement(
            String id,
            String tag,
            Map<String, String> attrs,
            Map<String, String> events,
            List<VNode> children,
            String key
    ) implements VNode {
        public VElement {
            attrs = Map.copyOf(attrs);
            events = Map.copyOf(events);
            children = List.copyOf(children);
        }
    }

    /** A text node. */
    record VText(String id, String value) implements VNode {}
}
