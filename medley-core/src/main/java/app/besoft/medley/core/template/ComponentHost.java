package app.besoft.medley.core.template;

import app.besoft.medley.core.vnode.VNode;

import java.util.Map;

/**
 * The session-scoped coordinator that owns nested child component instances (Stage 4, increment 4b.2).
 *
 * <p>A {@code <medley-component name="x" ...>} boundary is <em>island-symmetric</em>: the host is a
 * diff-opaque leaf and the child subtree is owned by its own persistent instance. When the renderer
 * expands a boundary it does not create the child itself — it asks the host to
 * {@linkplain #mountChild mount (or reuse)} the child and hand back its current VNode subtree, which
 * the renderer embeds under the opaque host purely for serialization (SSR / insert / replace).</p>
 *
 * <p>The host is threaded through {@code render(...)} rather than held by the {@link TemplateRenderer}
 * because a renderer is cached per component class and shared across all sessions, whereas child
 * instances are per session. Core stays Spring-free: the starter implements this over its component
 * registry (fresh prototype bean) + session instance map, mirroring {@link PartialResolver} and
 * {@link ChildComponentFactory}.</p>
 */
@FunctionalInterface
public interface ComponentHost {

    /**
     * Mount the child at this boundary on first encounter (create the bean, inject {@code @Param}s,
     * wire its {@code @Output} callbacks, start its lifecycle, register it under {@code childId} for
     * action routing, render it once), or reuse the already-mounted instance on a later parent
     * re-render (its {@code @State} survives — the child is <em>not</em> re-created and, in 4b.2, not
     * re-rendered here). Either way returns the child's current VNode subtree rooted at {@code childId}.
     *
     * @param childId the child instance id, {@code hostId + "::" + name}, also its DOM boundary marker
     * @param name    the {@code name} attribute of the {@code <medley-component>}
     * @param params  resolved param values by attribute name (owner-evaluated for {@code :attr})
     * @param outputs output bindings by output name → owner-action binding (the {@code @event} entries
     *                on the boundary, e.g. {@code "save" -> "onSave($event)"}); wired once at mount
     * @param depth   the expansion depth to render the child at (for the recursion guard)
     * @return the child's current subtree, or {@code null} if no component is registered under {@code name}
     */
    VNode mountChild(String childId, String name, Map<String, Object> params,
                     Map<String, String> outputs, int depth);

    /** Convenience overload for a boundary with no {@code @Output} bindings (and for callers/tests
     *  that predate child→parent callbacks). Delegates with an empty output map. */
    default VNode mountChild(String childId, String name, Map<String, Object> params, int depth) {
        return mountChild(childId, name, params, Map.of(), depth);
    }
}
