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
     * @param needsHost true when this node needs its <b>own host element</b> in the DOM to be
     *                  addressable — i.e. the differ can emit a
     *                  {@link app.besoft.medley.core.diff.Patch.SetText} for <em>this id</em>, but a
     *                  browser cannot address a bare text node (and merges adjacent runs into one). The
     *                  serializer then wraps it in {@code <medley-text data-medley-id="…">}.
     *                  <p><b>Not a synonym for "came from an interpolation".</b> Text inside a raw-text
     *                  element ({@code <textarea>}, {@code <option>}, …) is interpolated too, but a marker
     *                  is illegal there, so {@code TemplateRenderer} merges that content into one node
     *                  carrying the <em>element's</em> id and leaves this false: the element is the
     *                  address. Setting it true for those would put an illegal element inside a
     *                  {@code <textarea>} and re-open the bug this flag exists to close
     *                  (MEDLEY_DESIGN §11a).</p>
     */
    record VText(String id, String value, boolean needsHost) implements VNode {

        /** Text that is addressed some other way (static text, or raw-text content held by its element). */
        public VText(String id, String value) {
            this(id, value, false);
        }
    }
}
