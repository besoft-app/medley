package app.besoft.medley.core.diff;

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
 */
public final class HtmlSerializer {

    private HtmlSerializer() {}

    public static String serialize(VNode node) {
        StringBuilder sb = new StringBuilder();
        write(node, sb);
        return sb.toString();
    }

    private static void write(VNode node, StringBuilder sb) {
        switch (node) {
            case VNode.VText t -> sb.append(escapeText(t.value()));
            case VNode.VElement el -> writeElement(el, sb);
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
            write(child, sb);
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
