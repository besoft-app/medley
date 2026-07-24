package app.besoft.medley.core.component;

import app.besoft.medley.core.diff.Differ;
import app.besoft.medley.core.diff.HtmlSerializer;
import app.besoft.medley.core.diff.Patch;
import app.besoft.medley.core.template.ComponentHost;
import app.besoft.medley.core.template.Projection;
import app.besoft.medley.core.template.TemplateException;
import app.besoft.medley.core.template.TemplateRenderer;
import app.besoft.medley.core.vnode.VNode;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Runtime wrapper around a single {@link Component} instance.
 *
 * <p>Holds the parsed template, the last rendered VNode tree, and the action lookup table.
 * This is where the render/diff loop lives:
 * <pre>
 *   invokeAction(name) -> mutate state -> renderToPatches() -> List&lt;Patch&gt;
 * </pre>
 * The previous VNode tree is retained so the next render can be diffed against it.</p>
 */
public final class ComponentInstance {

    private final Component component;
    private final TemplateRenderer renderer;
    private final Map<String, Method> actions;
    private final String id;
    /** Coordinator for nested {@code <medley-component>} boundaries; null for a leaf/PoC tree. */
    private final ComponentHost host;

    private VNode lastTree;
    /** Content the parent projected into this instance's {@code <medley-slot>}; set by the host. */
    private Projection projection = Projection.EMPTY;

    public ComponentInstance(String id, Component component, TemplateRenderer renderer) {
        this(id, component, renderer, null);
    }

    public ComponentInstance(String id, Component component, TemplateRenderer renderer, ComponentHost host) {
        this.id = id;
        this.component = component;
        this.renderer = renderer;
        this.host = host;
        this.component.bindId(id);
        this.actions = ActionScanner.scan(component.getClass());
    }

    public String id() {
        return id;
    }

    public Component component() {
        return component;
    }

    /** The content this instance currently renders into its {@code <medley-slot>} (6.2). */
    public Projection projection() {
        return projection;
    }

    /**
     * Set the content projected from the parent. Null is normalised to {@link Projection#EMPTY}.
     *
     * <p>Rejects body content given to a component whose template can never render it: a silent drop
     * would hide the author's typo, and the projected nodes would then be diffed against a DOM that
     * never received them.</p>
     */
    public void setProjection(Projection projection) {
        Projection p = projection == null ? Projection.EMPTY : projection;
        if (!p.isEmpty()) {
            if (!renderer.mayDeclareSlot()) {
                throw new TemplateException("Component '" + id + "' declares no <medley-slot>, but its "
                        + "<medley-component> boundary was given body content");
            }
            // 6.3: reject content aimed at a slot name the child never declares (a typo'd slot=, or
            // unnamed content with no default slot). A null declared set means a <medley-partial> could
            // declare anything → be permissive, matching mayDeclareSlot.
            java.util.Set<String> declared = renderer.declaredSlotNames();
            if (declared != null) {
                for (String filled : p.filledSlots()) {
                    if (!declared.contains(filled)) {
                        String which = filled.isEmpty() ? "a default <medley-slot>"
                                : "<medley-slot name=\"" + filled + "\">";
                        throw new TemplateException("Component '" + id + "' declares no " + which
                                + ", but its <medley-component> boundary projects content into it");
                    }
                }
            }
        }
        this.projection = p;
    }

    /** Initial render: builds the first VNode tree and returns its HTML for SSR. */
    public String renderInitialHtml() {
        component.onInit();
        lastTree = renderer.render(id, component, 0, host, projection);
        return HtmlSerializer.serialize(lastTree);
    }

    /**
     * Re-render this instance's subtree fresh and return its HTML, updating the diff baseline. Unlike
     * {@link #renderInitialHtml} it does <em>not</em> re-run {@code onInit} — the instance is already
     * mounted. Used for reconnect resync (Stage 4, increment 6b): the render re-embeds each nested
     * child's <em>current</em> state (via the host), so the returned HTML reflects the whole current
     * tree, and {@code lastTree} becomes the baseline the client is now in sync with.
     */
    public String resyncHtml() {
        lastTree = renderer.render(id, component, 0, host, projection);
        return HtmlSerializer.serialize(lastTree);
    }

    /**
     * Render (or re-render) this instance's own subtree at the given expansion depth, updating the
     * diff baseline, and return it. Used by a {@link ComponentHost} to mount a nested child — {@code
     * onInit} is <em>not</em> called here (a child's lifecycle is started by the factory), and depth
     * is threaded so the recursion guard sees the true nesting across the boundary.
     */
    public VNode renderTree(int depth) {
        lastTree = renderer.render(id, component, depth, host, projection);
        return lastTree;
    }

    /** The current (last-rendered) subtree, or null if not yet rendered. */
    public VNode currentTree() {
        return lastTree;
    }

    /** Re-render and diff against the previous tree. Call after state mutations. */
    public List<Patch> renderToPatches() {
        VNode next = renderer.render(id, component, 0, host, projection);
        List<Patch> patches = (lastTree == null)
                ? List.of() // nothing to diff against; caller should have used renderInitialHtml
                : Differ.diff(lastTree, next);
        lastTree = next;
        return patches;
    }

    /**
     * Invoke a server action by the name referenced in the template's {@code @event}, then
     * produce the resulting patches.
     */
    public List<Patch> invokeAction(String actionName, Object... args) {
        Method m = actions.get(actionName);
        if (m == null) {
            throw new TemplateException("No @Action named '" + actionName + "' on "
                    + component.getClass().getSimpleName());
        }
        // Client-supplied args arrive as JSON scalars (String/Number/Boolean). Bind each to the
        // action's declared parameter type — the reflective signature lives here in core, so typed
        // coercion belongs here (the starter's arg decoder stays a dumb JSON reader). The action
        // *name* is already whitelisted above; values are inert data, never used for lookup.
        Class<?>[] paramTypes = m.getParameterTypes();
        if (args.length != paramTypes.length) {
            throw new TemplateException("Action '" + actionName + "' expects " + paramTypes.length
                    + " argument(s) but got " + args.length);
        }
        Object[] coerced = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            coerced[i] = Coercions.coerce(args[i], paramTypes[i]);
        }
        try {
            m.setAccessible(true);
            m.invoke(component, coerced);
        } catch (Exception e) {
            throw new TemplateException("Action '" + actionName + "' failed", e);
        }
        return renderToPatches();
    }
}
