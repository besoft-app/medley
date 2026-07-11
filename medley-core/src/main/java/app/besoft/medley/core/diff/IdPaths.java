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
}
