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
     * @param opaque   when true the differ treats this element as a boundary: it diffs the host's
     *                 own attributes/events but never recurses into {@code children}. Used by nested
     *                 server components ({@code <medley-component>}), whose child subtree is owned and
     *                 diffed by its own instance, so a parent re-render never touches the child's DOM.
     *                 The serializer still emits the children (needed for SSR and insert/replace).
     */
    record VElement(
            String id,
            String tag,
            Map<String, String> attrs,
            Map<String, String> events,
            List<VNode> children,
            String key,
            boolean opaque
    ) implements VNode {
        public VElement {
            attrs = Map.copyOf(attrs);
            events = Map.copyOf(events);
            children = List.copyOf(children);
        }

        /** Ordinary (non-opaque) element — the common case. */
        public VElement(String id, String tag, Map<String, String> attrs,
                        Map<String, String> events, List<VNode> children, String key) {
            this(id, tag, attrs, events, children, key, false);
        }
    }

    /**
     * A text node.
     *
     * @param dynamic true when this text came from an interpolation ({@code {{ … }}}), i.e. the differ
     *                can emit a {@link app.besoft.medley.core.diff.Patch.SetText} for it. Such a node
     *                <b>must be addressable in the DOM</b>, so the serializer gives it a host element
     *                carrying this node's id (a browser cannot address a bare text node, and it merges
     *                adjacent text runs into one). Static template text is never patched and so stays
     *                bare text. See {@code HtmlSerializer}.
     */
    record VText(String id, String value, boolean dynamic) implements VNode {

        /** Static template text — never patched, so it needs no addressable host. */
        public VText(String id, String value) {
            this(id, value, false);
        }
    }
}
