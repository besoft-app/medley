package app.besoft.medley.core.diff;

import app.besoft.medley.core.vnode.Html;
import app.besoft.medley.core.vnode.VNode;

import java.util.Map;

/**
 * Serializes a VNode tree to HTML.
 *
 * <p>Used in two places: server-side rendering of the first page load, and producing the
 * {@code html} payload for {@link Patch.Insert} / {@link Patch.Replace} patches. Every
 * element carries its {@code medley-id} as a data attribute so the client can address it,
 * and events are emitted as {@code data-medley-on-*} attributes that {@code medley.js}
 * reads during hydration.</p>
 *
 * <p><b>Dynamic text gets a host element.</b> The differ addresses a text update by the text node's own
 * id, but a browser cannot address a bare text node — {@code medley.js} resolves a target only via
 * {@code [data-medley-id]}, which matches elements — and the HTML parser additionally merges adjacent
 * text runs into a single node. A {@link VNode.VText} that came from an interpolation is therefore
 * emitted inside {@code <medley-text data-medley-id="…">} carrying <b>that text node's existing id</b>.
 * The VNode tree, the id space, the differ and the wire are all unchanged; only the HTML gains a host,
 * and setting its {@code textContent} is exactly the intended update. Static text stays bare, so no
 * whitespace node becomes an element (list containers keep their keyed-only children).</p>
 *
 * <p>Raw-text elements are the exception: inside {@code <textarea>}/{@code <option>}/{@code <title>} a
 * marker would be illegal, so {@code TemplateRenderer} merges their content into one text node carrying
 * the <em>element's</em> id, and the patch addresses the element itself.</p>
 */
public final class HtmlSerializer {

    private HtmlSerializer() {}

    public static String serialize(VNode node) {
        StringBuilder sb = new StringBuilder();
        write(node, sb, null);
        return sb.toString();
    }

    private static void write(VNode node, StringBuilder sb, String parentTag) {
        switch (node) {
            case VNode.VText t -> writeText(t, sb, parentTag);
            case VNode.VElement el -> writeElement(el, sb);
        }
    }

    /**
     * Dynamic text is wrapped so the client can address it; static text is written as-is.
     *
     * <p>{@code parentTag} is {@code null} when this text is the <b>root</b> of the fragment being
     * serialized — which is exactly the case for a {@link Patch.Replace} / {@link Patch.Insert} payload
     * whose slot holds text. Such a payload <em>must</em> be an element: the client does
     * {@code htmlToElement(html).firstElementChild}, and bare text there yields {@code null} and a dropped
     * patch. So a null parent is "not raw text" — the marker is emitted.</p>
     */
    private static void writeText(VNode.VText t, StringBuilder sb, String parentTag) {
        if (t.needsHost() && !Html.isRawText(parentTag)) {
            sb.append('<').append(Html.TEXT_MARKER_TAG)
              .append(" data-medley-id=\"").append(escapeAttr(t.id())).append("\">")
              .append(escapeText(t.value()))
              .append("</").append(Html.TEXT_MARKER_TAG).append('>');
        } else {
            sb.append(escapeText(t.value()));
        }
    }

    private static void writeElement(VNode.VElement el, StringBuilder sb) {
        sb.append('<').append(el.tag());
        sb.append(" data-medley-id=\"").append(escapeAttr(el.id())).append('"');
        if (el.key() != null) {
            sb.append(" data-medley-key=\"").append(escapeAttr(el.key())).append('"');
        }
        for (Map.Entry<String, String> a : el.attrs().entrySet()) {
            sb.append(' ').append(a.getKey())
              .append("=\"").append(escapeAttr(a.getValue())).append('"');
        }
        for (Map.Entry<String, String> ev : el.events().entrySet()) {
            sb.append(" data-medley-on-").append(ev.getKey())
              .append("=\"").append(escapeAttr(ev.getValue())).append('"');
        }
        sb.append('>');
        for (VNode child : el.children()) {
            write(child, sb, el.tag());
        }
        sb.append("</").append(el.tag()).append('>');
    }

    static String escapeText(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    static String escapeAttr(String s) {
        return s.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
