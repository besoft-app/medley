package app.besoft.medley.core.template;

import app.besoft.medley.core.vnode.VNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Content a parent authored <em>inside</em> a {@code <medley-component>} boundary (Stage 6). The nodes
 * were rendered in the <b>parent's</b> scope and carry <b>parent ids</b> ({@code hostId.N} by absolute
 * source position); the child splices each bucket at the matching {@code <medley-slot>}.
 *
 * <p>Buckets are keyed by target slot name; unnamed content uses {@link #DEFAULT}. Ids come from source
 * position, not bucket, so a node's id is independent of which slot it lands in (Stage 6, increment 6.3).</p>
 *
 * @param ownerComponentId the instance id of the component that rendered the nodes — emitted on each
 *                         filled slot as {@code data-medley-cid} so the client's walk-up routes a
 *                         projected event to the parent, not the child it is nested in
 * @param bySlot           projected nodes grouped by target slot name (default under {@link #DEFAULT})
 */
public record Projection(String ownerComponentId, Map<String, List<VNode>> bySlot) {

    /** The bucket key for unnamed content (the default {@code <medley-slot>}). */
    public static final String DEFAULT = "";

    /** No body content at the boundary — the common case, and what a slotless child is given. */
    public static final Projection EMPTY = new Projection(null, Map.of());

    public Projection {
        // Deep, immutable copy so value-equality (used by MedleySession.lastProjection to detect a
        // projection change) compares stable data, not mutable references.
        Map<String, List<VNode>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<VNode>> e : bySlot.entrySet()) {
            copy.put(e.getKey(), List.copyOf(e.getValue()));
        }
        bySlot = Map.copyOf(copy);
    }

    public boolean isEmpty() {
        return bySlot.isEmpty();
    }

    /** The nodes targeting {@code slotName}, or an empty list (→ the slot renders its fallback). */
    public List<VNode> nodesFor(String slotName) {
        return bySlot.getOrDefault(slotName, List.of());
    }

    /** The slot names that actually carry projected nodes (drives the declared-name fail-fast). */
    public Set<String> filledSlots() {
        return bySlot.keySet();
    }
}
