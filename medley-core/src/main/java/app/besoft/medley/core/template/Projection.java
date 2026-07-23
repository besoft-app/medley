package app.besoft.medley.core.template;

import app.besoft.medley.core.vnode.VNode;

import java.util.List;

/**
 * Content a parent authored <em>inside</em> a {@code <medley-component>} boundary (Stage 6,
 * increment 6.2). The nodes were rendered in the <b>parent's</b> scope and carry <b>parent ids</b>
 * ({@code hostId.N}); the child splices them at its {@code <medley-slot>} without owning them.
 *
 * @param ownerComponentId the instance id of the component that rendered {@code nodes} — emitted on the
 *                         slot as {@code data-medley-cid} so the client's walk-up routes a projected
 *                         event to the parent instead of the child it is physically nested in
 * @param nodes            the projected VNodes, in source order
 */
public record Projection(String ownerComponentId, List<VNode> nodes) {

    /** No body content at the boundary — the common case, and what a slotless child is given. */
    public static final Projection EMPTY = new Projection(null, List.of());

    public Projection {
        nodes = List.copyOf(nodes);
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }
}
