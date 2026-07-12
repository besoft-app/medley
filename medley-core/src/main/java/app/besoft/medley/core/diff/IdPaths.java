package app.besoft.medley.core.diff;

/**
 * Helpers for reasoning about Medley's path-based {@code data-medley-id} scheme.
 *
 * <p>Ids are built by appending a separator to a parent id: {@code parent + "." + position}
 * (positional child), {@code parent + "[" + key + "]"} (keyed {@code *for} item), or
 * {@code host + "::" + name} (nested component boundary — see {@code TemplateRenderer}). Containment
 * therefore means "same id, or a longer id whose extra part starts at one of those separators" — a
 * bare {@code startsWith} would wrongly report {@code "root.1"} as containing {@code "root.10"}.</p>
 */
public final class IdPaths {

    private IdPaths() {}

    /**
     * True when {@code id} is {@code ancestorId} itself or a node nested (transitively) beneath it.
     *
     * <p>The check is separator-aware: after the {@code ancestorId} prefix the next character must be
     * a Medley id separator ({@code '.'}, {@code ':'} for {@code "::"}, or {@code '['}), so numeric
     * neighbours like {@code "root.1"} and {@code "root.10"} do not falsely contain one another.</p>
     */
    public static boolean isSelfOrDescendant(String ancestorId, String id) {
        if (ancestorId == null || id == null) {
            return false;
        }
        if (id.equals(ancestorId)) {
            return true;
        }
        if (id.length() <= ancestorId.length() || !id.startsWith(ancestorId)) {
            return false;
        }
        char sep = id.charAt(ancestorId.length());
        return sep == '.' || sep == ':' || sep == '[';
    }

    /**
     * The component-instance id that <b>owns</b> a nested child boundary, recovered from the child id.
     *
     * <p>A child id is {@code hostSlot + "::" + name}, where {@code hostSlot} is the {@code data-medley-id}
     * of the {@code <medley-component>} element within the owner's render tree (e.g. {@code "root.0"},
     * {@code "root.0[1]"}, or {@code "root.2::editor.1"}). The owner is the component whose tree that
     * slot lives in: strip the trailing {@code "::name"}, then drop the slot suffix
     * ({@code ".n"} / {@code "[k]"} …) after the last {@code "::"} segment. Examples:
     * {@code "root.0::counter" -> "root"}, {@code "root.0[1]::counter" -> "root"},
     * {@code "root.2::editor.1::inner" -> "root.2::editor"}.</p>
     *
     * @return the owner component id, or {@code null} if {@code childId} is not a boundary id (no {@code "::"}).
     */
    public static String ownerComponentId(String childId) {
        if (childId == null) {
            return null;
        }
        int lastSep = childId.lastIndexOf("::");
        if (lastSep < 0) {
            return null;
        }
        String hostSlot = childId.substring(0, lastSep);
        int prevSep = hostSlot.lastIndexOf("::");
        String head = prevSep < 0 ? "" : hostSlot.substring(0, prevSep + 2);
        String tail = prevSep < 0 ? hostSlot : hostSlot.substring(prevSep + 2);
        int cut = firstSlotSeparator(tail);
        String ownerName = cut < 0 ? tail : tail.substring(0, cut);
        return head + ownerName;
    }

    /** Index of the first slot separator ({@code '.'} positional or {@code '['} keyed) in {@code s}, or -1. */
    private static int firstSlotSeparator(String s) {
        int dot = s.indexOf('.');
        int bracket = s.indexOf('[');
        if (dot < 0) return bracket;
        if (bracket < 0) return dot;
        return Math.min(dot, bracket);
    }
}
