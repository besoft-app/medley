package app.besoft.medley.spring;

import app.besoft.medley.core.component.Component;
import app.besoft.medley.core.component.ComponentInstance;
import app.besoft.medley.core.component.OutputBinder;
import app.besoft.medley.core.component.ParamBinder;
import app.besoft.medley.core.diff.IdPaths;
import app.besoft.medley.core.diff.Patch;
import app.besoft.medley.core.template.ChildComponentFactory;
import app.besoft.medley.core.template.ComponentHost;
import app.besoft.medley.core.template.TemplateException;
import app.besoft.medley.core.template.TemplateRenderer;
import app.besoft.medley.core.vnode.VNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;

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
 * <p><b>Eviction + cap (Stage 4, increment 4b.3b):</b> a child whose boundary structurally disappears
 * this pass (an {@code *if} turning false → a {@code Replace} host→placeholder, or a dropped keyed
 * {@code *for} item → a {@code Remove}) is evicted: {@link #evictByPatches(List)} drops every
 * registered child covered by such a patch that was <em>not</em> (re)mounted this pass (the
 * touched-set spares a boundary a {@code Replace} <em>introduced</em>, whose subtree is already in the
 * patch HTML). {@link Component#onDestroy()} cascades to descendants. A per-session component cap
 * (runaway guard) fails fast on mount.</p>
 *
 * <p><b>Lifecycle (Stage 4, increment 6a):</b> the session is held as an HTTP session attribute, so
 * when the HTTP session is invalidated or times out the container calls {@link #valueUnbound}, which
 * releases the whole component tree ({@code onDestroy} cascaded deepest-first). This ties Medley's
 * server-side state to the servlet session timeout with no extra timer; recovery is the normal SSR
 * path on the next visit.</p>
 *
 * <p>Concurrency: events for one session are processed one at a time (see the WebSocket handler), so
 * a {@code ConcurrentHashMap} for the registry plus a single-threaded render loop per session keeps
 * diffs deterministic.</p>
 */
public class MedleySession implements ComponentHost, HttpSessionBindingListener {

    private static final Logger log = LoggerFactory.getLogger(MedleySession.class);

    private final TemplateRegistry templates;
    private final int maxComponents;
    private final Map<String, ComponentInstance> instances = new ConcurrentHashMap<>();
    /** Last-injected bound param values per child id, so a re-render only re-injects on real change. */
    private final Map<String, Map<String, Object>> lastParams = new ConcurrentHashMap<>();
    /** Patches cascaded from children re-rendered during the current owner render (see class doc).
     *  Touched only on the render/WS thread, bracketed by resetRenderCycle()/drainCascade(). */
    private final List<Patch> cascade = new ArrayList<>();
    /** Child ids (re)mounted during the current render pass, so eviction spares a just-mounted child. */
    private final Set<String> thisPassTouched = ConcurrentHashMap.newKeySet();

    /** Unlimited component cap — for the PoC/tests that don't wire {@link MedleyProperties}. */
    public MedleySession(TemplateRegistry templates) {
        this(templates, 0);
    }

    /** @param maxComponents per-session component cap; {@code 0} or negative means unlimited. */
    public MedleySession(TemplateRegistry templates, int maxComponents) {
        this.templates = templates;
        this.maxComponents = maxComponents;
    }

    /** Mount a freshly created root component under a generated id; returns the instance. Evicts any
     *  previously-registered subtree under {@code id} first, so revisiting a route in the same session
     *  does not orphan the old component tree (and leak cap headroom). */
    public ComponentInstance mount(String id, Component component) {
        remove(id);
        enforceCap(id);
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
    public VNode mountChild(String childId, String name, Map<String, Object> params,
                            Map<String, String> outputs, int depth) {
        thisPassTouched.add(childId);
        ComponentInstance existing = instances.get(childId);
        if (existing != null) {
            if (!params.equals(lastParams.get(childId))) {
                ParamBinder.inject(existing.component(), params);
                existing.component().onParamChange();
                cascade.addAll(existing.renderToPatches());
                lastParams.put(childId, new LinkedHashMap<>(params));
            }
            return existing.currentTree(); // outputs are static — wired once at creation, not on reuse
        }
        ChildComponentFactory.Child created = templates.createChild(name, params);
        if (created == null) {
            return null;
        }
        enforceCap(childId);
        Component childComponent = (Component) created.component();
        // Wire child→parent callbacks: each bound @Output emitter invokes the owner action, buffering
        // the owner's patches into the cascade so they ride back with the child's own response.
        wireOutputs(childId, childComponent, outputs);
        ComponentInstance child = new ComponentInstance(childId, childComponent, created.renderer(), this);
        // Render before registering, so a template that fails to render does not leave a
        // half-mounted instance (with a null currentTree) behind for a later action to hit.
        VNode tree = child.renderTree(depth);
        instances.put(childId, child);
        lastParams.put(childId, new LinkedHashMap<>(params));
        return tree;
    }

    /**
     * Wire a freshly-created child's {@code @Output} emitters to its parent's actions, per the
     * boundary's {@code @event} bindings ({@code output name -> "ownerAction($event)"}). An unbound
     * output stays a no-op. When a callback fires it invokes the owner action in-process; the owner's
     * resulting patches are buffered into the {@code cascade}, so the WebSocket handler drains and
     * merges them with the child's own patches into one response — no new wire message needed.
     */
    private void wireOutputs(String childId, Component child, Map<String, String> outputs) {
        Map<String, Consumer<Object>> sinks = new LinkedHashMap<>();
        if (outputs != null && !outputs.isEmpty()) {
            String ownerId = IdPaths.ownerComponentId(childId);
            for (Map.Entry<String, String> e : outputs.entrySet()) {
                OutputBinding binding = OutputBinding.parse(e.getValue());
                sinks.put(e.getKey(), payload -> {
                    ComponentInstance owner = instances.get(ownerId);
                    if (owner == null) {
                        return; // owner evicted — emitting is a no-op
                    }
                    List<Patch> ownerPatches = binding.passesPayload()
                            ? owner.invokeAction(binding.action(), payload)
                            : owner.invokeAction(binding.action());
                    cascade.addAll(ownerPatches);
                });
            }
        }
        // Always inject: this ensures every @Output field holds an emitter (no-op if unbound), so the
        // child can safely emit even when the parent bound nothing.
        OutputBinder.inject(child, sinks);
    }

    /**
     * A parsed {@code @output} boundary binding: the owner action to invoke and whether the emitted
     * payload is passed. Supports {@code "action"} (bare → zero-arg owner action) and
     * {@code "action($event)"} (payload passed, coerced to the action's parameter type by the owner).
     */
    private record OutputBinding(String action, boolean passesPayload) {
        static OutputBinding parse(String binding) {
            String b = binding.trim();
            int lparen = b.indexOf('(');
            if (lparen < 0) {
                return new OutputBinding(b, false);
            }
            String action = b.substring(0, lparen).trim();
            String args = b.substring(lparen + 1, b.endsWith(")") ? b.length() - 1 : b.length()).trim();
            if (action.isEmpty()) {
                throw new TemplateException("Invalid @Output binding (missing action): '" + binding + "'");
            }
            if (args.isEmpty()) {
                return new OutputBinding(action, false);
            }
            if (args.equals("$event")) {
                return new OutputBinding(action, true);
            }
            throw new TemplateException("Unsupported @Output binding args '" + args + "' in '" + binding
                    + "' — use $event or no arguments");
        }
    }

    /** Start a fresh render cycle: clear the cascade buffer and the touched-set left from a previous
     *  message. Called by the WebSocket handler immediately before invoking an action / island commit. */
    public void resetRenderCycle() {
        cascade.clear();
        thisPassTouched.clear();
    }

    /** Drain the patches cascaded from children whose params changed during this render cycle. */
    public List<Patch> drainCascade() {
        List<Patch> drained = new ArrayList<>(cascade);
        cascade.clear();
        return drained;
    }

    /**
     * Evict children whose boundary was structurally removed by this render's patches (a {@code Replace}
     * host→placeholder for a false {@code *if}, or a {@code Remove} for a dropped keyed item). A
     * registered child is evicted when it is a self-or-descendant of such a patch id and was not
     * (re)mounted this pass — the touched-set spares a boundary a {@code Replace} <em>introduced</em>
     * (an {@code *if} turning true), whose subtree is already carried in the patch HTML. Evicting a
     * boundary evicts its whole subtree, since each descendant is itself covered by the patch id.
     * Call after merging the owner + cascade patches, on the render/WS thread.
     */
    public void evictByPatches(List<Patch> patches) {
        List<String> structuralIds = new ArrayList<>();
        for (Patch p : patches) {
            if (p instanceof Patch.Replace || p instanceof Patch.Remove) {
                structuralIds.add(p.id());
            }
        }
        if (structuralIds.isEmpty()) {
            return;
        }
        List<String> doomed = new ArrayList<>();
        for (String id : instances.keySet()) {
            if (thisPassTouched.contains(id)) {
                continue;
            }
            for (String structuralId : structuralIds) {
                if (IdPaths.isSelfOrDescendant(structuralId, id)) {
                    doomed.add(id);
                    break;
                }
            }
        }
        evictAll(doomed);
    }

    public ComponentInstance get(String id) {
        return instances.get(id);
    }

    public boolean contains(String id) {
        return instances.containsKey(id);
    }

    /** The ids of every live component in this session (root + nested children), as a snapshot copy.
     *  Read-only introspection — used by the dev-tools inspector (Stage 5, increment 2). */
    public Set<String> componentIds() {
        return Set.copyOf(instances.keySet());
    }

    /** Release the whole component tree (root + all children), cascading {@link Component#onDestroy()}
     *  deepest-first. Idempotent. Called when the owning HTTP session ends (see {@link #valueUnbound}). */
    public void destroyAll() {
        evictAll(new ArrayList<>(instances.keySet()));
        lastParams.clear();
    }

    /** {@link HttpSessionBindingListener}: the HTTP session was invalidated or timed out — release the
     *  tree. This is Medley's idle/session eviction (increment 6a); no extra timer is needed. */
    @Override
    public void valueUnbound(HttpSessionBindingEvent event) {
        destroyAll();
    }

    /** Remove a subtree (an id and every descendant), cascading {@link Component#onDestroy()}. */
    public void remove(String id) {
        List<String> subtree = new ArrayList<>();
        for (String k : instances.keySet()) {
            if (IdPaths.isSelfOrDescendant(id, k)) {
                subtree.add(k);
            }
        }
        evictAll(subtree);
    }

    /** Evict the given ids deepest-first, so a child's {@code onDestroy} runs before its parent's. */
    private void evictAll(List<String> ids) {
        ids.sort(Comparator.comparingInt(String::length).reversed());
        for (String id : ids) {
            ComponentInstance removed = instances.remove(id);
            lastParams.remove(id);
            if (removed != null) {
                try {
                    removed.component().onDestroy();
                } catch (RuntimeException e) {
                    log.warn("onDestroy of component '{}' threw", id, e);
                }
            }
        }
    }

    private void enforceCap(String id) {
        if (maxComponents > 0 && instances.size() >= maxComponents) {
            throw new MedleyCapacityExceededException(id, maxComponents);
        }
    }
}
