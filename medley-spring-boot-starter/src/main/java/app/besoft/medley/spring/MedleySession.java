package app.besoft.medley.spring;

import app.besoft.medley.core.component.Component;
import app.besoft.medley.core.component.ComponentInstance;
import app.besoft.medley.core.component.ParamBinder;
import app.besoft.medley.core.diff.Patch;
import app.besoft.medley.core.template.ChildComponentFactory;
import app.besoft.medley.core.template.ComponentHost;
import app.besoft.medley.core.template.TemplateRenderer;
import app.besoft.medley.core.vnode.VNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the live component instances for a single user session, and coordinates nested children.
 *
 * <p>Each top-level route renders one root component, registered here under {@code "root"}. Nested
 * {@code <medley-component>} boundaries register their child instances here too, under their child
 * instance id ({@code hostId + "::" + name}), so a WebSocket event addressed to a child dispatches
 * straight to it (Stage 4, increment 4b.2). The session is the unit that bounds server-side state:
 * when it ends, its whole component tree (root + children, and all {@code @State}) is released.</p>
 *
 * <p>As the {@link ComponentHost}, this mounts a child the first time a boundary is rendered (create
 * the prototype bean, inject {@code @Param}s, register it, render it once) and reuses that same
 * instance on later parent re-renders — so the child's {@code @State} survives. The boundary host is
 * diff-opaque, so a parent re-render never touches the child's DOM; a child re-renders and diffs its
 * own subtree only when one of its own actions fires.</p>
 *
 * <p><b>Props-down cascade (Stage 4, increment 4b.3a):</b> when a parent re-render changes a reused
 * child's bound {@code :param} values, the child is re-injected, {@link Component#onParamChange()}
 * fires, and the child's own re-render/diff patches are buffered here. The WebSocket handler brackets
 * each inbound message with {@link #resetRenderCycle()} / {@link #drainCascade()} and appends the
 * buffered child patches to the owner's — the opaque boundary keeps the owner diff from also emitting
 * them, so props-down reaches nested children in the same single response.</p>
 *
 * <p>Concurrency: events for one session are processed one at a time (see the WebSocket handler), so
 * a {@code ConcurrentHashMap} for the registry plus a single-threaded render loop per session keeps
 * diffs deterministic.</p>
 */
public class MedleySession implements ComponentHost {

    private final TemplateRegistry templates;
    private final Map<String, ComponentInstance> instances = new ConcurrentHashMap<>();
    /** Last-injected bound param values per child id, so a re-render only re-injects on real change. */
    private final Map<String, Map<String, Object>> lastParams = new ConcurrentHashMap<>();
    /** Patches cascaded from children re-rendered during the current owner render (see class doc).
     *  Touched only on the render/WS thread, bracketed by resetRenderCycle()/drainCascade(). */
    private final List<Patch> cascade = new ArrayList<>();

    public MedleySession(TemplateRegistry templates) {
        this.templates = templates;
    }

    /** Mount a freshly created root component under a generated id; returns the instance. */
    public ComponentInstance mount(String id, Component component) {
        TemplateRenderer renderer = templates.rendererFor(component.getClass());
        ComponentInstance instance = new ComponentInstance(id, component, renderer, this);
        instances.put(id, instance);
        return instance;
    }

    /**
     * {@link ComponentHost}: mount the child at a boundary on first encounter, or reuse the
     * already-mounted instance on a later parent re-render. On reuse, if the bound {@code :param}
     * values changed since last injection, the child is re-injected, {@link Component#onParamChange()}
     * fires, and the child's re-render/diff patches are buffered into the cascade (4b.3a); if they are
     * unchanged the child is left untouched (its {@code @State} survives, no work). Null when
     * {@code name} is not a registered {@code @MedleyChild} — the renderer then raises a named
     * {@link app.besoft.medley.core.template.TemplateException}.
     */
    @Override
    public VNode mountChild(String childId, String name, Map<String, Object> params, int depth) {
        ComponentInstance existing = instances.get(childId);
        if (existing != null) {
            if (!params.equals(lastParams.get(childId))) {
                ParamBinder.inject(existing.component(), params);
                existing.component().onParamChange();
                cascade.addAll(existing.renderToPatches());
                lastParams.put(childId, new LinkedHashMap<>(params));
            }
            return existing.currentTree();
        }
        ChildComponentFactory.Child created = templates.createChild(name, params);
        if (created == null) {
            return null;
        }
        ComponentInstance child = new ComponentInstance(
                childId, (Component) created.component(), created.renderer(), this);
        // Render before registering, so a template that fails to render does not leave a
        // half-mounted instance (with a null currentTree) behind for a later action to hit.
        VNode tree = child.renderTree(depth);
        instances.put(childId, child);
        lastParams.put(childId, new LinkedHashMap<>(params));
        return tree;
    }

    /** Start a fresh render cycle: clear any patches left buffered from a previous message. Called by
     *  the WebSocket handler immediately before invoking an action / island commit. */
    public void resetRenderCycle() {
        cascade.clear();
    }

    /** Drain the patches cascaded from children whose params changed during this render cycle. */
    public List<Patch> drainCascade() {
        List<Patch> drained = new ArrayList<>(cascade);
        cascade.clear();
        return drained;
    }

    public ComponentInstance get(String id) {
        return instances.get(id);
    }

    public boolean contains(String id) {
        return instances.containsKey(id);
    }

    public void remove(String id) {
        ComponentInstance removed = instances.remove(id);
        lastParams.remove(id);
        if (removed != null) {
            removed.component().onDestroy();
        }
    }
}
