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

    private final TemplateNode.Element root;

    public TemplateRenderer(TemplateNode.Element root) {
        this.root = root;
    }

    public static TemplateRenderer of(String template) {
        return new TemplateRenderer(TemplateParser.parse(template));
    }

    /**
     * Render the template for the given component instance.
     *
     * @param componentId the id of the owning component (used as the root id prefix)
     * @param context     the component instance whose state the template reads
     */
    public VNode render(String componentId, Object context) {
        List<VNode> nodes = renderNode(root, componentId, context);
        if (nodes.size() != 1) {
            throw new TemplateException("Template root must render exactly one element");
        }
        return nodes.get(0);
    }

    /** Returns a list because *for can expand one template node into many VNodes. */
    private List<VNode> renderNode(TemplateNode node, String id, Object ctx) {
        return switch (node) {
            case TemplateNode.Text t -> List.of(new VNode.VText(id, t.value()));
            case TemplateNode.Interpolation i -> {
                String text = new ExpressionEvaluator(ctx).evalString(i.expr());
                yield List.of(new VNode.VText(id, text));
            }
            case TemplateNode.Element el -> renderElement(el, id, ctx);
        };
    }

    private List<VNode> renderElement(TemplateNode.Element el, String id, Object ctx) {
        // *for expands first
        if (el.forExpr() != null) {
            return renderForLoop(el, id, ctx);
        }
        // *if gates the single element. When false we still occupy exactly one slot with a
        // stable placeholder so sibling positions (and therefore ids) do not drift between
        // renders. The differ then emits a clean Replace in place instead of insert/remove.
        if (el.ifExpr() != null && !new ExpressionEvaluator(ctx).evalBoolean(el.ifExpr())) {
            return List.of(placeholder(id));
        }
        return List.of(renderSingleElement(el, id, ctx));
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

    private List<VNode> renderForLoop(TemplateNode.Element el, String id, Object ctx) {
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
            out.add(renderSingleElement(el, childId, scope, key));
            index++;
        }
        return out;
    }

    private VNode renderSingleElement(TemplateNode.Element el, String id, Object ctx) {
        return renderSingleElement(el, id, ctx, null);
    }

    private VNode renderSingleElement(TemplateNode.Element el, String id, Object ctx, String key) {
        ExpressionEvaluator eval = new ExpressionEvaluator(ctx);

        // attributes: static first, then bound (bound can override)
        Map<String, String> attrs = new LinkedHashMap<>(el.staticAttrs());
        for (Map.Entry<String, String> e : el.boundAttrs().entrySet()) {
            attrs.put(e.getKey(), eval.evalString(e.getValue()));
        }

        // children with positional ids. A <medley-island> is a client-owned region: it renders
        // as a childless host (props are attributes) so the differ only ever patches its host
        // attributes and never diffs/replaces its client-managed internal DOM. Any template
        // children of an island are intentionally ignored server-side.
        List<VNode> children = new ArrayList<>();
        if (!ISLAND_TAG.equals(el.tag())) {
            int childPos = 0;
            for (TemplateNode childTemplate : el.children()) {
                String childId = id + "." + childPos;
                children.addAll(renderNode(childTemplate, childId, ctx));
                childPos++;
            }
        }

        return new VNode.VElement(
                id,
                el.tag(),
                attrs,
                el.events(),
                children,
                key
        );
    }

    /**
     * Read-only scope used inside loops: exposes the loop variable while delegating any other
     * member lookups to the parent context. Implemented as a dynamic member holder that the
     * reflective evaluator can read from.
     */
    static final class ScopedContext {
        private final Object parent;
        private final Map<String, Object> locals = new HashMap<>();

        ScopedContext(Object parent, String varName, Object value) {
            this.parent = parent;
            this.locals.put(varName, value);
        }

        /** Used by ExpressionEvaluator via reflection through getMember. */
        public Object getMember(String name) {
            if (locals.containsKey(name)) return locals.get(name);
            return parent;
        }

        Object parent() { return parent; }
        Map<String, Object> locals() { return locals; }
    }
}
