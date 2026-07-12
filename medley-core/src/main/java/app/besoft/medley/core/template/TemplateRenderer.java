package app.besoft.medley.core.template;

import app.besoft.medley.core.vnode.VNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a parsed template AST + a context object (the component instance) into a VNode tree.
 *
 * <p>Each produced node gets a stable {@code id}. Ids are path-based:
 * the root is the component id, and each child appends its position (and key, when present).
 * Stable ids are what let the differ + client address patches to specific DOM nodes across
 * renders. For keyed {@code *for} items the key is folded into the id so that reordering a
 * list keeps node identity.</p>
 *
 * <p>Expression scope: the base context is the component. Inside a {@code *for} the loop
 * variable shadows/extends the scope; we model this with a lightweight {@link ScopedContext}
 * so the existing {@link ExpressionEvaluator} (which reads members off a single object)
 * keeps working unchanged.</p>
 */
public final class TemplateRenderer {

    /** Client-owned region: rendered as an opaque, childless host so the differ never touches
     *  its client-managed internals (see {@link #renderSingleElement}). */
    private static final String ISLAND_TAG = "medley-island";
    /** Reusable template fragment expanded inline into the owner tree (see {@link #expandPartial}). */
    private static final String PARTIAL_TAG = "medley-partial";
    /** Nested child component boundary (see {@link #expandComponent}). */
    private static final String COMPONENT_TAG = "medley-component";
    /** Guards against a partial or component that (transitively) includes itself. */
    private static final int MAX_PARTIAL_DEPTH = 32;

    private final TemplateNode.Element root;
    /** Resolves {@code <medley-partial>} fragments; null when partials are unsupported (e.g. tests). */
    private final PartialResolver partials;

    public TemplateRenderer(TemplateNode.Element root) {
        this(root, null);
    }

    public TemplateRenderer(TemplateNode.Element root, PartialResolver partials) {
        this.root = root;
        this.partials = partials;
    }

    public static TemplateRenderer of(String template) {
        return new TemplateRenderer(TemplateParser.parse(template), null);
    }

    public static TemplateRenderer of(String template, PartialResolver partials) {
        return new TemplateRenderer(TemplateParser.parse(template), partials);
    }

    /**
     * Render the template for the given component instance, with no nested-component support.
     *
     * @param componentId the id of the owning component (used as the root id prefix)
     * @param context     the component instance whose state the template reads
     */
    public VNode render(String componentId, Object context) {
        return render(componentId, context, 0, null);
    }

    /**
     * Render the template, resolving any {@code <medley-component>} boundaries through {@code host}.
     *
     * @param host the session-scoped coordinator that mounts/reuses nested child instances; null
     *             disables nested components (a boundary then raises a {@link TemplateException})
     */
    public VNode render(String componentId, Object context, ComponentHost host) {
        return render(componentId, context, 0, host);
    }

    /** Render starting at a given expansion depth — threaded across component/partial boundaries so
     *  the recursion guard sees the true nesting (a child render is not a fresh depth-0 tree). Public
     *  so a {@link ComponentHost} can mount a nested child at the parent's continuing depth. */
    public VNode render(String componentId, Object context, int depth, ComponentHost host) {
        List<VNode> nodes = renderNode(root, componentId, context, depth, host);
        if (nodes.size() != 1) {
            throw new TemplateException("Template root must render exactly one element");
        }
        return nodes.get(0);
    }

    /** Returns a list because *for can expand one template node into many VNodes.
     *  {@code depth} counts partial-expansion nesting, for the recursion guard. */
    private List<VNode> renderNode(TemplateNode node, String id, Object ctx, int depth, ComponentHost host) {
        return switch (node) {
            case TemplateNode.Text t -> List.of(new VNode.VText(id, t.value()));
            case TemplateNode.Interpolation i -> {
                String text = new ExpressionEvaluator(ctx).evalString(i.expr());
                yield List.of(new VNode.VText(id, text));
            }
            case TemplateNode.Element el -> renderElement(el, id, ctx, depth, host);
        };
    }

    private List<VNode> renderElement(TemplateNode.Element el, String id, Object ctx, int depth, ComponentHost host) {
        // *for expands first
        if (el.forExpr() != null) {
            return renderForLoop(el, id, ctx, depth, host);
        }
        // *if gates the single element. When false we still occupy exactly one slot with a
        // stable placeholder so sibling positions (and therefore ids) do not drift between
        // renders. The differ then emits a clean Replace in place instead of insert/remove.
        if (el.ifExpr() != null && !new ExpressionEvaluator(ctx).evalBoolean(el.ifExpr())) {
            return List.of(placeholder(id));
        }
        return List.of(renderInstance(el, id, ctx, null, depth, host));
    }

    /** A zero-content, hidden element used to hold the slot of a false {@code *if}. */
    private static VNode placeholder(String id) {
        return new VNode.VElement(
                id, "medley-placeholder",
                java.util.Map.of("hidden", "hidden"),
                java.util.Map.of(),
                List.of(),
                null
        );
    }

    private List<VNode> renderForLoop(TemplateNode.Element el, String id, Object ctx, int depth, ComponentHost host) {
        Object iterable = new ExpressionEvaluator(ctx).eval(el.forExpr());
        if (!(iterable instanceof Iterable<?> items)) {
            throw new TemplateException("*for expression must be Iterable: " + el.forExpr());
        }
        List<VNode> out = new ArrayList<>();
        int index = 0;
        for (Object item : items) {
            ScopedContext scope = new ScopedContext(ctx, el.forVar(), item);
            // *if can also filter inside a loop
            if (el.ifExpr() != null && !new ExpressionEvaluator(scope).evalBoolean(el.ifExpr())) {
                index++;
                continue;
            }
            String key = el.keyExpr() != null
                    ? new ExpressionEvaluator(scope).evalString(el.keyExpr())
                    : String.valueOf(index);
            String childId = id + "[" + key + "]";
            out.add(renderInstance(el, childId, scope, key, depth, host));
            index++;
        }
        return out;
    }

    /** One element instance: a {@code <medley-partial>} expands to its fragment; anything else
     *  renders directly. */
    private VNode renderInstance(TemplateNode.Element el, String id, Object ctx, String key, int depth, ComponentHost host) {
        if (PARTIAL_TAG.equals(el.tag())) {
            return expandPartial(el, id, ctx, key, depth, host);
        }
        if (COMPONENT_TAG.equals(el.tag())) {
            return expandComponent(el, id, ctx, key, depth, host);
        }
        return renderSingleElement(el, id, ctx, key, depth, host);
    }

    private VNode renderSingleElement(TemplateNode.Element el, String id, Object ctx, String key, int depth, ComponentHost host) {
        ExpressionEvaluator eval = new ExpressionEvaluator(ctx);

        // attributes: static first, then bound (bound can override)
        Map<String, String> attrs = new LinkedHashMap<>(el.staticAttrs());
        for (Map.Entry<String, String> e : el.boundAttrs().entrySet()) {
            attrs.put(e.getKey(), eval.evalString(e.getValue()));
        }

        // Inside a partial, an @event action name that matches a passed param is rewritten to the
        // owner action the param carries (e.g. @click="onClick" with onClick="increment"). This
        // resolves up the scope chain, so it still fires for events inside a *for within a partial.
        Map<String, String> events = withinPartial(ctx)
                ? substituteEventParams(el.events(), ctx)
                : el.events();

        // children with positional ids. A <medley-island> is a client-owned region: it renders
        // as a childless host (props are attributes) so the differ only ever patches its host
        // attributes and never diffs/replaces its client-managed internal DOM. Any template
        // children of an island are intentionally ignored server-side.
        List<VNode> children = new ArrayList<>();
        if (!ISLAND_TAG.equals(el.tag())) {
            int childPos = 0;
            for (TemplateNode childTemplate : el.children()) {
                String childId = id + "." + childPos;
                children.addAll(renderNode(childTemplate, childId, ctx, depth, host));
                childPos++;
            }
        }

        return new VNode.VElement(id, el.tag(), attrs, events, children, key);
    }

    /**
     * Expand a {@code <medley-partial name="x" ...>} inline: resolve fragment x, build a local scope
     * from the passed attributes (static → string, {@code :attr} → evaluated against the owner), and
     * render the fragment's single root at this slot's id. Non-param references leak through to the
     * owner context. The optional key (from a keyed {@code *for}) is attached to the fragment root.
     */
    private VNode expandPartial(TemplateNode.Element el, String id, Object ctx, String key, int depth, ComponentHost host) {
        if (partials == null) {
            throw new TemplateException("<medley-partial> used but no PartialResolver is configured");
        }
        if (depth >= MAX_PARTIAL_DEPTH) {
            throw new TemplateException("Partial nesting exceeded max depth (" + MAX_PARTIAL_DEPTH
                    + ") — likely a recursive partial");
        }
        String name = el.staticAttrs().get("name");
        if (name == null || name.isBlank()) {
            throw new TemplateException("<medley-partial> requires a non-empty name attribute");
        }
        TemplateNode.Element fragment = partials.resolve(name);
        if (fragment == null) {
            throw new TemplateException("Unknown partial: '" + name + "'");
        }

        ExpressionEvaluator ownerEval = new ExpressionEvaluator(ctx);
        Map<String, Object> params = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : el.staticAttrs().entrySet()) {
            if (!"name".equals(e.getKey())) params.put(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, String> e : el.boundAttrs().entrySet()) {
            params.put(e.getKey(), ownerEval.eval(e.getValue()));
        }
        ScopedContext paramScope = new ScopedContext(ctx, params, true);

        List<VNode> nodes = renderNode(fragment, id, paramScope, depth + 1, host);
        if (nodes.size() != 1) {
            throw new TemplateException("Partial '" + name + "' must render exactly one root element");
        }
        VNode node = nodes.get(0);
        if (key != null && node instanceof VNode.VElement ve) {
            node = new VNode.VElement(ve.id(), ve.tag(), ve.attrs(), ve.events(), ve.children(), key, ve.opaque());
        }
        return node;
    }

    /** True if {@code ctx} is a partial scope, or nests (transitively) inside one. */
    private static boolean withinPartial(Object ctx) {
        while (ctx instanceof ScopedContext sc) {
            if (sc.isPartialScope()) return true;
            ctx = sc.parent();
        }
        return false;
    }

    /**
     * Expand a {@code <medley-component name="x" ...>} boundary: resolve the passed attributes to
     * param values (static → string, {@code :attr} → evaluated against the owner) and ask the
     * {@link ComponentHost} to mount (first encounter) or reuse (later parent re-render) the child at
     * the child instance id {@code hostId + "::" + name}, returning its current subtree.
     *
     * <p>The result is an <em>opaque</em> host carrying {@code data-medley-cid} (the child instance id,
     * the client's action-routing marker). The embedded child subtree is present for serialization
     * (SSR / insert / replace) but the differ never recurses into it — the child owns its own diff
     * loop — so a parent-only re-render leaves the child's DOM and {@code @State} untouched.</p>
     */
    private VNode expandComponent(TemplateNode.Element el, String hostId, Object ctx, String key, int depth, ComponentHost host) {
        if (host == null) {
            throw new TemplateException("<medley-component> used but no ComponentHost is configured");
        }
        if (depth >= MAX_PARTIAL_DEPTH) {
            throw new TemplateException("Component nesting exceeded max depth (" + MAX_PARTIAL_DEPTH
                    + ") — likely a recursive component");
        }
        String name = el.staticAttrs().get("name");
        if (name == null || name.isBlank()) {
            throw new TemplateException("<medley-component> requires a non-empty name attribute");
        }

        ExpressionEvaluator ownerEval = new ExpressionEvaluator(ctx);
        Map<String, Object> params = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : el.staticAttrs().entrySet()) {
            if (!"name".equals(e.getKey())) params.put(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, String> e : el.boundAttrs().entrySet()) {
            params.put(e.getKey(), ownerEval.eval(e.getValue()));
        }

        // An @event on the boundary is a child→parent callback binding (output name → owner action),
        // e.g. @save="onSave($event)". Passed to the host, which wires the child's @Output emitters.
        String childId = hostId + "::" + name;
        VNode childRoot = host.mountChild(childId, name, params, el.events(), depth + 1);
        if (childRoot == null) {
            throw new TemplateException("Unknown component: '" + name + "'");
        }

        Map<String, String> hostAttrs = new LinkedHashMap<>();
        hostAttrs.put("name", name);
        hostAttrs.put("data-medley-cid", childId);
        return new VNode.VElement(hostId, COMPONENT_TAG, hostAttrs, Map.of(), List.of(childRoot), key, true);
    }

    private static Map<String, String> substituteEventParams(Map<String, String> events, Object ctx) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : events.entrySet()) {
            out.put(e.getKey(), substituteActionName(e.getValue(), ctx));
        }
        return out;
    }

    /** If the binding's leading action identifier is a String local (a partial param, resolved up
     *  the scope chain), swap in the owner action name, preserving any {@code (args)} call-syntax
     *  from increment 2 (e.g. {@code "onChange($value)"}). */
    private static String substituteActionName(String binding, Object ctx) {
        int i = 0;
        while (i < binding.length()) {
            char c = binding.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '$') i++;
            else break;
        }
        String ident = binding.substring(0, i);
        return (resolveLocal(ctx, ident) instanceof String action)
                ? action + binding.substring(i)
                : binding;
    }

    /** Resolve a local by name up the scope chain (partial params + any enclosing loop vars). */
    private static Object resolveLocal(Object ctx, String ident) {
        while (ctx instanceof ScopedContext sc) {
            if (sc.locals().containsKey(ident)) return sc.locals().get(ident);
            ctx = sc.parent();
        }
        return null;
    }

    /**
     * Read-only scope used inside loops: exposes the loop variable while delegating any other
     * member lookups to the parent context. Implemented as a dynamic member holder that the
     * reflective evaluator can read from.
     */
    static final class ScopedContext {
        private final Object parent;
        private final Map<String, Object> locals;
        private final boolean partialScope;

        /** Single loop-variable scope (used by {@code *for}). */
        ScopedContext(Object parent, String varName, Object value) {
            this.parent = parent;
            this.locals = new HashMap<>();
            this.locals.put(varName, value);
            this.partialScope = false;
        }

        /** Multi-local scope built from a partial's passed attributes. */
        ScopedContext(Object parent, Map<String, Object> locals, boolean partialScope) {
            this.parent = parent;
            this.locals = locals;
            this.partialScope = partialScope;
        }

        boolean isPartialScope() { return partialScope; }
        Object parent() { return parent; }
        Map<String, Object> locals() { return locals; }
    }
}
