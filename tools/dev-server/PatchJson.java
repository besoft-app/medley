import app.besoft.medley.core.diff.Patch;

import java.util.List;

/** Minimal, dependency-free JSON encoder for patches (harness mirror of PatchEncoder). */
public final class PatchJson {

    public static String encode(List<Patch> patches) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < patches.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(node(patches.get(i)));
        }
        return sb.append(']').toString();
    }

    private static String node(Patch p) {
        return switch (p) {
            case Patch.SetText s -> obj("op", "text", "id", s.id(), "value", s.value());
            case Patch.SetAttr s -> obj("op", "attr", "id", s.id(), "name", s.name(), "value", s.value());
            case Patch.RemoveAttr s -> obj("op", "removeAttr", "id", s.id(), "name", s.name());
            case Patch.SetEvent s -> obj("op", "event", "id", s.id(), "event", s.event(), "action", s.action());
            case Patch.RemoveEvent s -> obj("op", "removeEvent", "id", s.id(), "event", s.event());
            case Patch.Replace s -> obj("op", "replace", "id", s.id(), "html", s.html());
            case Patch.Insert s -> "{" + kv("op", "insert") + "," + kv("id", s.id()) + ","
                    + kv("parentId", s.parentId()) + "," + "\"index\":" + s.index() + ","
                    + kv("html", s.html()) + "}";
            case Patch.Remove s -> obj("op", "remove", "id", s.id());
        };
    }

    private static String obj(String... kvs) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < kvs.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append(kv(kvs[i], kvs[i + 1]));
        }
        return sb.append('}').toString();
    }

    private static String kv(String k, String v) {
        return "\"" + k + "\":\"" + esc(v) + "\"";
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
