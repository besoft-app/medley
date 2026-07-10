package app.besoft.medley.core.component;

import app.besoft.medley.core.diff.Differ;
import app.besoft.medley.core.diff.HtmlSerializer;
import app.besoft.medley.core.diff.Patch;
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

    private VNode lastTree;

    public ComponentInstance(String id, Component component, TemplateRenderer renderer) {
        this.id = id;
        this.component = component;
        this.renderer = renderer;
        this.component.bindId(id);
        this.actions = ActionScanner.scan(component.getClass());
    }

    public String id() {
        return id;
    }

    public Component component() {
        return component;
    }

    /** Initial render: builds the first VNode tree and returns its HTML for SSR. */
    public String renderInitialHtml() {
        component.onInit();
        lastTree = renderer.render(id, component);
        return HtmlSerializer.serialize(lastTree);
    }

    /** Re-render and diff against the previous tree. Call after state mutations. */
    public List<Patch> renderToPatches() {
        VNode next = renderer.render(id, component);
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
